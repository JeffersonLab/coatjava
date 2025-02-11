package org.jlab.rec.atof.trackMatch;

import java.util.ArrayList;
import java.util.List;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.clas.tracking.trackrep.Helix;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.rec.atof.constants.Parameters;

/**
 * The {@code TrackProjector} class projects ahdc tracks to the inner surfaces
 * of the bar and wedges of the atof
 *
 * <p>
 * Uses ahdc track bank information (for now position, momentum) Creates a
 * {@link TrackProjection} for each track.
 * </p>
 *
 * <p>
 * TO DO: - replace hardcoded values with database values. - magnetic field ?
 * use swimmer tools? - charge ?
 * </p>
 *
 * @author pilleux
 */
public class TrackProjector {

    /**
     * projections of tracks.
     */
    private List<TrackProjection> projections;
    
    /**
     * solenoid magnitude
     */
    private Double b;

    /**
     * Default constructor that initializes the list of projections as new empty
     * list and the magnetic field to 5T.
     */
    public TrackProjector() {
        projections = new ArrayList<TrackProjection>();
        this.b = 5.0;
    }

    /**
     * Gets the list of track projections.
     *
     * @return a {@link List} of {@link TrackProjection} objects representing
     * the projections.
     */
    public List<TrackProjection> getProjections() {
        return projections;
    }
    
     /**
     * Gets the solenoid magnitude
     *
     * @return solenoid magnitude
     */
    public Double getB() {
        return b;
    }

    /**
     * Sets the list of track projections.
     *
     * @param Projections a {@link List} of {@link TrackProjection}.
     */
    public void setProjections(List<TrackProjection> Projections) {
        this.projections = Projections;
    }
    
     /**
     * Sets the solenoid magnitude.
     *
     * @param B a double.
     */
    public void setB(Double B) {
        this.b = B;
    }

    /**
     * Projects the ahdc tracks in the event onto the atof using a {@link Helix}
     * model.
     *
     * @param event the {@link DataEvent} containing track data to be projected.
     */
    public void projectTracks(DataEvent event) {//, CalibrationConstantsLoader ccdb) {

        projections.clear();
        
        String track_bank_name = "AHDC::Track";

        if (event == null) { // check if there is an event
            //System.out.print(" no event \n");
        } else if (event.hasBank(track_bank_name) == false) {
            // check if there are ahdc tracks in the event
            //System.out.print("no tracks \n");
        } else {
            DataBank bank = event.getBank(track_bank_name);
            int nt = bank.rows(); // number of tracks 
            TrackProjection projection = new TrackProjection();
            DataBank outputBank = event.createBank("AHDC::Projections", nt);
            for (int i = 0; i < nt; i++) {

                double x = bank.getFloat("x", i);
                double y = bank.getFloat("y", i);
                double z = bank.getFloat("z", i);
                double px = bank.getFloat("px", i);
                double py = bank.getFloat("py", i);
                double pz = bank.getFloat("pz", i);

                int q = -1; //need the charge sign from tracking

                Units units = Units.MM; //can be MM or CM. 

                double xb = 0;
                double yb = 0;

                //momenta must be in GeV for the helix class
                Helix helix = new Helix(x, y, z, px/1000., py/1000., pz/1000., q, b, xb, yb, units);

                //Intersection points with the middle of the bar or wedge
                projection.setBarIntersect(helix.getHelixPointAtR(Parameters.BAR_MIDDLE_RADIUS));
                projection.setWedgeIntersect(helix.getHelixPointAtR(Parameters.WEDGE_MIDDLE_RADIUS));

                //Path length to the middle of the bar or wedge
                projection.setBarPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_INNER_RADIUS)));
                projection.setWedgePathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_INNER_RADIUS)));
                
                //Path length from the inner radius to the middle radius
                projection.setBarInPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_MIDDLE_RADIUS)) - projection.getBarPathLength());
                projection.setWedgeInPathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_MIDDLE_RADIUS)) - projection.getWedgePathLength());
                projections.add(projection);
                fill_out_bank(outputBank, projection, i);
            }
            event.appendBank(outputBank);
        }
    }
    
    /**
     * Projects the MC particles onto the atof using a {@link Helix}
     * model.
     *
     * @param event the {@link DataEvent} containing track data to be projected.
     */
    public void projectMCTracks(DataEvent event) {//, CalibrationConstantsLoader ccdb) {

        projections.clear();
                
        String track_bank_name = "MC::Particle";
        if (event == null) { // check if there is an event
            //System.out.print(" no event \n");
        } else if (event.hasBank(track_bank_name) == false) {
            // check if there are ahdc tracks in the event
            //System.out.print("no tracks \n");
        } else {
            DataBank bank = event.getBank(track_bank_name);
            int nt = bank.rows(); // number of tracks 
            TrackProjection projection = new TrackProjection();
            DataBank outputBank = event.createBank("AHDC::Projections", nt);

            for (int i = 0; i < nt; i++) {

                double x = bank.getFloat("vx", i);
                double y = bank.getFloat("vy", i);
                double z = bank.getFloat("vz", i);
                double px = bank.getFloat("px", i);
                double py = bank.getFloat("py", i);
                double pz = bank.getFloat("pz", i);
                
                //Put everything in MM

                x = x*10;
                y = y*10;
                z = z*10;

		Units units = Units.MM;   
                
                int q = -1; //need the charge sign from tracking

                double xb = 0;
                double yb = 0;

                //momenta must be in GeV for the helix class
                Helix helix = new Helix(x, y, z, px, py, pz, q, b, xb, yb, units);

                //Intersection points with the middle of the bar or wedge
		projection.setBarIntersect(helix.getHelixPointAtR(Parameters.BAR_MIDDLE_RADIUS));
                projection.setWedgeIntersect(helix.getHelixPointAtR(Parameters.WEDGE_MIDDLE_RADIUS));

                //Path length to the middle of the bar or wedge

		projection.setBarPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_INNER_RADIUS)));
                projection.setWedgePathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_INNER_RADIUS)));
                
                //Path length from the inner radius to the middle radius

		projection.setBarInPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_MIDDLE_RADIUS)) - projection.getBarPathLength());
                projection.setWedgeInPathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_MIDDLE_RADIUS)) - projection.getWedgePathLength());
                projections.add(projection);
                fill_out_bank(outputBank, projection, i);
            }
            event.appendBank(outputBank);
        }
    }
    
    public static void fill_out_bank(DataBank outputBank, TrackProjection projection, int i) {
        outputBank.setFloat("x_at_bar", i, (float) projection.getBarIntersect().x());
        outputBank.setFloat("y_at_bar", i, (float) projection.getBarIntersect().y());
        outputBank.setFloat("z_at_bar", i, (float) projection.getBarIntersect().z());
        outputBank.setFloat("L_at_bar", i, (float) projection.getBarPathLength());
        outputBank.setFloat("L_in_bar", i, (float) projection.getBarInPathLength());
        outputBank.setFloat("x_at_wedge", i, (float) projection.getWedgeIntersect().x());
        outputBank.setFloat("y_at_wedge", i, (float) projection.getWedgeIntersect().y());
        outputBank.setFloat("z_at_wedge", i, (float) projection.getWedgeIntersect().z());
        outputBank.setFloat("L_at_wedge", i, (float) projection.getWedgePathLength());
        outputBank.setFloat("L_in_wedge", i, (float) projection.getWedgeInPathLength());
   
    }

    public static void main(String arg[]) {
        
    }
}
