package org.jlab.rec.alert.banks;

import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.alert.projections.TrackProjection;

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

        DataBank bank = event.createBank("ALERT::Projections", projections.size());

        if (bank == null) {
            System.err.println("COULD NOT CREATE A ALERT::Projections BANK!!!!!!");
            return null;
        }
        for (int i = 0; i < projections.size(); i++) {
            TrackProjection projection = projections.get(i);
            bank.setShort("id", i, (short) (i + 1));
            bank.setShort("trackID", i, (short) projection.getTrackID());
            bank.setFloat("x_at_bar", i, (float) projection.getBarIntersect().x());
            bank.setFloat("y_at_bar", i, (float) projection.getBarIntersect().y());
            bank.setFloat("z_at_bar", i, (float) projection.getBarIntersect().z());
            bank.setFloat("L_at_bar", i, (float) projection.getBarPathLength());
            bank.setFloat("L_in_bar", i, (float) projection.getBarInPathLength());
            bank.setFloat("x_at_wedge", i, (float) projection.getWedgeIntersect().x());
            bank.setFloat("y_at_wedge", i, (float) projection.getWedgeIntersect().y());
            bank.setFloat("z_at_wedge", i, (float) projection.getWedgeIntersect().z());
            bank.setFloat("L_at_wedge", i, (float) projection.getWedgePathLength());
            bank.setFloat("L_in_wedge", i, (float) projection.getWedgeInPathLength());
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

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }

}
