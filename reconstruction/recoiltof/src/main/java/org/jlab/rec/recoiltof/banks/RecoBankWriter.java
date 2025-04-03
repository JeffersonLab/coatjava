package org.jlab.rec.recoiltof.banks;

import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.recoiltof.cluster.RECOILTOFCluster;
import org.jlab.rec.recoiltof.hit.RECOILTOFHit;
import org.jlab.rec.recoiltof.hit.BarHit;


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
     * @param barHits the {@link ArrayList} of {@link BarHit} containing the bar
     * hits to be added to the bank
     *
     * @return {@link DataBank} the bank with all the hits read in the event.
     *
     */
    public static DataBank fillRECOILTOFHitBank(DataEvent event, ArrayList<BarHit> barHits) {

        ArrayList<RECOILTOFHit> hitList = new ArrayList<>();
        hitList.addAll(barHits);

        DataBank bank = event.createBank("RECOILTOF::hits", hitList.size());

        if (bank == null) {
            System.err.println("COULD NOT CREATE A RECOILTOF::hits BANK!!!!!!");
            return null;
        }

        for (int i = 0; i < hitList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setShort("clusterid", i, (short) hitList.get(i).getAssociatedClusterIndex());
            bank.setInt("sector", i, (int) hitList.get(i).getSector());
            bank.setInt("row", i, (int) hitList.get(i).getRow());
            bank.setInt("column", i, (int) hitList.get(i).getColumn());
            bank.setFloat("time", i, (float) hitList.get(i).getTime());
            bank.setFloat("x", i, (float) (hitList.get(i).getX()));
            bank.setFloat("y", i, (float) (hitList.get(i).getY()));
            bank.setFloat("z", i, (float) (hitList.get(i).getZ()));
            bank.setFloat("energy", i, (float) hitList.get(i).getEnergy());
        }
        return bank;
    }

    /**
     * Writes the bank of recoiltof clusters.
     *
     * @param event the {@link DataEvent} in which to add the bank
     * @param clusterList the {@link ArrayList} of {@link RECOILTOFCluster}
     * containing the clusters info to be added to the bank
     *
     * @return {@link DataBank} the bank with all the clusters built in the
     * event.
     *
     */
    public static DataBank fillRECOILTOFClusterBank(DataEvent event, ArrayList<RECOILTOFCluster> clusterList) {

        DataBank bank = event.createBank("RECOILTOF::clusters", clusterList.size());

        if (bank == null) {
            System.err.println("COULD NOT CREATE A RECOILTOF::clusters BANK!!!!!!");
            return null;
        }

        for (int i = 0; i < clusterList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setInt("N_bar", i, (int) clusterList.get(i).getBarHits().size());
            bank.setFloat("time", i, (float) clusterList.get(i).getTime());
            bank.setFloat("x", i, (float) (clusterList.get(i).getX()));
            bank.setFloat("y", i, (float) (clusterList.get(i).getY()));
            bank.setFloat("z", i, (float) (clusterList.get(i).getZ()));
            bank.setFloat("energy", i, (float) clusterList.get(i).getEnergy());
        }
        return bank;
    }


    /**
     * Appends the recoil tof banks to an event.
     *
     * @param event the {@link DataEvent} in which to append the banks
     * @param clusterList the {@link ArrayList} of {@link RECOILTOFCluster}
     * containing the clusters info to be added to the bank
     * @param barHits the {@link ArrayList} of {@link BarHit} containing the bar
     * hits info to be added
     *
     * @return 0 if it worked, 1 if it failed
     *
     */
    public int appendRECOILTOFBanks(DataEvent event, ArrayList<BarHit> barHits, ArrayList<RECOILTOFCluster> clusterList) {

        DataBank hitbank = this.fillRECOILTOFHitBank(event, barHits);
        if (hitbank != null) {
            event.appendBank(hitbank);
        } else {
            return 1;
        }

        DataBank clusterbank = fillRECOILTOFClusterBank(event, clusterList);
        if (clusterbank != null) {
            event.appendBank(clusterbank);
        } else {
            return 1;
        }

        return 0;
    }
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }

}
