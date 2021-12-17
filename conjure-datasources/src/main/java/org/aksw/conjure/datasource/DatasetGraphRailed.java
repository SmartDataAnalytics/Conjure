package org.aksw.conjure.datasource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.aksw.commons.lock.LockUtils;
import org.aksw.commons.util.exception.FinallyRunAll;
import org.aksw.jenax.arq.connection.TransactionalDelegate;
import org.aksw.jenax.arq.connection.TransactionalMultiplex;
import org.aksw.jenax.arq.datasource.HasDataset;
import org.aksw.jenax.arq.datasource.RdfDataSourceFactory;
import org.aksw.jenax.arq.datasource.RdfDataSourceSpecTerms;
import org.aksw.jenax.connection.datasource.RdfDataSource;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.TxnType;
import org.apache.jena.riot.other.G;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphQuads;
import org.apache.jena.sparql.core.GraphView;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * A dataset graph whose 'add' method creates new dataset graph instance
 * once the current graph's size exceeds a threshold.
 *
 * Deletion and lookup of quads and quad patterns have to be delegated to all graphs part of the rail.
 *
 *
 * @author raven
 *
 */
public class DatasetGraphRailed
    extends DatasetGraphQuads
    implements TransactionalDelegate
{
    private static final Logger logger = LoggerFactory.getLogger(DatasetGraphRailed.class);

    protected PrefixMap prefixes;

    /** Only check database size after that many triples */
    protected long newRailCheckIntervalSize = 100000;
    protected AtomicLong newRailCheckCount = new AtomicLong(0);

    /** A lock that needs to be acquired before the list of delegates can be modified*/
    protected ReadWriteLock delegatesLock = new ReentrantReadWriteLock();
    protected List<DatasetGraph> delegates = new ArrayList<>();

    protected TransactionalMultiplex<Transactional> transactional;


    protected Path railPropertiesFile;
    protected long railMemberSizeLimit;

    // In memory copy of the file
    protected Properties railProperties;
    protected RdfDataSourceFactory memberFactory;


    protected void checkTxn() {
        DatasetGraph master = delegates.get(0);
        if (master.isInTransaction()) {
            TxnType type = null;
            for (int i = 1; i < delegates.size(); ++i) {
                DatasetGraph slave = delegates.get(i);
                if (!slave.isInTransaction()) {
                    if (type == null) {
                        type = master.transactionType();
                    }
                    slave.begin(type);
                }
            }
        }
    }


    public static long getNumRailMembers(Properties props) {
        String numRailMembersStr = (String)props.get(RdfDataSourceSpecTerms.NUM_RAIL_MEMBERS);
        long numRailMembers = numRailMembersStr == null ? 0 : Long.parseLong(numRailMembersStr);
        return numRailMembers;
    }

    public static void setNumRailMembers(Properties props, long count) {
        props.put(RdfDataSourceSpecTerms.NUM_RAIL_MEMBERS, Long.toString(count));
    }

    public DatasetGraphRailed(Path railPropertiesFile, RdfDataSourceFactory delegateFactory) {
        super();
        this.railPropertiesFile = railPropertiesFile;
        this.memberFactory = delegateFactory;

        this.transactional = new TransactionalMultiplex<>(delegates) {
            protected <X> X forEachR(java.util.function.Function<? super Transactional, X> handler) {
                return LockUtils.runWithLock(delegatesLock.readLock(), () -> {
                    checkTxn();
                    return super.forEachR(handler);
                });
            };
        };

        try {
            railProperties = PropertiesUtils.read(railPropertiesFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        long numRailMembers = getNumRailMembers(railProperties);

        String railSizeStr = (String)railProperties.get(RdfDataSourceSpecTerms.RAIL_SIZE);
        Long railSize = railSizeStr == null ? null : Long.parseLong(railSizeStr);

        railMemberSizeLimit = Objects.requireNonNull(railSize, "Rail size must be specified (this should be roughly free ram / num partitions");


        // If there are no rail members then immediately create one because
        //   we need at least one delegate on which we can base the txn management
        // Otherwise just load the rails
        if (numRailMembers == 0) {
            addRail();
        } else {
            for (long i = 0; i < numRailMembers; ++i) {
                Properties memberProps = (Properties)railProperties.clone();
                Path memberLoc = railPropertiesFile.resolveSibling("rail-" + i);
                memberProps.put(RdfDataSourceSpecTerms.LOCATION_KEY, memberLoc.toString());

                try {
                    RdfDataSource member = memberFactory.create(PropertiesUtils.toStringObjectMap(memberProps));

                    HasDataset tmp = (HasDataset)member;
                    delegates.add(tmp.getDataset().asDatasetGraph());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        }
//
//        this.prefixes = prefixes;
//        this.newRailCheckIntervalSize = newRailCheckIntervalSize;
//        this.newRailCheckCount = newRailCheckCount;
//        this.delegates = delegates;
//        this.transactional = transactional;
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

    protected void addRail() {
        long nextId = delegates.size();

        Properties memberProps = (Properties)railProperties.clone();

        railProperties.put(RdfDataSourceSpecTerms.NUM_RAIL_MEMBERS, Long.toString(nextId + 1));

        try {
            PropertiesUtils.write(railPropertiesFile, railProperties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path memberLoc = railPropertiesFile.resolveSibling("rail-" + nextId);
        memberProps.put(RdfDataSourceSpecTerms.LOCATION_KEY, memberLoc.toString());
        RdfDataSource member;
        try {
            member = memberFactory.create(PropertiesUtils.toStringObjectMap(memberProps));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        DatasetGraph tmp = ((HasDataset)member).getDataset().asDatasetGraph();

//        if (isInTransaction()) {
//            tmp.begin(transactionType());
//        }
//
        delegates.add(tmp);
    }


     @Override
    public void add(Quad quad) {
         long cnt = newRailCheckCount.incrementAndGet();

         long byteSize = -1;

         boolean usedWriteLock = false;
         try {
            delegatesLock.readLock().lock();
            checkTxn();

            int numDelegates = delegates.size();
            DatasetGraph delegate = delegates.get(numDelegates - 1);

            boolean doAdd = true;
            for (int i = 0; i < numDelegates - 1; ++i) {
                if (delegates.get(i).contains(quad)) {
                    doAdd = false;
                    break;
                }
            }

            if (doAdd) {
                delegate.add(quad);
            }

            if (cnt % newRailCheckIntervalSize == 1) {
                byteSize = ((HasByteSize)delegate).getByteSize();
                if (byteSize > railMemberSizeLimit) {
                    delegatesLock.readLock().unlock();

                    logger.info(String.format("Adding rail graph; size %d >= threshold %d", byteSize, railMemberSizeLimit));

                    usedWriteLock = true;
                    delegatesLock.writeLock().lock();
                    try {
                        // Recheck the condition as maybe another thread acquired the write lock just when
                        // we released the read lock
                        if (delegates.size() == numDelegates) {
                            addRail();
                        }
                    } finally {
                        delegatesLock.writeLock().unlock();
                    }
                }
            }

        } finally {
            if (!usedWriteLock) {
                delegatesLock.readLock().unlock();
            }
        }
    }

    @Override
    public void delete(Quad quad) {
        LockUtils.runWithLock(delegatesLock.readLock(), () -> {
            checkTxn();

            for (DatasetGraph delegate : delegates) {
                delegate.delete(quad);
            }
        });
    }

    @Override
    public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
        return LockUtils.runWithLock(delegatesLock.readLock(), () -> {
            checkTxn();
            return DatasetGraphHashPartitioned.find(delegates, g, s, p, o);
        });
    }

    @Override
    public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) {
        return LockUtils.runWithLock(delegatesLock.readLock(), () -> {
            checkTxn();
            return DatasetGraphHashPartitioned.findNG(delegates, g, s, p, o);
        });
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
        LockUtils.runWithLock(delegatesLock.readLock(), () -> {
            FinallyRunAll fin = FinallyRunAll.create();
            for (DatasetGraph dg : delegates) {
                fin.add(dg::close);
            }
            fin.run();
        });
    }
}
