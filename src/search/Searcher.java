/**
 * Copyright 2014 Mohammed Elseidy, Ehab Abdelhamid
 *
 * This file is part of Grami.
 *
 * Grami is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Grami is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Grami.  If not, see <http://www.gnu.org/licenses/>.
 */
package search;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.Vector;

import statistics.Statistics;
import utilities.MyPair;
import utilities.Settings;
import utilities.StopWatch;
import Dijkstra.*;
import dataStructures.Canonizable;
import dataStructures.DFSCode;
import dataStructures.DFScodeSerializer;
import dataStructures.Edge;
import dataStructures.Frequency;
import dataStructures.FrequentSubgraph;
import dataStructures.Frequented;
import dataStructures.GSpanEdge;
import dataStructures.Graph;
import dataStructures.HPListGraph;
import dataStructures.IntFrequency;
import dataStructures.StaticData;
import dataStructures.gEdgeComparator;
import dataStructures.myNode;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class Searcher<NodeType, EdgeType> {

    private Graph singleGraph;
    private IntFrequency freqThreshold;
    private int resultNumber;
    private int distanceThreshold;
    private ArrayList<Integer> sortedFrequentLabels;
    private ArrayList<Double> freqEdgeLabels;
    Map<GSpanEdge<NodeType, EdgeType>, DFSCode<NodeType, EdgeType>> initials;
    public PriorityQueue<FrequentSubgraph> kSubgraphs;
    PriorityQueue<FrequentSubgraph> candidates;
    int smallestOccurrences;
    private int type;
    public ArrayList<HPListGraph<NodeType, EdgeType>> result;
    public static Hashtable<Integer, Vector<Integer>> neighborLabels;
    private String path;
    private Extender<NodeType, EdgeType> extender;
    private Collection<HPListGraph<NodeType, EdgeType>> ret;

    public Searcher(String path, int freqThreshold, int shortestDistance, int resultNumber) throws Exception {
        this.freqThreshold = new IntFrequency(freqThreshold);
        this.resultNumber = resultNumber;
        //     System.out.println(this.resultNumber);
        this.distanceThreshold = shortestDistance;
        singleGraph = new Graph(1, freqThreshold);
        singleGraph.loadFromFile(path);
        this.path = path;
        sortedFrequentLabels = singleGraph.getSortedFreqLabels();
        freqEdgeLabels = singleGraph.getFreqEdgeLabels();
        singleGraph.printFreqNodes();
        singleGraph.setShortestPaths_1hop();
    }

    public void initialize() {
        kSubgraphs = new PriorityQueue<FrequentSubgraph>();

        initials = new TreeMap<GSpanEdge<NodeType, EdgeType>, DFSCode<NodeType, EdgeType>>(
                new gEdgeComparator<NodeType, EdgeType>());
        HashMap<Integer, HashMap<Integer, myNode>> freqNodesByLabel = singleGraph.getFreqNodesByLabel();
        HashSet<Integer> contains = new HashSet<Integer>();
        for (Iterator<java.util.Map.Entry<Integer, HashMap<Integer, myNode>>> it = freqNodesByLabel.entrySet()
                .iterator(); it.hasNext();) {

            java.util.Map.Entry<Integer, HashMap<Integer, myNode>> ar = it.next();
            int firstLabel = ar.getKey();
            contains.clear();
            HashMap<Integer, myNode> tmp = ar.getValue();
            for (Iterator<myNode> iterator = tmp.values().iterator(); iterator.hasNext();) {
                myNode node = iterator.next();
                int firstNodeID = node.getID();
                HashMap<Integer, ArrayList<MyPair<Integer, Double>>> neighbours = node.getReachableWithNodes();
                if (neighbours != null) {
                    for (Iterator<Integer> iter = neighbours.keySet().iterator(); iter.hasNext();) {
                        int secondLabel = iter.next();
                        int labelA = sortedFrequentLabels.indexOf(firstLabel);
                        int labelB = sortedFrequentLabels.indexOf(secondLabel);
                        // iterate over all neighbor nodes to get edge labels as well
                        for (Iterator<MyPair<Integer, Double>> iter1 = neighbours.get(secondLabel).iterator(); iter1
                                .hasNext();) {
                            MyPair<Integer, Double> mp = iter1.next();
                            double edgeLabel = mp.getB();
                            if (!freqEdgeLabels.contains(edgeLabel)) {
                                continue;
                            }

                            int secondNodeID = mp.getA();

                            final GSpanEdge<NodeType, EdgeType> gedge = new GSpanEdge<NodeType, EdgeType>().set(0, 1,
                                    labelA, (int) edgeLabel, labelB, 0, firstLabel, secondLabel);

                            if (!initials.containsKey(gedge)) {
                                final ArrayList<GSpanEdge<NodeType, EdgeType>> parents = new ArrayList<GSpanEdge<NodeType, EdgeType>>(
                                        2);
                                parents.add(gedge);
                                parents.add(gedge);

                                HPListGraph<NodeType, EdgeType> lg = new HPListGraph<NodeType, EdgeType>();
                                gedge.addTo(lg);
                                DFSCode<NodeType, EdgeType> code = new DFSCode<NodeType, EdgeType>(sortedFrequentLabels,
                                        freqEdgeLabels, singleGraph, null).set(lg, gedge, gedge, parents);

                                initials.put(gedge, code);
                            }
                        }
                    }
                }
            }
        }

//        ArrayList<Thread> fEdgeThreads = new ArrayList<Thread>();
        for (final Iterator<Map.Entry<GSpanEdge<NodeType, EdgeType>, DFSCode<NodeType, EdgeType>>> eit = initials
                .entrySet().iterator(); eit.hasNext();) {
//            Thread t1;
//            t1 = new Thread() {
//                @Override
//                public void run() {
//                    synchronized (kSubgraphs) {
            final DFSCode<NodeType, EdgeType> code = eit.next().getValue();
            if (freqThreshold.compareTo(code.frequency()) > 0) {
                eit.remove();
            } else {
                FrequentSubgraph newSubGraph = new FrequentSubgraph(code);
                addToKSubgraph(newSubGraph);
            }
        }
//                }
//            };
//            t1.start();
//            fEdgeThreads.add(t1);
//        }
//        for (int i = 0; i < fEdgeThreads.size(); i++) {
//            try {
//                fEdgeThreads.get(i).join();
//            } catch (InterruptedException e) {
//                System.out.println(e);
//            }
//        }
        // get the top of kSubgraph
        // kSubgraphs.poll();
        FrequentSubgraph smallestSub = kSubgraphs.peek();
        // PriorityQueue kSubgraphsCopy = new
        // PriorityQueue<FrequentSubgraph>(kSubgraphs);

        candidates = new PriorityQueue<FrequentSubgraph>(
                (FrequentSubgraph a, FrequentSubgraph b) -> {
                    int compare = a.dfsCode.frequency().compareTo(b.dfsCode.frequency());
                    if (compare < 0) {
                        return 1;
                    }
                    if (compare > 0) {
                        return -1;
                    }
                    return 0;
                });

        // copy kSubgraph into candidates
        // while (!kSubgraphsCopy.isEmpty()) {
        // candidates.add((FrequentSubgraph) kSubgraphsCopy.poll());
        // }
        kSubgraphs.forEach((FrequentSubgraph eachEgde) -> {
            candidates.add((FrequentSubgraph) eachEgde);
        });

        // get the top of candidates queue
        // candidates.poll();
        FrequentSubgraph largestCandidate = candidates.peek();

        neighborLabels = new Hashtable();

        for (final Iterator<Map.Entry<GSpanEdge<NodeType, EdgeType>, DFSCode<NodeType, EdgeType>>> eit = initials
                .entrySet().iterator(); eit.hasNext();) {
            final DFSCode<NodeType, EdgeType> code = eit.next().getValue();

            // add possible neighbor labels for each label
            int labelA;
            int labelB;
            GSpanEdge<NodeType, EdgeType> edge = code.getFirst();
            labelA = edge.getThelabelA();
            labelB = edge.getThelabelB();
            Vector temp = neighborLabels.get(labelA);
            if (temp == null) {
                temp = new Vector();
                neighborLabels.put(labelA, temp);
            }
            temp.addElement(labelB);

            // now the reverse
            temp = neighborLabels.get(labelB);
            if (temp == null) {
                temp = new Vector();
                neighborLabels.put(labelB, temp);
            }
            temp.addElement(labelA);
        }
    }

    public void search() {
        Algorithm<NodeType, EdgeType> algo = new Algorithm<NodeType, EdgeType>();
        algo.setInitials(initials);

        ret = new ArrayList<HPListGraph<NodeType, EdgeType>>();

        extender = algo.getExtender(this.freqThreshold.intValue());

        // while Qc.size > 0
        while (!candidates.isEmpty()) {
            // get top of candidates
            FrequentSubgraph topCandidate = candidates.poll();
            //check support
            boolean isSatifyMinSub = freqThreshold.compareTo(topCandidate.dfsCode.frequency()) <= 0;
            // if greater than or equal minsub
            if (isSatifyMinSub) {
                // call search method to extend
                search(topCandidate.dfsCode);
                // remove frequent edge labels that are already processed - test test test
                // before approval
                // double edgeLabel =
                // Double.parseDouble(topCandidate.dfsCode.getHPlistGraph().getEdgeLabel(topCandidate.dfsCode.getHPlistGraph().getEdge(0,
                // 1)).toString());
                // int node1Label =
                // Integer.parseInt(topCandidate.dfsCode.getHPlistGraph().getNodeLabel(0).toString());
                // int node2Label =
                // Integer.parseInt(topCandidate.dfsCode.getHPlistGraph().getNodeLabel(1).toString());
                // String signature;
                // if (node1Label < node2Label) {
                // signature = node1Label + "_" + edgeLabel + "_" + node2Label;
                // } else {
                // signature = node2Label + "_" + edgeLabel + "_" + node1Label;
                // }
                // StaticData.hashedEdges.remove(signature);

                // if (VERBOSE) {
                // out.println("\tdone (" + (System.currentTimeMillis() - time)
                // + " ms)");
                // }
            } else {
                candidates.clear();
            }
        }
        result = (ArrayList<HPListGraph<NodeType, EdgeType>>) ret;
    }

    private int getNumOfDistinctLabels(HPListGraph<NodeType, EdgeType> list) {
        HashSet<Integer> difflabels = new HashSet<Integer>();
        for (int i = 0; i < list.getNodeCount(); i++) {
            int label = (Integer) list.getNodeLabel(i);
            if (!difflabels.contains(label)) {
                difflabels.add(label);
            }
        }

        return difflabels.size();

    }

    public Graph getSingleGraph() {
        return singleGraph;
    }

    protected void addToKSubgraph(FrequentSubgraph newSubgraph) {

        int isLowest = newSubgraph.dfsCode.frequency().compareTo(freqThreshold);
        kSubgraphs.add(newSubgraph);

        // in kSubgraphs
        System.out.println("----------------------- Sau khi them subgraph co support: " + newSubgraph.dfsCode.frequency());
        System.out.println("----------------------- Hang doi luc nay la: ");
        System.out.println(Arrays.deepToString(kSubgraphs.toArray()));
        kSubgraphs.forEach(action -> {
            System.out.println("- Graph co support: " + action.dfsCode.me.getFreq());
            String out = DFScodeSerializer.serialize(action.dfsCode.me);
            System.out.println(out + "\n");
        });
        // if kSubgraphs is full
        if (kSubgraphs.size() > resultNumber && isLowest != 0) {

            // if current kSub size subtract occurrences of smallest element equal to 4
            // remove unused element and update minsub (freqThreshold)
            while (kSubgraphs.size() > resultNumber) {
                smallestOccurrences = 0;
                // count occurences of smallest kSubgraphs
                kSubgraphs.forEach((FrequentSubgraph eachSubgraph) -> {
                    Frequency eachFreq = eachSubgraph.dfsCode.frequency();
                    int isEqualTheshHold = freqThreshold.compareTo(eachFreq);
                    if (isEqualTheshHold == 0) {
                        smallestOccurrences++;
                    }
                });

                if (kSubgraphs.size() - smallestOccurrences >= resultNumber) {
                    for (int i = 0; i < smallestOccurrences; i++) {
                        //     System.out.println("before remove :" + kSubgraphs.size());
                        kSubgraphs.remove();
                        //     System.out.println("after remove :" + kSubgraphs.size());
                    }
                } else {
                    break;
                }
            }

        }

        if (kSubgraphs.size() >= resultNumber) {
            Frequency newFreq = kSubgraphs.peek().dfsCode.frequency();
            // update minsub
            if (freqThreshold.compareTo(newFreq) < 0) {
                //     System.out.print("before update freq: " + freqThreshold);
                freqThreshold.update(newFreq);
                //     //     System.out.print("after update freq: " + freqThreshold);
            }

        }
    }

    @SuppressWarnings("unchecked")
    private void search(final SearchLatticeNode<NodeType, EdgeType> node) { // RECURSIVE NODES SEARCH

        // //     System.out.println("Getting Children");
        System.out.println("--------------------------------------------------------- ");
        System.out.println("Danh sach con cua node: \n" + node);
        Frequency NodeFreq = ((Frequented) node).frequency();
        System.out.println("--> Support: " + NodeFreq);

        final Collection<SearchLatticeNode<NodeType, EdgeType>> tmp = extender
                .getChildren(node, freqThreshold);

        // //     System.out.println("finished Getting Children");
        // //     System.out.println(node.getLevel());
        for (final SearchLatticeNode<NodeType, EdgeType> child : tmp) {

            // if (VVVERBOSE) {
            // out.println("doing " + child);
            // }
            // //     System.out.println(" branching into: "+child);
            // //     System.out.println(" ---------------------");
            // check support
//            Frequency childFreq = ((Frequented) child).frequency();
//            System.out.println("* Subgraph con co support: " + childFreq);
            System.out.println("* Subgraph con: ");
            String out = DFScodeSerializer.serialize(((DFSCode<NodeType, EdgeType>) child).me);
            System.out.println(out + "\n");
        }

//        ArrayList<Thread> searchThreads = new ArrayList<Thread>();
        for (final SearchLatticeNode<NodeType, EdgeType> child : tmp) {
//            Thread t2;
//            t2 = new Thread() {
//                @Override
//                public void run() {
//                    synchronized (kSubgraphs) {
            Frequency childFreq = ((Frequented) child).frequency();
            System.out.println("$$$ Bat dau Check cho supgraph con co support: " + childFreq);
            String out = DFScodeSerializer.serialize(((DFSCode<NodeType, EdgeType>) child).me);
            System.out.println(out + "\n");
            boolean isSatifyMinSub = freqThreshold.compareTo(childFreq) <= 0;
            if (isSatifyMinSub) {
                //check isCanical
                final Canonizable can = (Canonizable) child;
                if (can.isCanonical()) {
                    FrequentSubgraph newChildSubgraph
                            = new FrequentSubgraph((DFSCode<NodeType, EdgeType>) child);
                    // add to Qk and update minsub
                    addToKSubgraph(newChildSubgraph);
                    // add to Qc
                    candidates.add(newChildSubgraph);
//                         System.out.println("success add child");
                }
            }

            System.out.println(" --> New Final minsub: " + freqThreshold + "\n");
        }
//                }
//            };
//            t2.start();
//            searchThreads.add(t2);
//        }
//        for (int i = 0; i < tmp.size(); i++) {
//            try {
//                searchThreads.get(i).join();
//            } catch (InterruptedException e) {
//                System.out.println(e);
//            }
//        }

        node.store(ret);
        node.finalizeIt();
// if (VVERBOSE) {
        // out.println("node " + node + " done. Store: " + node.store()
        // + " children " + tmp.size() + " freq "
        // + ((Frequented) node).frequency());
        // }

//        if (node.store()) {
//            node.store(ret);
//        } else {
//            node.release();
//        }
//
//        node.finalizeIt();
    }

    public int getMinsub() {
        return freqThreshold.intValue();
    }
}
