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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.Vector;

import utilities.MyPair;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;
import com.mongodb.client.model.Updates;

import dataStructures.Canonizable;
import dataStructures.DFSCode;
import dataStructures.DFScodeSerializer;
import dataStructures.Frequency;
import dataStructures.FrequentSubgraph;
import dataStructures.Frequented;
import dataStructures.GSpanEdge;
import dataStructures.Graph;
import dataStructures.HPListGraph;
import dataStructures.IntFrequency;
import dataStructures.gEdgeComparator;
import dataStructures.myNode;
import java.util.Collection;
import org.bson.Document;
import org.bson.types.ObjectId;

public class Searcher<NodeType, EdgeType> {

    private Graph singleGraph;
    private IntFrequency freqThreshold;
    private int resultNumber;
    private int distanceThreshold;
    private ArrayList<Integer> sortedFrequentLabels;
    private ArrayList<Double> freqEdgeLabels;
    Map<GSpanEdge<NodeType, EdgeType>, DFSCode<NodeType, EdgeType>> initials;
    public PriorityQueue<FrequentSubgraph> kSubgraphs;
//    PriorityQueue<FrequentSubgraph> candidates;
    MongoCollection<Document> subgraph;
    ObjectId currentKey;
    Map<ObjectId, DFSCode<NodeType, EdgeType>> candidates;
    int smallestOccurrences;
    private int type;
    public ArrayList<HPListGraph<NodeType, EdgeType>> result;
    public static Hashtable<Integer, Vector<Integer>> neighborLabels;
    private String path;
    private Extender<NodeType, EdgeType> extender;
    private Collection<HPListGraph<NodeType, EdgeType>> ret;
    int graphNumber = 0;

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

    public void initialize(MongoCollection<Document> sgraph) {
        subgraph = sgraph;
//        kSubgraphs = new PriorityQueue<FrequentSubgraph>();
        candidates = new HashMap<ObjectId, DFSCode<NodeType, EdgeType>>();

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
            final DFSCode<NodeType, EdgeType> code = eit.next().getValue();
            Frequency support = code.frequency();
            if (freqThreshold.compareTo(support) > 0) {
                eit.remove();
            } else {
                ObjectId id = addToDB(((IntFrequency) support).intValue());
                candidates.put(id, code);
            }
        }

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

//        ret = new ArrayList<HPListGraph<NodeType, EdgeType>>();
        extender = algo.getExtender(this.freqThreshold.intValue());

        // while Qc.size > 0
        while (getCandidate()) {
            // get top of candidates
            DFSCode<NodeType, EdgeType> node = candidates.get(currentKey);

//            FrequentSubgraph topCandidate = candidates.poll();
            //check support
//            boolean isSatifyMinSub = freqThreshold.compareTo(node.frequency()) <= 0;
            // if greater than or equal minsub
//            if (isSatifyMinSub)
            // call search method to extend
            search(node);
        }
//        result = (ArrayList<HPListGraph<NodeType, EdgeType>>) ret;
    }

//    private int getNumOfDistinctLabels(HPListGraph<NodeType, EdgeType> list) {
//        HashSet<Integer> difflabels = new HashSet<Integer>();
//        for (int i = 0; i < list.getNodeCount(); i++) {
//            int label = (Integer) list.getNodeLabel(i);
//            if (!difflabels.contains(label)) {
//                difflabels.add(label);
//            }
//        }
//
//        return difflabels.size();
//
//    }
    public Graph getSingleGraph() {
        return singleGraph;
    }

//    protected void addToKSubgraph(FrequentSubgraph newSubgraph) {
//
//        int isLowest = newSubgraph.dfsCode.frequency().compareTo(freqThreshold);
//        kSubgraphs.add(newSubgraph);
//
//        // in kSubgraphs
//        System.out.println("----------------------- Sau khi them subgraph co support: " + newSubgraph.dfsCode.frequency());
//        System.out.println("----------------------- Hang doi luc nay la: ");
//        System.out.println(Arrays.deepToString(kSubgraphs.toArray()));
//        kSubgraphs.forEach(action -> {
//            System.out.println("- Graph co support: " + action.dfsCode.me.getFreq());
//            String out = DFScodeSerializer.serialize(action.dfsCode.me);
//            System.out.println(out + "\n");
//        });
//        // if kSubgraphs is full
//        if (kSubgraphs.size() > resultNumber && isLowest != 0) {
//
//            // if current kSub size subtract occurrences of smallest element equal to 4
//            // remove unused element and update minsub (freqThreshold)
//            while (kSubgraphs.size() > resultNumber) {
//                smallestOccurrences = 0;
//                // count occurences of smallest kSubgraphs
//                kSubgraphs.forEach((FrequentSubgraph eachSubgraph) -> {
//                    Frequency eachFreq = eachSubgraph.dfsCode.frequency();
//                    int isEqualTheshHold = freqThreshold.compareTo(eachFreq);
//                    if (isEqualTheshHold == 0) {
//                        smallestOccurrences++;
//                    }
//                });
//
//                if (kSubgraphs.size() - smallestOccurrences >= resultNumber) {
//                    for (int i = 0; i < smallestOccurrences; i++) {
//                        //     System.out.println("before remove :" + kSubgraphs.size());
//                        kSubgraphs.remove();
//                        //     System.out.println("after remove :" + kSubgraphs.size());
//                    }
//                } else {
//                    break;
//                }
//            }
//
//        }
//
//        if (kSubgraphs.size() >= resultNumber) {
//            Frequency newFreq = kSubgraphs.peek().dfsCode.frequency();
//            // update minsub
//            if (freqThreshold.compareTo(newFreq) < 0) {
//                //     System.out.print("before update freq: " + freqThreshold);
//                freqThreshold.update(newFreq);
//                //     //     System.out.print("after update freq: " + freqThreshold);
//            }
//
//        }
//    }
    @SuppressWarnings("unchecked")
    private void search(final SearchLatticeNode<NodeType, EdgeType> node) { // RECURSIVE NODES SEARCH

        // //     System.out.println("Getting Children");
        System.out.println("--------------------------------------------------------- ");
        System.out.println("Danh sach con cua node: " + node);
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
//                    FrequentSubgraph newChildSubgraph
//                            = new FrequentSubgraph((DFSCode<NodeType, EdgeType>) child);
                    ObjectId id = addToDB(((IntFrequency) childFreq).intValue());
                    candidates.put(id, (DFSCode<NodeType, EdgeType>) child);
                }
            }

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
        sequenlizeToPrint((DFSCode<NodeType, EdgeType>) node);
//        node.store(ret);
//        node.finalizeIt();
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

    void checkAndUpdateMinSub() {
        Document kGraph = subgraph.find()
                .sort(descending("support"))
                .skip(resultNumber - 1)
                .first();
        if (kGraph != null) {
            Frequency newMinsub = new IntFrequency((int) kGraph.get("support"));
            if (freqThreshold.compareTo(newMinsub) < 0) {
                freqThreshold.update(newMinsub);
                System.out.println(" --> New Final minsub: " + kGraph.get("support"));
                subgraph.deleteMany(lt("support", freqThreshold.intValue()));
            }
        }

    }

    boolean getCandidate() {
        Document graph = subgraph.find(and(eq("isExtender", false),
                gte("support", freqThreshold.intValue())))
                .sort(descending("support"))
                .first();
        if (graph != null) {
            currentKey = (ObjectId) graph.get("_id");
            return true;
        }
        return false;
    }

    ObjectId addToDB(int support) {
        ObjectId id = new ObjectId();
        Document newGraph = new Document("_id", id)
                .append("support", support)
                .append("isExtender", false)
                .append("serialize", "");
        subgraph.insertOne(newGraph);
        graphNumber++;
        if (graphNumber >= resultNumber && freqThreshold.intValue() - support < 0) {
            checkAndUpdateMinSub();
        }
        return id;
    }

    void sequenlizeToPrint(DFSCode<NodeType, EdgeType> node) {
        String serialize = DFScodeSerializer.serialize(node.me);
        subgraph.findOneAndUpdate(eq("_id", currentKey),
                Updates.combine(Updates.set("isExtender", true), Updates.set("serialize", serialize)));
        candidates.remove(currentKey);
    }

}
