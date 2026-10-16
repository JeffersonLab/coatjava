package org.jlab.service.recoil.tof;

import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 * The {@code RecoBankWriter} writes the banks needed for the recoil tof
 * reconstruction: hits and clusters info.
 *
 * @author pilleux, Nilanga Wickramaarachchi
 */
public class RecoBankWriter {
    
    /**
     * Writes the bank of recoil tof hits.
     *
     * @param event the {@link DataEvent} in which to add the bank
     * @param rtofHits the {@link ArrayList} of {@link RTOFHit} containing the rtof
     * hits to be added to the bank
     *
     * @return {@link DataBank} the bank with all the hits read in the event.
     *
     */
    public static DataBank fillRTOFRawHitBank(DataEvent event, ArrayList<RTOFHit> rtofHits) {
        
        ArrayList<RTOFRawHit> hitList = new ArrayList<>();
        hitList.addAll(rtofHits);
        
        DataBank bank = event.createBank("RTOF::hits", hitList.size());
        
        if (bank == null) {
            System.err.println("COULD NOT CREATE A RTOF::hits BANK!!!!!!");
            return null;
        }
        
        for (int i = 0; i < hitList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setShort("clusterid", i, (short) hitList.get(i).getAssociatedClusterIndex());
            bank.setByte("sector", i, (byte) hitList.get(i).getSector());
            bank.setByte("layer", i, (byte) hitList.get(i).getRow());
            bank.setByte("component", i, (byte) hitList.get(i).getColumn());
            bank.setFloat("time", i, (float) hitList.get(i).getTime());
            bank.setFloat("x", i, (float) (hitList.get(i).getX()));
            bank.setFloat("y", i, (float) (hitList.get(i).getY()));
            bank.setFloat("z", i, (float) (hitList.get(i).getZ()));
            bank.setFloat("energy", i, (float) hitList.get(i).getEnergy());
        }
        return bank;
    }
    
    /**
     * Writes the bank of rtof clusters.
     *
     * @param event the {@link DataEvent} in which to add the bank
     * @param clusterList the {@link ArrayList} of {@link RTOFCluster}
     * containing the clusters info to be added to the bank
     *
     * @return {@link DataBank} the bank with all the clusters built in the
     * event.
     *
     */
    public static DataBank fillRTOFClusterBank(DataEvent event, ArrayList<RTOFCluster> clusterList) {
        
        DataBank bank = event.createBank("RTOF::clusters", clusterList.size());
        
        if (bank == null) {
            System.err.println("COULD NOT CREATE A RTOF::clusters BANK!!!!!!");
            return null;
        }
        
        for (int i = 0; i < clusterList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setShort("size", i, (short) clusterList.get(i).getRTOFHits().size());
            bank.setByte("sector", i, (byte) clusterList.get(i).getSectorMaxHit());
            bank.setFloat("time", i, (float) clusterList.get(i).getTime());
            bank.setFloat("x", i, (float) (clusterList.get(i).getX()));
            bank.setFloat("y", i, (float) (clusterList.get(i).getY()));
            bank.setFloat("z", i, (float) (clusterList.get(i).getZ()));
            bank.setFloat("energy", i, (float) clusterList.get(i).getEnergy());
        }
        return bank;
    }
    
    /**
     * Appends the rtof banks to an event.
     *
     * @param event the {@link DataEvent} in which to append the banks
     * @param clusterList the {@link ArrayList} of {@link RTOFCluster}
     * containing the clusters info to be added to the bank
     * @param rtofHits the {@link ArrayList} of {@link RTOFHit} containing the bar
     * hits info to be added
     *
     * @return 0 if it worked, 1 if it failed
     *
     */
    public int appendRTOFBanks(DataEvent event, ArrayList<RTOFHit> rtofHits, ArrayList<RTOFCluster> clusterList) {
        
        DataBank hitbank = this.fillRTOFRawHitBank(event, rtofHits);
        if (hitbank != null) {
            event.appendBank(hitbank);
        } else {
            return 1;
        }
        
        DataBank clusterbank = fillRTOFClusterBank(event, clusterList);
        if (clusterbank != null) {
            event.appendBank(clusterbank);
        } else {
            return 1;
        }
        
        return 0;
    }
}
