package org.jlab.rec.atof.hit;

import org.jlab.geom.base.*;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.atof.constants.Parameters;
import java.util.logging.Logger;
import org.jlab.rec.alert.constants.CalibrationConstantsLoader;

/**
 *
 * Represents a hit in the atof. Stores info about the sector, layer, component,
 * order, TDC, ToT. Type is wedge/bar up/bar down/ bar Spatial coordinates are
 * computed from atof detector object using the geometry service Stores whether
 * the hit is part of a cluster. Calculates time, energy based on TDC/ToT.
 *
 * @author npilleux
 */
public class ATOFHit {

    static final Logger LOGGER = Logger.getLogger(ATOFHit.class.getName());
    
    private int sector, layer, component, order;
    private int tdc, tot;
    private float startTime;
    private double time, energy, x, y, z;
    private String type;
    private boolean isInACluster;
    private int associatedClusterIndex;
    int idTDC;

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

    public int getTdc() {
        return tdc;
    }

    public void setTdc(int tdc) {
        this.tdc = tdc;
    }

    public int getTot() {
        return tot;
    }

    public void setTot(int tot) {
        this.tot = tot;
    }

    public double getTime() {
        return time;
    }
    
    public double getStartTime() {
        return this.startTime;
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

    public boolean getIsInACluster() {
        return isInACluster;
    }

    public void setIsInACluster(boolean is_in_a_cluster) {
        this.isInACluster = is_in_a_cluster;
    }

    public int getAssociatedClusterIndex() {
        return associatedClusterIndex;
    }

    public void setAssociatedClusterIndex(int index) {
        this.associatedClusterIndex = index;
    }

    public int getIdTDC() {
        return idTDC;
    }

    public void setIdTDC(int index) {
        this.idTDC = index;
    }

    /**
     * Computes the module index for the hit.
     *
     */
    public int computeModuleIndex() {
        //Index ranging 0 to 60 for each wedge+bar module
        return 4 * this.sector + this.layer;
    }

    /**
     * Assigns a type to the hit.
     *
     */
    public final String makeType() {
        //Type of hit can be wedge, bar up, bar down or bar.
        //Avoids testing components and order every time.
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

    /**
     * Converts TDC to time (ns). Sets the hit time parameter to a raw time for
     * up/down bar hits or to the time corrected for the propagation for wedge
     * hits.
     *
     * @return 0 if the time was successfully set, or 1 if the hit type is
     * unsupported.
     */
    public final int convertTdcToTime() {
        
        //Converting tdc to ns, event start time correction
        this.time = Parameters.TDC2TIME*this.tdc - this.startTime;

        //TODO: When table structure evolves, pay attention to order.
        //Key for the current channel 
        int key = this.sector*10000 + this.layer*1000 + this.component*10 + 0;//this.order;
        //Key for the reference channel over which all the others sharing the same z are aligned 
        int referenceModuleKey = this.component*10; 
    
        //Time offsets
        double[] timeOffsets = CalibrationConstantsLoader.ATOF_TIME_OFFSETS.get(key);
        double[] timeOffsetsRef = CalibrationConstantsLoader.ATOF_TIME_OFFSETS.get(referenceModuleKey);
        
        //The names below correspond to the CCDB entries
        //They will most probably evolve
        //For now let's say t0 is used to store the bar-to-bar and wedge-to-wedge alignments
        double t0 = timeOffsets[0];
        double tChannelToChannelPhiAlignment = (t0 - timeOffsetsRef[0]);
        if(this.type=="bar up" || this.type=="bar down") //bar alignment is done with the sum of the two times
            tChannelToChannelPhiAlignment=tChannelToChannelPhiAlignment/2.;
        
        //tud is used to store the bar up - bar down alignment
        double tud = timeOffsets[1];
        //The rest of the constants are not used for now
        /*double twb = timeOffsets[2];
        double xtra1 = timeOffsets[3];
        double xtra2 = timeOffsets[4];*/
        
        //TW corrections TO BE IMPLEMENTED
        /*
        double[] timeWalks = CalibrationConstantsLoader.ATOF_TIME_WALK.get(key);
	double tw0 = timeWalks[0];
        double tw1 = timeWalks[1];
        double tw2 = timeWalks[2];
        double tw3 = timeWalks[3];
        double dtw0 = timeWalks[4];
        double dtw1 = timeWalks[5];
        double dtw2 = timeWalks[6];
        double dtw3 = timeWalks[7];
        double chi2ndf = timeWalks[8];*/
        
        //Veff corrections TO BE IMPLEMENTED
        
        double veff, distance_to_sipm, timeOffset;
        if (null == this.type) {
            LOGGER.finest("Null hit type, cannot convert tdc to time.");
            return 1;
        } else {
            switch (this.type) {
                case "wedge" -> {
                    veff = Parameters.VEFF;
                    //Wedge hits are placed at the center of wedges and sipm at their top
                    distance_to_sipm = Parameters.WEDGE_THICKNESS / 2.;
                    timeOffset = - tChannelToChannelPhiAlignment;
                }
                case "bar up" -> {
                    veff = Parameters.VEFF;
                    //The distance will be computed at barhit level when z information is available
                    distance_to_sipm = 0;
                    timeOffset = - tud/2. - tChannelToChannelPhiAlignment;
                }
                case "bar down" -> {
                    veff = Parameters.VEFF;
                    //The distance will be computed at barhit level when z information is available
                    distance_to_sipm = 0;
                    timeOffset = + tud/2. - tChannelToChannelPhiAlignment;
                }
                case "bar" -> {
                    LOGGER.finest("Bar hit type, cannot convert tdc to time.");
                    return 1;
                }
                default -> {
                    LOGGER.finest("Undefined hit type, cannot convert tdc to time.");
                    return 1;
                }
            }
        }
        //Hit time.
        this.time = this.time - distance_to_sipm / veff + timeOffset;
        return 0;
    }

    /**
     * Converts ToT to energy (MeV). Sets the hit energy parameter to a raw
     * energy for up/down bar hits or to the energy corrected for the
     * attenuation for wedge hits.
     *
     * @return 0 if the energy was successfully set, or 1 if the hit type is
     * unsupported.
     */
    public final int convertTotToEnergy() {
        double tot2energy;
        if (null == this.type) {
            LOGGER.finest("Null hit type, cannot convert tot to energy.");
            return 1;
        } else {
            switch (this.type) {
                case "wedge" -> {
                    tot2energy = Parameters.TOT2ENERGY_WEDGE;
                    //For now hits are considered in the middle of the wedge
                    //And the SiPM on top 
                    double distance_hit_to_sipm = Parameters.WEDGE_THICKNESS / 2.;
                    this.energy = tot2energy * this.tot * Math.exp(distance_hit_to_sipm / Parameters.ATT_L);
                }
                case "bar up" -> {
                    tot2energy = Parameters.TOT2ENERGY_BAR;
                    //only half the information in the bar, 
                    //the attenuation will be computed when the full hit is formed
                    this.energy = tot2energy * this.tot;
                }
                case "bar down" -> {
                    tot2energy = Parameters.TOT2ENERGY_BAR;
                    //only half the information in the bar, 
                    //the attenuation will be computed when the full hit is formed
                    this.energy = tot2energy * this.tot;
                }
                case "bar" -> {
                    LOGGER.finest("Bar hit type, cannot convert tot to energy.");
                    return 1;
                }
                default -> {
                    LOGGER.finest("Undefined hit type, cannot convert tot to energy.");
                    return 1;
                }
            }
        }
        return 0;
    }

    /**
     * Calculates spatial coordinates for the hit based on associated detector
     * component. Retrieves the midpoint of the atof component to assign the
     * corresponding x, y, z coordinates to the hit (mm).
     *
     *
     * @param atof The Detector object representing the atof.
     * @return 0 if the coordinates were successfully set, or 1 if the hit type
     * is undefined or unsupported.
     */
    public final int convertSLCToXYZ(Detector atof) {
        int sl;
        if (null == this.type) {
            return 1;
        } else {
            switch (this.type) {
                case "wedge" ->
                    sl = 1;
                case "bar up", "bar down", "bar" ->
                    sl = 0;
                default -> {
                    return 1;
                }
            }
        }
        Component comp = atof.getSector(this.sector).getSuperlayer(sl).getLayer(this.layer).getComponent(this.component);
        Point3D midpoint = comp.getMidpoint();
        //Midpoints defined in the system were z=0 is the upstream end of the atof
        //Units are mm
        this.x = midpoint.x();
        this.y = midpoint.y();
        this.z = midpoint.z();
	return 0;
    }

    /**
     * Compares two ATOFHit objects to check if they match in the bar.
     * <ul>
     * <li>If the sector or layer of the two hits do not match, the method
     * returns {@code false}.</li>
     * <li>If either hit is not in the bar (component must be 10), the method
     * returns {@code false}.</li>
     * <li>If both hits are in the same SiPM (i.e., their order is the same), or
     * have incorrect order, the method returns {@code false}.</li>
     * </ul>
     * If none of these conditions are violated, the method returns
     * {@code true}, indicating the two hits match.
     *
     * @param hit2match The ATOFHit object to compare with the current instance.
     * @return {@code true} if the hits match; {@code false} otherwise.
     */
    public boolean matchBar(ATOFHit hit2match) {
        if (this.getSector() != hit2match.getSector()) {
            //Two hits in different sectors
            return false;
        } else if (this.getLayer() != hit2match.getLayer()) {
            //Two hits in different layers
            return false;
        } else if (this.getComponent() != 10 || hit2match.getComponent() != 10) {
            //At least one hit not in the bar
            return false;
        } else if (this.getOrder() > 1 || hit2match.getOrder() > 1) {
            //At least one hit has incorrect order
            return false;
        } else {
            //Match if one is order 0 and the other is order 1
            return this.getOrder() != hit2match.getOrder();
        }
    }

    /**
     * Computes the azimuthal angle (phi) of the hit in rad.
     *
     * @return The azimuthal angle (phi) in radians, in the range [-π, π].
     */
    public double getPhi() {
        return Math.atan2(this.y, this.x);
    }

    /**
     * Constructor for a hit in the atof. Initializes the hit's sector, layer,
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
    public ATOFHit(int sector, int layer, int component, int order, int tdc, int tot, float startTime, Detector atof) {
        this.sector = sector;
        this.layer = layer;
        this.component = component;
        this.order = order;
        this.tdc = tdc;
        this.tot = tot;
        this.startTime = startTime;
        this.isInACluster = false;

        this.makeType();
        this.convertTdcToTime();
        this.convertTotToEnergy();
        this.convertSLCToXYZ(atof);
    }

    public ATOFHit() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}
