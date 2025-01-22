/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package org.jlab.rec.atof.banks;

import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.atof.hit.AtofHit;

/**
 *
 * @authors npilleux,churaman
 */
public class RecoBankWriter {
    
    // write useful information in the bank
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

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
    }
    
}
