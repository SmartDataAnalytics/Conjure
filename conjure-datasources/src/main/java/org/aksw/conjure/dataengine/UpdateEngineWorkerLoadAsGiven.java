package org.aksw.conjure.dataengine;

import org.aksw.jenax.sparql.query.rx.RDFDataMgrEx;
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
import org.apache.jena.sparql.util.Context;
import org.apache.jena.update.UpdateException;

/**
 * Subclass of the default UpdateEngineWorker that modifies SPARQL LOAD
 * statement execution such that relative IRIs are retained.
 *
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

}
