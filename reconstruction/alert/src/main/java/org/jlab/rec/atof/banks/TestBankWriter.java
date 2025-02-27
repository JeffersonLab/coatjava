package org.jlab.rec.atof.banks;

import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.atof.cluster.ATOFCluster;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;

/**
 * The {@code RecoBankWriter} writes the banks needed for the atof
 * testing: hits and clusters info.
 *
 * @author pilleux
 */
public class TestBankWriter {


    public static DataBank fillATOFTestHitBank(DataEvent event, ArrayList<ATOFHit> wedgeHits, ArrayList<BarHit> barHits) {
        ArrayList<ATOFHit> hitList = new ArrayList<>();
        hitList.addAll(wedgeHits);
        hitList.addAll(barHits);
        DataBank bank = event.createBank("ATOF::testhits", hitList.size());
        if (bank == null) {
            System.err.println("COULD NOT CREATE A ATOF::testhits BANK!!!!!!");
            return null;
        }

        for (int i = 0; i < hitList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setShort("clusterid", i, (short) hitList.get(i).getAssociatedClusterIndex());
            bank.setInt("sector", i, (int) hitList.get(i).getSector());
            bank.setInt("layer", i, (int) hitList.get(i).getLayer());
            bank.setInt("component", i, (int) hitList.get(i).getComponent());
            bank.setInt("TDC", i, (int) hitList.get(i).getTdc());
            bank.setFloat("time", i, (float) hitList.get(i).getTime());
            bank.setFloat("x", i, (float) (hitList.get(i).getX()));
            bank.setFloat("y", i, (float) (hitList.get(i).getY()));
            bank.setFloat("z", i, (float) (hitList.get(i).getZ()));
            bank.setInt("TOT", i, (int) hitList.get(i).getTot());
            bank.setFloat("energy", i, (float) hitList.get(i).getEnergy());
        }
        return bank;
    }
    
    public static DataBank fillATOFTestClusterBank(DataEvent event, ArrayList<ATOFCluster> clusterList) {

        DataBank bank = event.createBank("ATOF::testclusters", clusterList.size());

        if (bank == null) {
            System.err.println("COULD NOT CREATE A ATOF::testclusters BANK!!!!!!");
            return null;
        }

        for (int i = 0; i < clusterList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setInt("N_bar", i, (int) clusterList.get(i).getBarHits().size());
            bank.setInt("N_wedge", i, (int) clusterList.get(i).getWedgeHits().size());
            bank.setInt("TDC", i, (int) clusterList.get(i).getTdc());
            bank.setFloat("time", i, (float) clusterList.get(i).getTime());
            bank.setFloat("x", i, (float) (clusterList.get(i).getX()));
            bank.setFloat("y", i, (float) (clusterList.get(i).getY()));
            bank.setFloat("z", i, (float) (clusterList.get(i).getZ()));
            bank.setInt("TOT", i, (int) clusterList.get(i).getTot());
            bank.setFloat("energy", i, (float) clusterList.get(i).getEnergy());
        }
        return bank;
    }

    /**
     * Appends the atof banks to an event.
     *
     * @param event the {@link DataEvent} in which to append the banks
     * @param clusterList the {@link ArrayList} of {@link ATOFCluster}
     * containing the clusters info to be added to the bank
     * @param wedgeHits the {@link ArrayList} of {@link ATOFHit} containing the
     * wedge hits info to be added
     * @param barHits the {@link ArrayList} of {@link BarHit} containing the bar
     * hits info to be added
     *
     * @return 0 if it worked, 1 if it failed
     *
     */
    public int appendATOFBanks(DataEvent event, ArrayList<ATOFHit> wedgeHits, ArrayList<BarHit> barHits, ArrayList<ATOFCluster> clusterList) {

        DataBank hitbank = this.fillATOFTestHitBank(event, wedgeHits, barHits);
        if (hitbank != null) {
            event.appendBank(hitbank);
        } else {
            return 1;
        }

        DataBank clusterbank = fillATOFTestClusterBank(event, clusterList);
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
