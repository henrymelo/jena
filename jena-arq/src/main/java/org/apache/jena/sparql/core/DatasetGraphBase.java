/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.sparql.core;

import java.util.Iterator ;
import org.apache.jena.atlas.io.IndentedLineBuffer ;
import org.apache.jena.atlas.iterator.Iter ;
import org.apache.jena.graph.Graph ;
import org.apache.jena.graph.Node ;
import org.apache.jena.shared.Lock ;
import org.apache.jena.shared.LockMRSW ;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory ;
import org.apache.jena.sparql.graph.GraphOps;
import org.apache.jena.sparql.sse.writers.WriterGraph ;
import org.apache.jena.sparql.util.Context ;

/** 
 * <p>DatasetGraph framework : readonly dataset need only provide find(g,s,p,o), getGraph() and getDefaultGraph()
 * although it may wish to override other operations and do better.</p>
 * 
 * <p>Implementations include:</p>
 * <ul>
 * <li>{@link DatasetGraphBase} that adds an implementation of find based on default / named graphs.</li>
 * <li>{@link DatasetGraphInMemory} provides full transactions for an in-memory {@code DatasetGraph}.</li>
 * <li>{@link DatasetGraphTriplesQuads} that adds mutating quad operations.</li>
 * <li>{@link DatasetGraphMap} provides for operations working over a collection of in-memory graphs.</li>
 * <li>{@link DatasetGraphMapLink} provides for operations working over a collection of graphs provived by the application.</li>
 * <li>{@link DatasetGraphCollection} that provides for operations working over a collection of graphs.</li>
 * </ul> 
 */
abstract public class DatasetGraphBase implements DatasetGraph
{
    private final Lock lock = new LockMRSW() ;
    private Context context = new Context() ;
    
    protected DatasetGraphBase() {}
    
    @Override
    public boolean containsGraph(Node graphNode)
    { return contains(graphNode, Node.ANY, Node.ANY, Node.ANY) ; }
    
    // Explicit record of what's not provided here.
    
    @Override
    public abstract Graph getDefaultGraph() ;
    
    @Override
    public Graph getUnionGraph() {
        // Implementations are encouraged to implement an efficent
        // {@code DatasetGraphBase.findQuadsInUnionGraph} or
        // {@code findNG(Quad.unionGraph, Node.ANY, Node.ANY, Node.ANY)}
        // for a distinct iterator (e.g. avoid calling "distinct()" by not creating duplicates).
        return GraphOps.unionGraph(this);
    }

    @Override
    public abstract Graph getGraph(Node graphNode) ;

    @Override
    public abstract void addGraph(Node graphName, Graph graph) ;
    
    @Override
    public abstract void removeGraph(Node graphName) ;

    @Override
    public void setDefaultGraph(Graph g)
    { throw new UnsupportedOperationException("DatasetGraph.setDefaultGraph") ; }
    
    @Override
    public void add(Quad quad) { throw new UnsupportedOperationException("DatasetGraph.add(Quad)") ; } 
    
    @Override
    public void delete(Quad quad) { throw new UnsupportedOperationException("DatasetGraph.delete(Quad)") ; }
    
    @Override
    public void add(Node g, Node s, Node p, Node o)     { add(new Quad(g,s,p,o)) ; }  
    @Override
    public void delete(Node g, Node s, Node p, Node o)  { delete(new Quad(g,s,p,o)) ; }
    
    private static final int DeleteBufferSize = 1000 ;
    @Override
    /** Simple implementation but done without assuming iterator.remove() */
    public void deleteAny(Node g, Node s, Node p, Node o) {
        // Delete in slices rather than assume .remove() on the iterator is
        // implemented.
        // We keep executing find(g, s, p, o) until we don't get a full slice.
        Quad[] buffer = new Quad[DeleteBufferSize];
        while (true) {
            Iterator<Quad> iter = find(g, s, p, o);
            // Get a slice
            int len = 0;
            for ( ; len < DeleteBufferSize ; len++ ) {
                if ( !iter.hasNext() )
                    break;
                buffer[len] = iter.next();
            }
            // Delete them.
            for ( int i = 0 ; i < len ; i++ ) {
                delete(buffer[i]);
                buffer[i] = null;
            }
            // Finished?
            if ( len < DeleteBufferSize )
                break;
        }
    }

    @Override
    public Iterator<Quad> find()
    { return find(Node.ANY, Node.ANY, Node.ANY, Node.ANY) ; }

    @Override
    public Iterator<Quad> find(Quad quad) {
        if ( quad == null )
            return find();
        return find(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
    }

    @Override
    public boolean contains(Quad quad) { return contains(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject()) ; }

    @Override
    public boolean contains(Node g, Node s, Node p, Node o) {
        Iterator<Quad> iter = find(g, s, p, o);
        boolean b = iter.hasNext();
        Iter.close(iter);
        return b;
    }
    
    protected static boolean isWildcard(Node g) {
        return g == null || g == Node.ANY;
    }

    @Override
    public void clear() {
        deleteAny(Node.ANY, Node.ANY, Node.ANY, Node.ANY);
    }

    @Override
    public boolean isEmpty() {
        return !contains(Node.ANY, Node.ANY, Node.ANY, Node.ANY);
    }

    @Override
    public long size() {
        return -1;
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void close()
    { }
    
    @Override
    public String toString()
    {
        // Using the size of the graphs would be better.
        IndentedLineBuffer out = new IndentedLineBuffer() ;
        WriterGraph.output(out, this, null) ;
        return out.asString() ;
    }

    // Helpers: See GraphUtils.
}
