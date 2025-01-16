package org.jlab.rec.atof.hit;

import java.util.List;
import org.jlab.geom.base.*;
import org.jlab.geom.detector.alert.ATOF.*;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.rec.atof.trackMatch.TrackProjection;
import org.jlab.rec.atof.trackMatch.TrackProjector;

/**
 *
 * Represents a hit in the atof. Stores info about the sector, layer, component,
 * order, TDC, ToT. Type is wedge/bar up/bar down Spatial coordinates are
 * computed from atof detector object using the geometry service Stores whether
 * the hit is part of a cluster. Calculates time, energy based on TDC/ToT.
 *
 * @author npilleux
 */
public class AtofHit {

    private int sector, layer, component, order;
    private int TDC, ToT;
    private double time, energy, x, y, z;
    private String type;
    private boolean is_in_a_cluster;
    private double path_length, inpath_length;

    public int getSector() {
        return sector;
    }

    public void setSector(int sector) {
        this.sector = sector;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getComponent() {
        return component;
    }

    public void setComponent(int component) {
        this.component = component;
    }

    public int getTDC() {
        return TDC;
    }

    public void setTDC(int tdc) {
        this.TDC = tdc;
    }

    public int getToT() {
        return ToT;
    }

    public void setToT(int tot) {
        this.ToT = tot;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public double getEnergy() {
        return energy;
    }

    public void setEnergy(double energy) {
        this.energy = energy;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean getIs_in_a_cluster() {
        return is_in_a_cluster;
    }

    public void setIs_in_a_cluster(boolean is_in_a_cluster) {
        this.is_in_a_cluster = is_in_a_cluster;
    }

    public double getPath_length() {
        return path_length;
    }

    public void setPath_length(double path_length) {
        this.path_length = path_length;
    }

    public double getInpath_length() {
        return inpath_length;
    }

    public void setInpath_length(double inpath_length) {
        this.inpath_length = inpath_length;
    }

    public int computeModule_index() {
        //Index ranging 0 to 60 for each wedge+bar module
        return 4 * this.sector + this.layer;
    }

    public final String makeType() {
        String itype = "undefined";
        if (this.component == 10 && this.order == 0) {
            itype = "bar down";
        } else if (this.component == 10 && this.order == 1) {
            itype = "bar up";
        } else if (this.component < 10) {
            itype = "wedge";
        }
        this.type = itype;
        return itype;
    }

    public final int TDC_to_time() {
        double some_conversion = 0;
        double veff = 1;
        if (null == this.type) {
            return 1;
        } else {
            switch (this.type) {
                case "wedge" -> {
                    some_conversion = 10;//read calib constants here
                    veff = 1;
                }
                case "bar up" -> {
                    some_conversion = 10;//read calib constants here
                    veff = 1;
                }
                case "bar down" -> {
                    some_conversion = 10;//read calib constants here
                    veff = 1;
                }
                default -> {
                    return 1;
                }
            }
        }
        //Time at the inner surface
        this.time = some_conversion * this.TDC - this.inpath_length / veff;
        return 0;
    }

    public final int ToT_to_energy() {
        double some_conversion = 0;
        double att_L = 1;
        if (null == this.type) {
            return 1;
        } else {
            switch (this.type) {
                case "wedge" -> {
                    some_conversion = 10;//read calib constants here
                    att_L = 10;
                }
                case "bar up" -> {
                    some_conversion = 10;//read calib constants here
                    att_L = 10;
                }
                case "bar down" -> {
                    some_conversion = 10;//read calib constants here
                    att_L = 10;
                }
                default -> {
                    return 1;
                }
            }
        }
        this.energy = some_conversion * this.ToT * Math.exp(this.inpath_length / att_L);
        return 0;
    }

    /**
     * Calculates spatial coordinates for the hit based on associated detector
     * component. Retrieves the midpoint of the atof component to assign the
     * corresponding x, y, z coordinates to the hit.
     *
     *
     * @param atof The Detector object representing the atof.
     * @return 0 if the coordinates were successfully set, or 1 if the hit type
     * is undefined or unsupported.
     */
    public final int slc_to_xyz(Detector atof) {
        int sl = 999;
        if (null == this.type) {
            return 1;
        } else {
            switch (this.type) {
                case "wedge" ->
                    sl = 1;
                case "bar up", "bar down" ->
                    sl = 0;
                default -> {
                    return 1;
                }
            }
        }
        Component comp = atof.getSector(this.sector).getSuperlayer(sl).getLayer(this.layer).getComponent(this.component);
        Point3D midpoint = comp.getMidpoint();
        this.x = midpoint.x();
        this.y = midpoint.y();
        this.z = midpoint.z();
        return 0;
    }

    /**
     * Compares two AtofHit objects to check if they match in the bar.
     * <ul>
     * <li>If the sector or layer of the two hits do not match, the method
     * returns {@code false}.</li>
     * <li>If either hit is not in the bar (component must be 10), the method
     * returns {@code false}.</li>
     * <li>If both hits are in the same SiPM (i.e., their order is the same),
     * the method returns {@code false}.</li>
     * </ul>
     * If none of these conditions are violated, the method returns
     * {@code true}, indicating the two hits match.
     *
     * @param hit2match The AtofHit object to compare with the current instance.
     * @return {@code true} if the hits match; {@code false} otherwise.
     */
    public boolean matchBar(AtofHit hit2match) {
        if (this.getSector() != hit2match.getSector()) {
            return false; //System.out.print("Two hits in different sectors \n");
        } else if (this.getLayer() != hit2match.getLayer()) {
            return false; //System.out.print("Two hits in different layers \n");
        } else if (this.getComponent() != 10 || hit2match.getComponent() != 10) {
            return false; //System.out.print("At least one hit is not in the bar \n");
        } else {
            return this.getOrder() != hit2match.getOrder(); //System.out.print("Two hits in same SiPM \n");
        }
    }

    /**
     * Returns the azimuthal angle (phi) of the hit.
     *
     * @return The azimuthal angle (phi) in radians, in the range [-π, π].
     */
    public double getPhi() {
        return Math.atan2(this.y, this.x);
    }

    /**
     * Constructor for a fit in the atof. Initializes the hit's sector, layer,
     * component, order, TDC, ToT. Sets the hit's initial state regarding
     * clustering. Set up the hit's type, time, energy, and spatial coordinates.
     *
     * @param sector The sector of the detector where the hit occurred.
     * @param layer The layer of the detector where the hit was detected.
     * @param component The component within the layer that registered the hit.
     * @param order Order of the hit.
     * @param tdc TDC value.
     * @param tot ToT value.
     * @param atof Detector object representing the atof, used to calculate
     * spatial coordinates.
     */
    public AtofHit(int sector, int layer, int component, int order, int tdc, int tot, Detector atof) {
        this.sector = sector;
        this.layer = layer;
        this.component = component;
        this.order = order;
        this.TDC = tdc;
        this.ToT = tot;
        this.is_in_a_cluster = false;

        this.makeType();
        int is_ok = this.TDC_to_time();
        if (is_ok != 1) {
            is_ok = this.ToT_to_energy();
        }
        if (is_ok != 1) {
            is_ok = this.slc_to_xyz(atof);
        }
    }

    /**
     * Constructor for a fit in the atof. Initializes the hit's sector, layer,
     * component, order, TDC, ToT. Sets the hit's initial state regarding
     * clustering. Set up the hit's type, time, energy, and spatial coordinates.
     *
     * @param sector The sector of the detector where the hit occurred.
     * @param layer The layer of the detector where the hit was detected.
     * @param component The component within the layer that registered the hit.
     * @param order Order of the hit.
     * @param tdc TDC value.
     * @param tot ToT value.
     * @param atof Detector object representing the atof, used to calculate
     * @param track_projector TrackProjector object with ahdc tracks to be
     * matched to the hit.
     */
    public AtofHit(int sector, int layer, int component, int order, int tdc, int tot, Detector atof, TrackProjector track_projector) {
        this.sector = sector;
        this.layer = layer;
        this.component = component;
        this.order = order;
        this.TDC = tdc;
        this.ToT = tot;
        this.is_in_a_cluster = false;

        //First the type needs to be set
        this.makeType();
        //From it the coordinates can be computed
        this.slc_to_xyz(atof);
        //From them tracks can be matched
        this.matchTrack(track_projector);
        //And energy and time can then be computed
        this.ToT_to_energy();
        this.TDC_to_time();
    }

    /**
     * Matches the current track with ahdc tracks projections. Calculates the
     * match by comparing the hit's azimuthal angle and longitudinal position
     * (z) with the track projection. If a match is found within defined
     * tolerances for phi and z, the path length of the matched hit is updated.
     *
     * @param track_projector The TrackProjector object that provides a list of
     * TrackProjections.
     *
     */
    public final void matchTrack(TrackProjector track_projector) {
        double sigma_phi = 10;
        double sigma_z = 10;
        List<TrackProjection> Projections = track_projector.getProjections();
        for (int i_track = 0; i_track < Projections.size(); i_track++) {
            Point3D projection_point = new Point3D();
            if (null == this.getType()) {
                System.out.print("Impossible to match track and hitm hit type is undefined \n");
            } else {
                switch (this.getType()) {
                    case "wedge" ->
                        projection_point = Projections.get(i_track).get_WedgeIntersect();
                    case "bar up", "bar down" ->
                        projection_point = Projections.get(i_track).get_BarIntersect();
                    default ->
                        System.out.print("Impossible to match track and hitm hit type is undefined \n");
                }
            }

            if (Math.abs(this.getPhi() - projection_point.toVector3D().phi()) < sigma_phi) {
                if (Math.abs(this.getZ() - projection_point.z()) < sigma_z) {
                    if ("wedge".equals(this.getType())) {
                        this.setPath_length(Projections.get(i_track).get_WedgePathLength());
                    } else {
                        this.setPath_length(Projections.get(i_track).get_BarPathLength());
                    }
                }
            }
        }
    }

    public AtofHit() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        Detector atof = factory.createDetectorCLAS(cp);

        //Input to be read
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/reconstruction/alert/src/main/java/org/jlab/rec/atof/Hit/test_tdc_atof.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);

        int event_number = 0;
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            event_number++;
            DataBank bank = event.getBank("ATOF::tdc");
            int nt = bank.rows(); // number of tracks 

            for (int i = 0; i < nt; i++) {
                int sector = bank.getInt("sector", i);
                int layer = bank.getInt("layer", i);
                int component = bank.getInt("component", i);
                int order = bank.getInt("order", i);
                int tdc = bank.getInt("TDC", i);
                int tot = bank.getInt("ToT", i);
                AtofHit hit = new AtofHit(sector, layer, component, order, tdc, tot, atof);
                System.out.print(hit.getX() + "\n");
            }
        }
    }
}
