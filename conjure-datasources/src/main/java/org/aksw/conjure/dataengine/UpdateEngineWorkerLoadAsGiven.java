package org.aksw.conjure.dataengine;

import java.util.Iterator;
import java.util.List;

import org.aksw.jenax.sparql.query.rx.RDFDataMgrEx;
import org.apache.jena.atlas.data.BagFactory;
import org.apache.jena.atlas.data.DataBag;
import org.apache.jena.atlas.data.ThresholdPolicy;
import org.apache.jena.atlas.data.ThresholdPolicyFactory;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.atlas.web.TypedInputStream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.sparql.modify.UpdateEngineWorker;
import org.apache.jena.sparql.modify.request.UpdateLoad;
import org.apache.jena.sparql.modify.request.UpdateModify;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementNamedGraph;
import org.apache.jena.sparql.system.SerializationFactoryFinder;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.update.UpdateException;

/**
 * Subclass of the default UpdateEngineWorker that modifies SPARQL LOAD
 * statement execution such that relative IRIs are retained.
 */
public class UpdateEngineWorkerLoadAsGiven
    extends UpdateEngineWorker
{
    public UpdateEngineWorkerLoadAsGiven(DatasetGraph datasetGraph, Binding inputBinding, Context context) {
        super(datasetGraph, inputBinding, context);
    }

    @Override
    public void visit(UpdateLoad update) {
        // LOAD SILENT? iri ( INTO GraphRef )?
        String source = update.getSource();
        Node dest = update.getDest();
        executeOperation(update.isSilent(), ()->{
            Graph graph = graphOrThrow(datasetGraph, dest);
            // We must load buffered if silent so that the dataset graph sees
            // all or no triples/quads when there is a parse error
            // (no nested transaction abort).
            try {
                boolean loadBuffered = update.isSilent() || ! datasetGraph.supportsTransactionAbort();
                if ( dest == null ) {
                    // LOAD SILENT? iri
                    // Quads accepted (extension).
                    if ( loadBuffered ) {
                        DatasetGraph dsg2 = DatasetGraphFactory.create();
                        RDFDataMgrEx.readAsGiven(dsg2, source);
                        dsg2.find().forEachRemaining(datasetGraph::add);
                    } else {
                        RDFDataMgrEx.readAsGiven(datasetGraph, source);
                    }
                    return;
                }
                // LOAD SILENT? iri INTO GraphRef
                // Load triples. To give a decent error message and also not have the usual
                // parser behaviour of just selecting default graph triples when the
                // destination is a graph, we need to do the same steps as RDFParser.parseURI,
                // with different checking.
                TypedInputStream input = RDFDataMgr.open(source);
                String contentType = input.getContentType();
                Lang lang = RDFDataMgr.determineLang(source, contentType, Lang.TTL);
                if ( lang == null )
                    throw new UpdateException("Failed to determine the syntax for '"+source+"'");
                if ( ! RDFLanguages.isTriples(lang) )
                    throw new UpdateException("Attempt to load quads into a graph");
                RDFParser parser = RDFParser
                        .source(input.getInputStream())
                        .forceLang(lang)
                        .build();
                if ( loadBuffered ) {
                    Graph g = GraphFactory.createGraphMem();
                    parser.parse(g);
                    GraphUtil.addInto(graph, g);
                } else {
                    parser.parse(graph);
                }
            } catch (RiotException ex) {
                if ( !update.isSilent() ) {
                    throw new UpdateException("Failed to LOAD '" + source + "' :: " + ex.getMessage(), ex);
                }
            }
        });
    }

    protected boolean executeOperation(boolean isSilent, Runnable action) {
        try {
            action.run();
            return true;
        } catch (UpdateException ex) {
            if ( isSilent )
                return false;
            throw ex;
        }
    }

    /**
     * Copy of the method because it uses a none overridable evalBindings method
     * <pre>Iterator<Binding> bindings = evalBindings(query, dsg, inputBinding, context);</pre>
     */
    @Override
    public void visit(UpdateModify update) {
        Node withGraph = update.getWithIRI();
        Element elt = update.getWherePattern();

        // null or a dataset for USING clause.
        // USING/USING NAMED
        DatasetGraph dsg = processUsing(update);

        // -------------------
        // WITH
        // USING overrides WITH
        if ( dsg == null && withGraph != null ) {
            // Subtle difference : WITH <uri>... WHERE {}
            // and an empty/unknown graph <uri>
            //   rewrite with GRAPH -> no match.
            //   redo as dataset with different default graph -> match
            // SPARQL is unclear about what happens when the graph does not exist.
            //   but the rewrite with ElementNamedGraph is closer to SPARQL.
            // Better, treat as
            // WHERE { GRAPH <with> { ... } }
            // This is the SPARQL wording (which is a bit loose).
            elt = new ElementNamedGraph(withGraph, elt);
        }

        // WITH :
        // The quads from deletion/insertion are altered when streamed
        // into the templates later on.

        // -------------------

        if ( dsg == null )
            dsg = datasetGraph;

        ThresholdPolicy<Binding> policy = ThresholdPolicyFactory.policyFromContext(datasetGraph.getContext());
        DataBag<Binding> db = BagFactory.newDefaultBag(policy, SerializationFactoryFinder.bindingSerializationFactory());
        try {
            Iterator<Binding> bindings = evalBindings(elt);
            try {
                if ( false ) {
                    List<Binding> x = Iter.toList(bindings);
                    System.out.printf("====>> Bindings (%d)\n", x.size());
                    Iter.print(System.out, x.iterator());
                    System.out.println("====<<");
                    bindings = Iter.iter(x);
                }
                db.addAll(bindings);
            } finally {
                Iter.close(bindings);
            }

            Iterator<Binding> it = db.iterator();
            execDelete(datasetGraph, update.getDeleteQuads(), withGraph, it);
            Iter.close(it);

            Iterator<Binding> it2 = db.iterator();
            execInsert(datasetGraph, update.getInsertQuads(), withGraph, it2);
            Iter.close(it2);
        }
        finally {
            db.close();
        }
    }
}
