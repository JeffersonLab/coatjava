package org.jlab.rec.alert.projections;

import java.util.ArrayList;
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
    private ArrayList<TrackProjection> projections;

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
    public ArrayList<TrackProjection> getProjections() {
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
    public void setProjections(ArrayList<TrackProjection> Projections) {
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
        String track_bank_name = "AHDC::track";

        // check if there is an event
        if (event == null)  return;
        //System.out.print(" no event \n");
        
        // check if there are ahdc tracks in the event
        if (event.hasBank(track_bank_name) == false) return;
        //System.out.print("no tracks \n");
        
        DataBank bank = event.getBank(track_bank_name);
        int nt = bank.rows(); // number of tracks 
        TrackProjection projection = new TrackProjection();
        for (int i = 0; i < nt; i++) {
            double x = bank.getFloat("x", i);
            double y = bank.getFloat("y", i);
            double z = bank.getFloat("z", i);
            double px = bank.getFloat("px", i);
            double py = bank.getFloat("py", i);
            double pz = bank.getFloat("pz", i);
            int id = nt + 1;//To be replaced by track id if it is added to the out bank

            int q = -1; //need the charge sign from tracking

            Units units = Units.MM; //can be MM or CM. 

            double xb = 0;
            double yb = 0;

            //momenta must be in GeV for the helix class
            Helix helix = new Helix(x, y, z, px / 1000., py / 1000., pz / 1000., q, b, xb, yb, units);

            //Intersection points with the middle of the bar or wedge
            projection.setBarIntersect(helix.getHelixPointAtR(Parameters.BAR_MIDDLE_RADIUS));
            projection.setWedgeIntersect(helix.getHelixPointAtR(Parameters.WEDGE_MIDDLE_RADIUS));

            double rVertex = Math.sqrt(x * x + y * y);

            //Path length to the middle of the bar or wedge
            projection.setBarPathLength((float) helix.getPathLength(rVertex, Parameters.BAR_MIDDLE_RADIUS));
            projection.setWedgePathLength((float) helix.getPathLength(rVertex, Parameters.WEDGE_MIDDLE_RADIUS));
            //Path length from the inner radius to the middle radius
            projection.setBarInPathLength((float) helix.getPathLength(Parameters.BAR_INNER_RADIUS, Parameters.BAR_MIDDLE_RADIUS));
            projection.setWedgeInPathLength((float) helix.getPathLength(Parameters.WEDGE_INNER_RADIUS, Parameters.WEDGE_MIDDLE_RADIUS));
            projection.setTrackID(id);
            projections.add(projection);

        }
    }

    /**
     * Projects the MC particles onto the atof using a {@link Helix} model.
     *
     * @param event the {@link DataEvent} containing track data to be projected.
     */
    public void projectMCTracks(DataEvent event) {//, CalibrationConstantsLoader ccdb) {

        projections.clear();

        String track_bank_name = "MC::Particle";
        if (event == null) return;
        //System.out.print(" no event \n");

        // check if there are ahdc tracks in the event
        if (event.hasBank(track_bank_name) == false)  return;
        //System.out.print("no tracks \n");
        
        DataBank bank = event.getBank(track_bank_name);
        int nt = bank.rows(); // number of tracks 
        TrackProjection projection = new TrackProjection();

        for (int i = 0; i < nt; i++) {

            double x = bank.getFloat("vx", i);
            double y = bank.getFloat("vy", i);
            double z = bank.getFloat("vz", i);
            double px = bank.getFloat("px", i);
            double py = bank.getFloat("py", i);
            double pz = bank.getFloat("pz", i);
            int id = bank.getShort("id", i);
            //Put everything in MM
            x = x * 10;
            y = y * 10;
            z = z * 10;

            Units units = Units.MM;

            int q = -1; //need the charge sign from tracking

            double xb = 0;
            double yb = 0;

            //momenta must be in GeV for the helix class
            Helix helix = new Helix(x, y, z, px, py, pz, q, b, xb, yb, units);

            //Intersection points with the middle of the bar or wedge
            projection.setBarIntersect(helix.getHelixPointAtR(Parameters.BAR_MIDDLE_RADIUS));
            projection.setWedgeIntersect(helix.getHelixPointAtR(Parameters.WEDGE_MIDDLE_RADIUS));

            double rVertex = Math.sqrt(x * x + y * y);

            //Path length to the middle of the bar or wedge
            projection.setBarPathLength((float) helix.getPathLength(rVertex, Parameters.BAR_MIDDLE_RADIUS));
            projection.setWedgePathLength((float) helix.getPathLength(rVertex, Parameters.WEDGE_MIDDLE_RADIUS));
            //Path length from the inner radius to the middle radius
            projection.setBarInPathLength((float) helix.getPathLength(Parameters.BAR_INNER_RADIUS, Parameters.BAR_MIDDLE_RADIUS));
            projection.setWedgeInPathLength((float) helix.getPathLength(Parameters.WEDGE_INNER_RADIUS, Parameters.WEDGE_MIDDLE_RADIUS));

            projection.setTrackID(id);
            projections.add(projection);
        }
    }

    public static void main(String arg[]) {

    }
}
