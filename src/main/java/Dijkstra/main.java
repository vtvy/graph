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
package Dijkstra;

import search.Searcher;
import utilities.Settings;
import dataStructures.DFScodeSerializer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Sorts.descending;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.bson.Document;

public class main {

    static int APPROX = 0;
    static int EXACT = 1;

    static int FSM = 0;

    public static void main(String[] args) {
        int maxNumOfDistinctNodes = 1;

        // default frequency
        int freq = 1;

        //Creating a MongoDB client
        MongoClient client = MongoClients.create("mongodb+srv://test:test@graph.eywuuaq.mongodb.net/test");
        //Connecting to the database
        MongoDatabase db = client.getDatabase("grami");
        db.getCollection("subgraph").drop();
        MongoCollection subgraph = db.getCollection("subgraph");
//        String[] filenames = {"fb.lg", "citeseer.lg", "mico.lg", "p2p.lg","mydata9v9e1.lg"};
        String[] filenames = {"S_CC.lg"};
        int minK = 300;
        int maxK = 300;
        for (String filename : filenames) {
            for (int k = minK; k <= maxK; k = k + 10) {

                // parse the command line arguments
                // CommandLineParser.parse(args);
                // if(utilities.Settings.frequency>-1)
                // freq = utilities.Settings.frequency;
                Settings.fileName = filename;
                Searcher<String, String> sr = null;

                // Initialize the tool to check memory usage
                try {
                    if (Settings.fileName == null) {
                        System.out.println("You have to specify a dataset filename");
                        System.exit(1);
                    } else {
                        sr = new Searcher<String, String>(Settings.datasetsFolder + Settings.fileName, freq, 1, k);
                    }
                    long t0 = System.nanoTime();
                    // start mining
                    sr.initialize(subgraph);
                    System.out.println("In ra tap canh pho bien:");
//                    sr.kSubgraphs.forEach(action -> {
//                        System.out.println("- Canh co support: " + action.dfsCode.me.getFreq());
//                        String out = DFScodeSerializer.serialize(action.dfsCode.me);
//                        System.out.println(out + "\n");
//                    });
//                    System.out.println("************************************************");
//                    System.out.println("************************************************");
//                    System.out.println("************************************************");
//                    System.out.println("**********   Final minsub: " + sr.getMinsub() + "\n");

                    sr.search();
                    long timeUsage = System.nanoTime() - t0;
                    double actualMemUsed = printPeakHeapUsage();
                    // write output file for the following things:
                    // 1- time
                    // 2- number of resulted patterns
                    // 3- the list of frequent subgraphs
                    FileWriter fw;
                    try {
                        List<Document> results = (List<Document>) subgraph.find(gte("support", sr.getMinsub()))
                                .sort(descending("support"))
                                .into(new ArrayList<>());

                        String fName = "OutputK_" + k + "_" + Settings.fileName + ".txt";

                        fw = new FileWriter(fName);
                        fw.write("Time usage: " + timeUsage / 1000000000.0 + "\n");
                        fw.write("Memory usage: " + actualMemUsed + "MB" + "\n");
                        fw.write("Final minsub: " + sr.getMinsub() + "\n");
                        fw.write("k: " + k + "\n");
                        fw.write("Number of subgraphs: " + results.size() + "\n\n");

                        // write the frequent subgraphs
                        for (int i = 0; i < results.size(); i++) {
                            String out = (String) results.get(i).get("serialize");
                            fw.write("Subgraph " + i + ": ");
                            fw.write("support " + results.get(i).get("support") + "\n");
                            fw.write(out);
                            fw.write("\n");
                        }
                        fw.close();
                        //write to a sum
                        File totalFile = new File("Total_Output_" + filename + ".txt");
                        if (!totalFile.exists()) {
                            totalFile.createNewFile();
                            FileWriter totalFw = new FileWriter(totalFile);
                            totalFw.write("Time:Memory:Minsub:K:NumberOfSubgraphs\n");
                            totalFw.close();
                        }
                        FileWriter totalFw = new FileWriter(totalFile, true);
                        BufferedWriter bw = new BufferedWriter(totalFw);
                        bw.write(timeUsage / 1000000000.0 + ":");
                        bw.write(actualMemUsed + ":");
                        bw.write(sr.getMinsub() + ":");
                        bw.write(k + ":");
                        bw.write(results.size() + "\n");
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    System.out.println(e);
                }

            }

        }
    }

    public static double printPeakHeapUsage() {
        double result = 0;
        try {
            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            // we print the result in the console
            double total = 0;
            for (MemoryPoolMXBean memoryPoolMXBean : pools) {
                if (memoryPoolMXBean.getType() == MemoryType.HEAP) {
                    long peakUsed = memoryPoolMXBean.getPeakUsage().getUsed();
                    System.out.println(String.format("Peak used for: %s is %.2f", memoryPoolMXBean.getName(), (double) peakUsed / 1024 / 1024));
                    total = total + peakUsed;
                }
            }
            result = total / 1024 / 1024;
            System.out.println(String.format("Total heap peak used: %f MB", result));

        } catch (Throwable t) {
            System.err.println("Exception in agent: " + t);
        }
        return result;
    }
}
