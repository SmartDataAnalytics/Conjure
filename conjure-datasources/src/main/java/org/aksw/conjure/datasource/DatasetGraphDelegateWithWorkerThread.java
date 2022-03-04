package org.aksw.conjure.datasource;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import org.aksw.jenax.arq.connection.link.IteratorDelegateWithWorkerThread;
import org.aksw.jenax.arq.connection.link.TransactionalDelegateWithWorkerThread;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.util.Context;

public class DatasetGraphDelegateWithWorkerThread
    extends TransactionalDelegateWithWorkerThread<DatasetGraph>
    implements DatasetGraph
{
    protected DatasetGraph delegate;

    public DatasetGraphDelegateWithWorkerThread(DatasetGraph delegate) {
        super();
        this.delegate = delegate;
    }

    public static DatasetGraph wrap(DatasetGraph delegate) {
        return new DatasetGraphDelegateWithWorkerThread(delegate);
    }

    @Override
    public DatasetGraph getDelegate() {
        return delegate;
    }

    @Override
    public Graph getDefaultGraph() {
        return submit(() -> getDelegate().getDefaultGraph());
    }

    @Override
    public Graph getGraph(Node graphNode) {
        return submit(() -> getDelegate().getGraph(graphNode));
    }

    @Override
    public Graph getUnionGraph() {
        return submit(() -> getDelegate().getUnionGraph());
    }

    @Override
    public boolean containsGraph(Node graphNode) {
        return submit(() -> getDelegate().containsGraph(graphNode));
    }

    @Override
    public void setDefaultGraph(Graph g) {
        submit(() -> getDelegate().setDefaultGraph(g));
    }

    @Override
    public void addGraph(Node graphName, Graph graph) {
        submit(() -> getDelegate().addGraph(graphName, graph));
    }

    @Override
    public void removeGraph(Node graphName) {
        submit(() -> getDelegate().removeGraph(graphName));
    }

    @Override
    public Iterator<Node> listGraphNodes() {
        return submit(() -> new IteratorDelegateWithWorkerThread<>(getDelegate().listGraphNodes(), es));
    }

    @Override
    public void add(Quad quad) {
        submit(() -> getDelegate().add(quad));
    }

    @Override
    public void delete(Quad quad) {
        submit(() -> getDelegate().delete(quad));
    }

    @Override
    public void add(Node g, Node s, Node p, Node o) {
        submit(() -> getDelegate().add(g, s, p, o));
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
        submit(() -> getDelegate().delete(g, s, p, o));
    }

    @Override
    public void deleteAny(Node g, Node s, Node p, Node o) {
        submit(() -> getDelegate().deleteAny(g, s, p, o));
    }

    @Override
    public Iterator<Quad> find(Quad quad) {
        return submit(() -> new IteratorDelegateWithWorkerThread<>(getDelegate().find(quad), es));
    }

    @Override
    public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
        return submit(() -> new IteratorDelegateWithWorkerThread<>(getDelegate().find(g, s, p, o), es));
    }

    @Override
    public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) {
        return submit(() -> new IteratorDelegateWithWorkerThread<>(getDelegate().findNG(g, s, p, o), es));
    }

    @Override
    public boolean contains(Node g, Node s, Node p, Node o) {
        return submit(() -> getDelegate().contains(g, s, p, o));
    }

    @Override
    public boolean contains(Quad quad) {
        return submit(() -> getDelegate().contains(quad));
    }

    @Override
    public void clear() {
        submit(() -> getDelegate().clear());
    }

    @Override
    public boolean isEmpty() {
        return submit(() -> getDelegate().isEmpty());
    }

    @Override
    public Lock getLock() {
        return submit(() -> getDelegate().getLock());
    }

    @Override
    public Context getContext() {
        return submit(() -> getDelegate().getContext());
    }

    @Override
    public long size() {
        return submit(() -> getDelegate().size());
    }

    @Override
    public synchronized void close() {
        if (es == null || !es.isShutdown()) {
            submit(() -> getDelegate().close());
            es.shutdownNow();
        }
    }

    @Override
    public PrefixMap prefixes() {
        return submit(() -> getDelegate().prefixes());
    }

    @Override
    public boolean supportsTransactions() {
        return submit(() -> getDelegate().supportsTransactions());
    }

}
