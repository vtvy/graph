package dataStructures;

import java.util.PriorityQueue;

public class ResultQueues {

    public PriorityQueue<FrequentSubgraph> kSubgraphs;
    public PriorityQueue<FrequentSubgraph> candidates;
    public int smallestOccurrences;
    public IntFrequency freqThreshold;
    public int resultNumber;

    public ResultQueues(int freq, int rsNum) {
        freqThreshold = new IntFrequency(freq);
        resultNumber = rsNum;
        kSubgraphs = new PriorityQueue<FrequentSubgraph>();
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
    }

    public void addToKSubgraph(FrequentSubgraph newSubgraph) {

        boolean isEqualMinsup = newSubgraph.dfsCode.frequency().compareTo(freqThreshold) == 0;
        kSubgraphs.add(newSubgraph);
        // if kSubgraphs is full

        if (kSubgraphs.size() > resultNumber && !isEqualMinsup) {

            // if current kSub size subtract occurrences of smallest element equal to 4
            // remove unused element and update minsub (freqThreshold)
            smallestOccurrences = 0;
            // count occurences of smallest kSubgraphs
            kSubgraphs.forEach((FrequentSubgraph eachSubgraph) -> {
                Frequency eachFreq = eachSubgraph.dfsCode.frequency();
                int isEqualTheshHold = freqThreshold.compareTo(eachFreq);
                if (isEqualTheshHold == 0) {
                    smallestOccurrences++;
                }
            });

            if (kSubgraphs.size() - smallestOccurrences >= resultNumber && smallestOccurrences > 0) {
                for (int i = 0; i < smallestOccurrences; i++) {
                    //     System.out.print("before remove :" + kSubgraphs.size());
                    kSubgraphs.remove();
                    //     System.out.print("after remove :" + kSubgraphs.size());
                }
            }

            Frequency newFreq = kSubgraphs.peek().dfsCode.frequency();
            // update minsub
            freqThreshold.update(newFreq);

        } else {
            if (kSubgraphs.size() == resultNumber) {
                Frequency newFreq = kSubgraphs.peek().dfsCode.frequency();
                // update minsub
                freqThreshold.update(newFreq);
            }
        }
    }

    public void copyToCandidates() {
        kSubgraphs.forEach((FrequentSubgraph eachEgde) -> {
            candidates.add((FrequentSubgraph) eachEgde);
        });
    }
    
    public FrequentSubgraph getTop() {
        return candidates.peek();
    }
    
    public boolean isCandidatesEmty() {
        return candidates.isEmpty();
    }
}
