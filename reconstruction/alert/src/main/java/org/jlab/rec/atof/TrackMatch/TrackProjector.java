package org.jlab.rec.atof.trackMatch;

import java.util.ArrayList;
import java.util.List;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.clas.tracking.trackrep.Helix;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.clas.swimtools.Swim;
import org.jlab.utils.CLASResources;
import cnuphys.magfield.MagneticFields;
import java.io.BufferedWriter;
import java.io.IOException;
import org.jlab.geom.prim.Point3D;
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
    private List<TrackProjection> Projections;
    
    /**
     * solenoid magnitude
     */
    private Double B;

    /**
     * Default constructor that initializes the list of projections as new empty
     * list and the magnetic field to 5T.
     */
    public TrackProjector() {
        Projections = new ArrayList<TrackProjection>();
        B = 5.0;
    }

    /**
     * Gets the list of track projections.
     *
     * @return a {@link List} of {@link TrackProjection} objects representing
     * the projections.
     */
    public List<TrackProjection> getProjections() {
        return Projections;
    }
    
     /**
     * Gets the solenoid magnitude
     *
     * @return solenoid magnitude
     */
    public Double getB() {
        return B;
    }

    /**
     * Sets the list of track projections.
     *
     * @param Projections a {@link List} of {@link TrackProjection}.
     */
    public void setProjections(List<TrackProjection> Projections) {
        this.Projections = Projections;
    }
    
     /**
     * Sets the solenoid magnitude.
     *
     * @param B a double.
     */
    public void setB(Double B) {
        this.B = B;
    }

    /**
     * Projects the ahdc tracks in the event onto the atof using a {@link Helix}
     * model.
     *
     * @param event the {@link DataEvent} containing track data to be projected.
     */
    public void ProjectTracks(DataEvent event) {//, CalibrationConstantsLoader ccdb) {

        Projections.clear();
        
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
                Helix helix = new Helix(x, y, z, px/1000., py/1000., pz/1000., q, B, xb, yb, units);

                //Intersection points with the middle of the bar or wedge
                projection.set_BarIntersect(helix.getHelixPointAtR(Parameters.BAR_MIDDLE_RADIUS));
                projection.set_WedgeIntersect(helix.getHelixPointAtR(Parameters.WEDGE_MIDDLE_RADIUS));

                //Path length to the middle of the bar or wedge
                projection.set_BarPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_INNER_RADIUS)));
                projection.set_WedgePathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_INNER_RADIUS)));
                
                //Path length from the inner radius to the middle radius
                projection.set_BarInPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_MIDDLE_RADIUS)) - projection.get_BarPathLength());
                projection.set_WedgeInPathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_MIDDLE_RADIUS)) - projection.get_WedgePathLength());
                Projections.add(projection);
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

        Projections.clear();
                
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
                Helix helix = new Helix(x, y, z, px, py, pz, q, B, xb, yb, units);

                //Intersection points with the middle of the bar or wedge
		projection.set_BarIntersect(helix.getHelixPointAtR(Parameters.BAR_MIDDLE_RADIUS));
                projection.set_WedgeIntersect(helix.getHelixPointAtR(Parameters.WEDGE_MIDDLE_RADIUS));

                //Path length to the middle of the bar or wedge

		projection.set_BarPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_INNER_RADIUS)));
                projection.set_WedgePathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_INNER_RADIUS)));
                
                //Path length from the inner radius to the middle radius

		projection.set_BarInPathLength((float) Math.abs(helix.getLAtR(Parameters.BAR_MIDDLE_RADIUS)) - projection.get_BarPathLength());
                projection.set_WedgeInPathLength((float) Math.abs(helix.getLAtR(Parameters.WEDGE_MIDDLE_RADIUS)) - projection.get_WedgePathLength());
                Projections.add(projection);
                fill_out_bank(outputBank, projection, i);
            }
            event.appendBank(outputBank);
        }
    }
    
    public void projectMCTracks_full_path(DataEvent event, int num_event, BufferedWriter writer) throws IOException { 
                
        String track_bank_name = "MC::Particle";
        if (event == null) { // check if there is an event
            //System.out.print(" no event \n");
        } else if (event.hasBank(track_bank_name) == false) {
            // check if there are ahdc tracks in the event
            //System.out.print("no tracks \n");
        } else {
            DataBank bank = event.getBank(track_bank_name);
            int nt = bank.rows(); // number of tracks 

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
                Helix helix = new Helix(x, y, z, px, py, pz, q, B, xb, yb, units);
                
                double min = 0.1;
                double max = Parameters.WEDGE_INNER_RADIUS+Parameters.WEDGE_THICKNESS;
                int nsteps = 20;
                double step = (max-min)/nsteps;
                for(double i_radius = min; i_radius<max; i_radius+=step){
                    Point3D p = helix.getHelixPointAtR(i_radius);
                    writer.write(String.format("%d,%f,%f,%f%n", num_event, p.x(), p.y(), p.z()));
                }
            }
        }
    }

    public static void fill_out_bank(DataBank outputBank, TrackProjection projection, int i) {
        outputBank.setFloat("x_at_bar", i, (float) projection.get_BarIntersect().x());
        outputBank.setFloat("y_at_bar", i, (float) projection.get_BarIntersect().y());
        outputBank.setFloat("z_at_bar", i, (float) projection.get_BarIntersect().z());
        outputBank.setFloat("L_at_bar", i, (float) projection.get_BarPathLength());
        outputBank.setFloat("L_in_bar", i, (float) projection.get_BarInPathLength());
        outputBank.setFloat("x_at_wedge", i, (float) projection.get_WedgeIntersect().x());
        outputBank.setFloat("y_at_wedge", i, (float) projection.get_WedgeIntersect().y());
        outputBank.setFloat("z_at_wedge", i, (float) projection.get_WedgeIntersect().z());
        outputBank.setFloat("L_at_wedge", i, (float) projection.get_WedgePathLength());
        outputBank.setFloat("L_in_wedge", i, (float) projection.get_WedgeInPathLength());
   
    }

    public static void main(String arg[]) {

        //READING MAG FIELD MAP
        System.setProperty("CLAS12DIR", "../../");

        String mapDir = CLASResources.getResourcePath("etc") + "/data/magfield";
        try {
            MagneticFields.getInstance().initializeMagneticFields(mapDir,
                    "Symm_torus_r2501_phi16_z251_24Apr2018.dat", "Symm_solenoid_r601_phi1_z1201_13June2018.dat");
        } catch (Exception e) {
            e.printStackTrace();
        }

        float[] b = new float[3];
        Swim swimmer = new Swim();
        swimmer.BfieldLab(0, 0, 0, b);
        double B = Math.abs(b[2]);

        //Track Projector Initialisation with B field
        TrackProjector projector = new TrackProjector();
        projector.setB(B);
        
        //Input to be read
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/reconstruction/alert/src/main/java/org/jlab/rec/atof/Hit/update_protons_to_test_with_tracks.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);
        int event_number = 0;
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            event_number++;
            projector.ProjectTracks(event);
        }
        System.out.print("Read " + event_number + " events");
    }
}
