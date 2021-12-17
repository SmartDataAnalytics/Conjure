package org.aksw.conjure.datasource;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.commons.util.exception.FinallyRunAll;
import org.aksw.jenax.arq.connection.TransactionalDelegate;
import org.aksw.jenax.arq.connection.TransactionalMultiplex;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.other.G;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphQuads;
import org.apache.jena.sparql.core.GraphView;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Transactional;
import org.apache.jena.util.iterator.WrappedIterator;


/**
 * A dataset graph with a fixed number of child graphs and a hash functions
 * that determines for every quad deterministically in which child graph to place it.
 *
 * @author raven
 *
 */
public class DatasetGraphHashPartitioned
    extends DatasetGraphQuads
    implements TransactionalDelegate
{
    protected PrefixMap prefixes;

    protected Function<Quad, Integer> hashFunction;
    protected List<DatasetGraph> delegates;
    protected TransactionalMultiplex<Transactional> transactional;


    public static DatasetGraph createBySubject(List<DatasetGraph> delegates) {
        return new DatasetGraphHashPartitioned(delegates, DatasetGraphHashPartitioned::subjectToHash);
    }

    public static Integer subjectToHash(Quad quad) {
        Node s = quad.getSubject();
        Integer result = s == null || Node.ANY.equals(s) ? null : s.hashCode();
        return result;
    }


    public DatasetGraphHashPartitioned(List<DatasetGraph> delegates, Function<Quad, Integer> hashFunction) {
        super();
        this.prefixes = PrefixMapFactory.create();
        this.hashFunction = hashFunction;
        this.delegates = delegates;
        this.transactional = new TransactionalMultiplex<>(delegates);
    }

    @Override
    public PrefixMap prefixes() {
        return prefixes;
    }

    @Override
    public boolean supportsTransactions() {
        return true;
    }

    @Override
    public Transactional getDelegate() {
        return transactional;
    }


    public static Iterator<Quad> findNG(Collection<DatasetGraph> delegates, Node g, Node s, Node p, Node o) {
        List<Iterator<Quad>> iters = delegates.stream().map(dg -> dg.findNG(g, s, p, o)).collect(Collectors.toList());
        Iterator<Quad> result = WrappedIterator.createIteratorIterator(iters.iterator());
        return result;
    }

    public static Iterator<Quad> find(Collection<DatasetGraph> delegates, Node g, Node s, Node p, Node o) {
        List<Iterator<Quad>> iters = delegates.stream().map(dg -> dg.find(g, s, p, o)).collect(Collectors.toList());
        Iterator<Quad> result = WrappedIterator.createIteratorIterator(iters.iterator());
        return result;
    }

    @Override
    public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
        Quad quad = new Quad(g, s, p, o);
        Integer hash = hashFunction.apply(quad);

        Iterator<Quad> result;
        if (hash == null) {
            result = find(delegates, g, s, p, o);
        } else {
            int index = hash % delegates.size();
            DatasetGraph dg = delegates.get(index);
            result = dg.find(g, s, p, o);
        }

        return result;
    }

    @Override
    public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) {
        Quad quad = new Quad(g, s, p, o);
        Integer hash = hashFunction.apply(quad);

        Iterator<Quad> result;
        if (hash == null) {
            result = findNG(delegates, g, s, p, o);
        } else {
            int index = hashToIndex(hash);
            DatasetGraph dg = delegates.get(index);
            result = dg.find(g, s, p, o);
        }

        return result;
    }

    protected int quadToIndex(Quad quad) {
        int hash = hashFunction.apply(quad);
        int r = hashToIndex(hash);
        return r;
    }

    protected int hashToIndex(int hash) {
        return Math.abs(hash) % delegates.size();
    }

    @Override
    public void add(Quad quad) {
        int index = quadToIndex(quad);
        DatasetGraph dg = delegates.get(index);
        dg.add(quad);

    }

    @Override
    public void delete(Quad quad) {
        int index = quadToIndex(quad);
        DatasetGraph dg = delegates.get(index);
        dg.delete(quad);
    }

    @Override
    public Graph getDefaultGraph() {
        return GraphView.createDefaultGraph(this);
    }

    @Override
    public Graph getGraph(Node graphNode) {
        return GraphView.createNamedGraph(this, graphNode);
    }

    @Override
    public void addGraph(Node graphName, Graph graph) {
        G.triples2quads(graphName, graph.find()).forEach(this::add);
    }


    @Override
    public void close() {
        FinallyRunAll fin = FinallyRunAll.create();
        for (DatasetGraph dg : delegates) {
            fin.add(dg::close);
        }
        fin.run();
    }
}
