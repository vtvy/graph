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

import java.awt.Point;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Timer;

import automorphism.Automorphism;

import CSP.ConstraintGraph;
import CSP.DFSSearch;

import pruning.SPpruner;

import search.Searcher;
import statistics.DistinctLabelStat;
import statistics.TimedOutSearchStats;

import utilities.CommandLineParser;
import utilities.DfscodesCache;
import utilities.MyPair;
import utilities.Settings;
import utilities.StopWatch;

import dataStructures.DFSCode;
import dataStructures.DFScodeSerializer;
import dataStructures.Graph;
import dataStructures.HPListGraph;
import dataStructures.Query;
import dataStructures.StaticData;
import decomposer.Decomposer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

public class main {

    static int APPROX = 0;
    static int EXACT = 1;

    static int FSM = 0;

    public static void main(String[] args) {
        int maxNumOfDistinctNodes = 1;

        // default frequency
        int freq = 1;
        int minK = 10;
        int maxK = 10;
//        String[] filenames = {"fb.lg", "citeseer.lg", "mico.lg", "p2p.lg","mydata9v9e1.lg"};
        String[] filenames = {"S_CC.lg"};

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
                    sr.initialize();
                    System.out.println("In ra tap canh pho bien:");
                    sr.kSubgraphs.forEach(action -> {
                        System.out.println("- Canh co support: " + action.dfsCode.me.getFreq());
                        String out = DFScodeSerializer.serialize(action.dfsCode.me);
                        System.out.println(out + "\n");
                    });
                    System.out.println("************************************************");
                    System.out.println("************************************************");
                    System.out.println("************************************************");
                    System.out.println("**********   Final minsub: " + sr.getMinsub() + "\n");

                    sr.search();
                    long timeUsage = System.nanoTime() - t0;
                    double actualMemUsed = printPeakHeapUsage();
                    // write output file for the following things:
                    // 1- time
                    // 2- number of resulted patterns
                    // 3- the list of frequent subgraphs
                    FileWriter fw;
                    try {
                        String fName = "OutputK_" + k + "_" + Settings.fileName + ".txt";

                        fw = new FileWriter(fName);
                        fw.write("Time usage: " + timeUsage / 1000000000.0 + "\n");
                        fw.write("Memory usage: " + actualMemUsed + "MB" + "\n");
                        fw.write("Final minsub: " + sr.getMinsub() + "\n");
                        fw.write("k: " + k + "\n");
                        fw.write("Number of subgraphs: " + sr.result.size() + "\n\n");

                        // write the frequent subgraphs
                        for (int i = 0; i < sr.result.size(); i++) {
                            String out = DFScodeSerializer.serialize(sr.result.get(i));

                            fw.write("Subgraph " + i + ": ");
                            fw.write("support " + sr.result.get(i).getFreq() + "\n");
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
                        bw.write(sr.result.size() + "\n");
                        bw.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
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
