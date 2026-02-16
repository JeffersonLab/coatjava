package org.jlab.rec.alert.banks;

import java.util.ArrayList;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.alert.projections.TrackProjection;
import org.jlab.rec.alert.AIpid.PIDResult;

import ai.djl.util.Pair;

/**
 * The ALERT {@code RecoBankWriter} writes the banks needed for the ALERT
 * reconstruction: track projections.
 *
 * @author Noemie Pilleux
 * @author Whit Armstrong
 */
public class RecoBankWriter {

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

    public int appendTrackMatchingAIBank(DataEvent event, ArrayList<Pair<Integer, Integer>> trackAIResults) {
        DataBank bank = event.createBank("ALERT::ai:projections", trackAIResults.size());
        if (bank == null) {
            System.err.println("COULD NOT CREATE A ALERT::ai:projections BANK!!!!!!");
            return 1;
        }
        for (int i = 0; i < trackAIResults.size(); i++) {
            Pair<Integer, Integer> pair = trackAIResults.get(i);
            bank.setInt("trackid", i, pair.getKey());
            bank.setInt("matched_atof_hit_id", i, pair.getValue());
        }
        event.appendBank(bank);

        return 0;
    }
    
    public static DataBank fillAIPIDBank(DataEvent event, List<PIDResult> results) {
        DataBank bank = event.createBank("ALERT::ai:pid", results.size());

        for (int i = 0; i < results.size(); i++) {
            PIDResult r = results.get(i);
            bank.setInt("trackid", i, r.getTrackid());
            bank.setInt("clusterid", i, r.getClusterid());
            bank.setInt("pid", i, r.getPid());

            bank.setFloat("prob_2212", i, r.prob2212());
            bank.setFloat("prob_45",   i, r.prob45());
            bank.setFloat("prob_46",   i, r.prob46());
            bank.setFloat("prob_47",   i, r.prob47());
            bank.setFloat("prob_49",   i, r.prob49());
        }
        return bank;
    }
    
    // @skuditha: Testing... Delete afterwards!
    
    public static DataBank fillAIPIDBank(DataEvent event) {
        DataBank bank = event.createBank("ALERT::ai:pid", 1);


            //PIDResult r = results.get(i);
            bank.setInt("trackid", 0, -99);
            bank.setInt("clusterid", 0, -99);
            bank.setInt("pid", 0, 0);

            bank.setFloat("prob_2212", 0, 0.0f);
            bank.setFloat("prob_45",   0, 0.0f);
            bank.setFloat("prob_46",   0, 0.0f);
            bank.setFloat("prob_47",   0, 0.0f);
            bank.setFloat("prob_49",   0, 0.0f);
        
        return bank;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }

}
