package org.jlab.rec.atof.banks;

import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.atof.cluster.ATOFCluster;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;
import org.jlab.rec.alert.projections.TrackProjection;

/**
 * The {@code RecoBankWriter} writes the banks needed for the atof
 * reconstruction: track projections, hits and clusters info.
 *
 * @author pilleux
 */
public class RecoBankWriter {

    /**
     * Writes the bank of atof hits.
     *
     * @param event the {@link DataEvent} in which to add the bank
     * @param wedgeHits the {@link ArrayList} of {@link ATOFHit} containing the
     * wedge hits to be added to the bank
     * @param barHits the {@link ArrayList} of {@link BarHit} containing the bar
     * hits to be added to the bank
     *
     * @return {@link DataBank} the bank with all the hits read in the event.
     *
     */
    public static DataBank fillATOFHitBank(DataEvent event, ArrayList<ATOFHit> wedgeHits, ArrayList<BarHit> barHits) {

        ArrayList<ATOFHit> hitList = new ArrayList<>();
        hitList.addAll(wedgeHits);
        hitList.addAll(barHits);

        DataBank bank = event.createBank("ATOF::hits", hitList.size());

        if (bank == null) {
            System.err.println("COULD NOT CREATE A ATOF::hits BANK!!!!!!");
            return null;
        }

        for (int i = 0; i < hitList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setShort("clusterid", i, (short) hitList.get(i).getAssociatedClusterIndex());
            bank.setInt("sector", i, (int) hitList.get(i).getSector());
            bank.setInt("layer", i, (int) hitList.get(i).getLayer());
            bank.setInt("component", i, (int) hitList.get(i).getComponent());
            bank.setFloat("time", i, (float) hitList.get(i).getTime());
            bank.setFloat("x", i, (float) (hitList.get(i).getX()));
            bank.setFloat("y", i, (float) (hitList.get(i).getY()));
            bank.setFloat("z", i, (float) (hitList.get(i).getZ()));
            bank.setFloat("energy", i, (float) hitList.get(i).getEnergy());
        }
        return bank;
    }

    /**
     * Writes the bank of atof clusters.
     *
     * @param event the {@link DataEvent} in which to add the bank
     * @param clusterList the {@link ArrayList} of {@link ATOFCluster}
     * containing the clusters info to be added to the bank
     *
     * @return {@link DataBank} the bank with all the clusters built in the
     * event.
     *
     */
    public static DataBank fillATOFClusterBank(DataEvent event, ArrayList<ATOFCluster> clusterList) {

        DataBank bank = event.createBank("ATOF::clusters", clusterList.size());

        if (bank == null) {
            System.err.println("COULD NOT CREATE A ATOF::clusters BANK!!!!!!");
            return null;
        }

        for (int i = 0; i < clusterList.size(); i++) {
            bank.setShort("id", i, (short) (i + 1));
            bank.setInt("n_bar", i, (int) clusterList.get(i).getBarHits().size());
            bank.setInt("n_wedge", i, (int) clusterList.get(i).getWedgeHits().size());
            bank.setFloat("time", i, (float) clusterList.get(i).getTime());
            bank.setFloat("x", i, (float) (clusterList.get(i).getX()));
            bank.setFloat("y", i, (float) (clusterList.get(i).getY()));
            bank.setFloat("z", i, (float) (clusterList.get(i).getZ()));
            bank.setFloat("energy", i, (float) clusterList.get(i).getEnergy());
            bank.setFloat("inpathlength",i, (float) (clusterList.get(i).getInPathLength()));
            bank.setFloat("pathlength",i, (float) (clusterList.get(i).getPathLength()));
            bank.setShort("projID", i, (short) (clusterList.get(i).getIProj()));
        }
        return bank;
    }

    /**
     * Writes the bank of track projections.
     *
     * @param event the {@link DataEvent} in which to add the bank
     * @param projections the {@link ArrayList} of {@link TrackProjection}
     * containing the track projection info to be added to the bank
     *
     * @return {@link DataBank} the bank with all the projected tracks in the
     * event.
     *
     */
    public static DataBank fillProjectionsBank(DataEvent event, ArrayList<TrackProjection> projections) {

        DataBank bank = event.createBank("ALERT::projections", projections.size());

        if (bank == null) {
            System.err.println("COULD NOT CREATE A ALERT::projections BANK!!!!!!");
            return null;
        }
        for (int i = 0; i < projections.size(); i++) {
            TrackProjection projection = projections.get(i);
            bank.setShort("id", i, (short) (i + 1));
            bank.setShort("trackid", i, (short) projection.getTrackID());
            bank.setFloat("x_at_bar", i, (float) projection.getBarIntersect().x());
            bank.setFloat("y_at_bar", i, (float) projection.getBarIntersect().y());
            bank.setFloat("z_at_bar", i, (float) projection.getBarIntersect().z());
            bank.setFloat("l_at_bar", i, (float) projection.getBarPathLength());
            bank.setFloat("l_in_bar", i, (float) projection.getBarInPathLength());
            bank.setFloat("x_at_wedge", i, (float) projection.getWedgeIntersect().x());
            bank.setFloat("y_at_wedge", i, (float) projection.getWedgeIntersect().y());
            bank.setFloat("z_at_wedge", i, (float) projection.getWedgeIntersect().z());
            bank.setFloat("l_at_wedge", i, (float) projection.getWedgePathLength());
            bank.setFloat("l_in_wedge", i, (float) projection.getWedgeInPathLength());
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

        DataBank hitbank = this.fillATOFHitBank(event, wedgeHits, barHits);
        if (hitbank != null) {
            event.appendBank(hitbank);
        } else {
            return 1;
        }

        DataBank clusterbank = fillATOFClusterBank(event, clusterList);
        if (clusterbank != null) {
            event.appendBank(clusterbank);
        } else {
            return 1;
        }

        return 0;
    }
    
    /**
     * Appends the alert match banks to an event.
     *
     * @param event the {@link DataEvent} in which to append the banks
     * @param projections the {@link ArrayList} of {@link TrackProjection} containing the 
     * track projections info to be added
     *
     * @return 0 if it worked, 1 if it failed
     *
     */
    public int appendMatchBanks(DataEvent event, ArrayList<TrackProjection> projections) {

        DataBank projbank = this.fillProjectionsBank(event, projections);
        if (projbank != null) {
            event.appendBank(projbank);
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
