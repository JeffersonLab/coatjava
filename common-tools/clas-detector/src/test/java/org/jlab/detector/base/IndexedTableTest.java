package org.jlab.detector.base;

import org.junit.Test;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.detector.calib.utils.ConstantsManager;

public class IndexedTableTest {

    @Test
    public void run(){

        // Retrieve CCDB table:
        ConstantsManager manager = new ConstantsManager("default");
        manager.init("/daq/tt/ec");
        IndexedTable it = manager.getConstants(10, "/daq/tt/ec");

        // Store its valid crate/slot/channel indices:
        int csc[][] = new int[it.getRowCount()][3];
        for (int row=0; row<it.getRowCount(); ++row){
            csc[row][0] = Integer.parseInt((String)it.getValueAt(row,0));
            csc[row][1] = Integer.parseInt((String)it.getValueAt(row,1));
            csc[row][2] = Integer.parseInt((String)it.getValueAt(row,2));
        }

        // Read the table many times:
        for (int i=0; i<2e4; ++i){
            // Ignore initial reads, let it warm up first:
            if (i > 1e4) Benchmark.getInstance().resume("IT:GIV");
            // Read every table row:
            for (int j=0; j<csc.length; ++j){
                it.getIntValue("sector",csc[j][0],csc[j][1],csc[j][2]);
            }
            Benchmark.getInstance().pause("IT:GIV");
        }
        System.out.println(Benchmark.getInstance());
    }

    public static void main(String args[]){
        (new IndexedTableTest()).run();
    }
}
