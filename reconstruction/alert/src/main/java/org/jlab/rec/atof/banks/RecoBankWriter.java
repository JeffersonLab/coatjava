package org.jlab.rec.atof.banks;

import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.atof.cluster.AtofCluster;
import org.jlab.rec.atof.hit.AtofHit;

/**
 *
 * @author npilleux
 */
public class RecoBankWriter {
    
    public static DataBank fillAtofHitBank(DataEvent event, ArrayList<AtofHit> hitlist) {
        
        DataBank bank =  event.createBank("ATOF::hits", hitlist.size());
        
        if (bank == null) {
            System.err.println("COULD NOT CREATE A ATOF::Hits BANK!!!!!!");
            return null;
        }
        
        for(int i =0; i< hitlist.size(); i++) {
            bank.setShort("id",i, (short)(i+1));
            bank.setInt("sector",i, (int) hitlist.get(i).getSector());
            bank.setInt("layer",i, (int) hitlist.get(i).getLayer());
            bank.setInt("component",i, (int) hitlist.get(i).getComponent());
            //bank.setShort("trkID",i, (short) hitlist.get(i).get_AssociatedTrkId());
            //bank.setShort("clusterid", i, (short) hitlist.get(i).get_AssociatedClusterID());
            bank.setFloat("time",i, (float) hitlist.get(i).getTime());
            bank.setFloat("x",i, (float) (hitlist.get(i).getX()));
            bank.setFloat("y",i, (float) (hitlist.get(i).getY()));
            bank.setFloat("z",i, (float) (hitlist.get(i).getZ()));
            bank.setFloat("energy",i, (float) hitlist.get(i).getEnergy());
            bank.setFloat("inlength",i, (float) (hitlist.get(i).getInpath_length())); 
            bank.setFloat("pathlength",i, (float) (hitlist.get(i).getPath_length())); 
        }
        return bank;
    }
    
    public static DataBank fillAtofClusterBank(DataEvent event, ArrayList<AtofCluster> clusterlist) {
        
        DataBank bank =  event.createBank("ATOF::clusters", clusterlist.size());
        
        if (bank == null) {
            System.err.println("COULD NOT CREATE A ATOF::Hits BANK!!!!!!");
            return null;
        }
        
        for(int i =0; i< clusterlist.size(); i++) {
            bank.setShort("id",i, (short)(i+1));
            bank.setInt("barsize",i, (int) clusterlist.get(i).getBarHits().size());
            bank.setInt("wedgesize",i, (int) clusterlist.get(i).getWedgeHits().size());
            bank.setFloat("time",i, (float) clusterlist.get(i).getTime());
            bank.setFloat("x",i, (float) (clusterlist.get(i).getX()));
            bank.setFloat("y",i, (float) (clusterlist.get(i).getY()));
            bank.setFloat("z",i, (float) (clusterlist.get(i).getZ()));
            bank.setFloat("energy",i, (float) clusterlist.get(i).getEnergy());
            bank.setFloat("inpathlength",i, (float) (clusterlist.get(i).getInpath_length())); 
            bank.setFloat("pathlength",i, (float) (clusterlist.get(i).getPath_length())); 
        }
        return bank;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
    }
    
}
