package org.jlab.rec.recoiltof.hit;

import org.jlab.geom.base.*;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.recoiltof.constants.Parameters;

/**
 *
 * Represents a hit in the recoil tof. Stores info about the sector, row, column,
 * order, TDC, ToT. Type is bar up/bar down/ bar. Stores whether
 * the hit is part of a cluster. Calculates time, energy based on TDC/ToT.
 *
 * @author npilleux, Nilanga Wickramaarachchi 
 */
public class RECOILTOFHit {

    private int sector, row, column, order;
    private int tdc, tot;
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

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
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
     * Assigns a type to the hit.
     *
     */
    public final String makeType() {
        //Type of hit can be bar up, bar down or bar.
        //Avoids testing components and order every time.
        String itype = "undefined";
        if (this.order == 0) {
            itype = "bar down";
        } else if (this.order == 1) {
            itype = "bar up";
        } 
        this.type = itype;
        return itype;
    }

    /**
     * Converts TDC to time (ns). Sets the hit time parameter to a raw time for
     * up/down bar hits.
     *
     * @return 0 if the time was successfully set, or 1 if the hit type is
     * unsupported.
     */
    public final int convertTdcToTime() {
        double tdc2time, veff, distance_to_sipm;
        if (null == this.type) {
            System.out.print("Null hit type, cannot convert tdc to time.");
            return 1;
        } else {
            switch (this.type) {
	        case "bar up" -> {
                    tdc2time = Parameters.TDC2TIME;
                    veff = Parameters.VEFF;
                    //The distance will be computed at barhit level when y information is available
                    distance_to_sipm = 0;
                }
                case "bar down" -> {
                    tdc2time = Parameters.TDC2TIME;
                    veff = Parameters.VEFF;
                    //The distance will be computed at barhit level when y information is available
                    distance_to_sipm = 0;
                }
                case "bar" -> {
                    System.out.print("Bar hit type, cannot convert tdc to time.");
                    return 1;
                }
                default -> {
                    System.out.print("Undefined hit type, cannot convert tdc to time.");
                    return 1;
                }
            }
        }
        //Hit time. Will need implementation of offsets.
        this.time = tdc2time * this.tdc - distance_to_sipm / veff;
        return 0;
    }

    /**
     * Converts ToT to energy (MeV). Sets the hit energy parameter to a raw
     * energy for up/down bar hits.
     *
     * @return 0 if the energy was successfully set, or 1 if the hit type is
     * unsupported.
     */
    public final int convertTotToEnergy() {
        double tot2energy;
        if (null == this.type) {
            System.out.print("Null hit type, cannot convert tot to energy.");
            return 1;
        } else {
            switch (this.type) {
	        case "bar up" -> {
                    tot2energy = Parameters.TOT2ENERGY;
                    //only half the information in the bar, 
                    //the attenuation will be computed when the full hit is formed
                    this.energy = tot2energy * this.tot;
                }
                case "bar down" -> {
                    tot2energy = Parameters.TOT2ENERGY;
                    //only half the information in the bar, 
                    //the attenuation will be computed when the full hit is formed
                    this.energy = tot2energy * this.tot;
                }
                case "bar" -> {
                    System.out.print("Bar hit type, cannot convert tot to energy.");
                    return 1;
                }
                default -> {
                    System.out.print("Undefined hit type, cannot convert tot to energy.");
                    return 1;
                }
            }
        }
        return 0;
    }

    /**
     * Calculates spatial coordinates (mm) for the hit based on row and column number of the bar within a sector.
     * The row and column variables are obtained from the bank information.
     *
     * @return 0 if the coordinates were successfully set, or 1 if the hit type
     * is undefined or unsupported.
     */
    public final int calculateXYZ() {

	// Constants for positioning
	int nRows = Parameters.NROWS;
	double y_start = -(Parameters.LENGTH - Parameters.LONG_BAR_LENGTH)/2;  // Starting Y position
	double x_spacing = Parameters.BAR_WIDTH;
	double x_start = -(Parameters.WIDTH - x_spacing)/2;  // starting X position
	double dy_long = Parameters.LONG_BAR_LENGTH;
	double dy_short = Parameters.SHORT_BAR_LENGTH;

	//Position calculation
	double z_pos = 0;
	double x_pos = x_start + ((this.column-1) * x_spacing);
	    
        double y_pos;
	if(this.row-1 < (nRows - 1) / 2)
	    {
		y_pos = y_start + ((this.row-1) * dy_long);
	    }
	else if (this.row-1 == (nRows - 1) / 2) // middle row
	    {  
		y_pos = 0;
	    }
	else
	    {
		y_pos = y_start + (this.row -2) * dy_long + dy_short;
	    }
	    	
	this.x = x_pos;
        this.y = y_pos;
        this.z = z_pos;
        return 0;
    }

    /**
     * Compares two RECOILTOFHit objects to check if they match in the bar.
     * <ul>
     * <li>If the sector or row or column of the two hits do not match, the method
     * returns {@code false}.</li>
     * <li>If both hits are in the same SiPM (i.e., their order is the same), or
     * have incorrect order, the method returns {@code false}.</li>
     * </ul>
     * If none of these conditions are violated, the method returns
     * {@code true}, indicating the two hits match.
     *
     * @param hit2match The RECOILTOFHit object to compare with the current instance.
     * @return {@code true} if the hits match; {@code false} otherwise.
     */
    public boolean matchBar(RECOILTOFHit hit2match) {
        if (this.getSector() != hit2match.getSector()) {
            //Two hits in different sectors
            return false;
        } else if (this.getRow() != hit2match.getRow()) {
            //Two hits in different rows
            return false;
        } else if (this.getColumn() != hit2match.getColumn()) {
            //Two hits in different columns
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
     * Constructor for a hit in the recoil tof. Initializes the hit's sector, row,
     * column, order, TDC, ToT. Sets the hit's initial state regarding
     * clustering. Set up the hit's type, time, energy, and spatial coordinates.
     *
     * @param sector The sector of the detector where the hit occurred.
     * @param row The row of the detector where the hit was detected.
     * @param column The column within the row that registered the hit.
     * @param order Order of the hit.
     * @param tdc TDC value.
     * @param tot ToT value.
     *
     */
    public RECOILTOFHit(int sector, int row, int column, int order, int tdc, int tot) {
        this.sector = sector;
        this.row = row;
        this.column = column;
        this.order = order;
        this.tdc = tdc;
        this.tot = tot;
        this.isInACluster = false;

        this.makeType();
        this.convertTdcToTime();
        this.convertTotToEnergy();
        this.calculateXYZ();
    }

    public RECOILTOFHit() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}
