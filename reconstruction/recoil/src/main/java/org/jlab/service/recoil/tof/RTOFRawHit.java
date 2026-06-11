package org.jlab.service.recoil.tof;

import org.jlab.detector.geant4.v2.recoil.tof.RTOFConstants;

/**
 *
 * Represents a hit in the recoil tof. Stores info about the sector, row, column,
 * order, TDC, ToT. Type is bar up/bar down/ bar. Stores whether
 * the hit is part of a cluster. Calculates time, energy based on TDC/ToT.
 *
 * @author npilleux, Nilanga Wickramaarachchi 
 */
public class RTOFRawHit {

    private int sector, row, column, order;
    private int tdc, tot;
    private double time, energy, x, y, z, local_y;
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

    public double getLocalY() {
        return local_y;
    }
    
    public void setLocalY(double local_y) {
	this.local_y = local_y;
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
     * Calculates spatial coordinates (cm) for the hit based on row and column number of the bar within a sector.
     * The row and column variables are obtained from the bank information.
     *
     * @return 0 if the coordinates were successfully set, or 1 if the hit type
     * is undefined or unsupported.
     */
    public final int calculateXYZ() {

	// Constants for positioning
	int nRows = RTOFConstants.NROWS;
	double y_start = -(RTOFConstants.LENGTH - RTOFConstants.LONG_BAR_LENGTH)/2;  // Starting Y position
	double x_spacing = RTOFConstants.BAR_WIDTH;
	double x_start = -(RTOFConstants.WIDTH - x_spacing)/2;  // starting X position
	double dy_long = RTOFConstants.LONG_BAR_LENGTH;
	double dy_short = RTOFConstants.SHORT_BAR_LENGTH;

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

	double[] localCoords = {x_pos, y_pos, z_pos};
	    	
	// Calculate center coordinates for the sector
	double sector_x = (-1+(this.sector-1)*2)*(RTOFConstants.RADIUS)*Math.sin(Math.toRadians(RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE));
	double sector_y = 0;
	double sector_z = RTOFConstants.RADIUS*Math.cos(Math.toRadians(RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE));

	// Global coordinates of the sector
        double[] globalCoordsSector = {sector_x, sector_y, sector_z};

	// Rotation angle in radians 
        double thetaY = 0;

	if(this.sector==1) thetaY = Math.toRadians(-(RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE));
	if(this.sector==2) thetaY = Math.toRadians(RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE);
	
        // Rotation matrix around the Y-axis
        double[][] Ry = {
            {Math.cos(thetaY), 0, Math.sin(thetaY)},
            {0, 1, 0},
            {-Math.sin(thetaY), 0, Math.cos(thetaY)}
        };

        // Rotate local coordinates
        double[] rotatedCoords = new double[3];
        for (int i = 0; i < 3; i++) {
            rotatedCoords[i] = Ry[i][0] * localCoords[0] + Ry[i][1] * localCoords[1] + Ry[i][2] * localCoords[2];
        }

        // Calculate global coordinates for the hit
        double[] globalCoordsBar = new double[3];
        for (int i = 0; i < 3; i++) {
            globalCoordsBar[i] = globalCoordsSector[i] + rotatedCoords[i];
        }
	
	
	this.x = globalCoordsBar[0]; 
        this.y = globalCoordsBar[1];
        this.z = globalCoordsBar[2];
        return 0;
    }

    /**
     * Compares two RTOFRawHit objects to check if they match in the bar.
     * <ul>
     * <li>If the sector or row or column of the two hits do not match, the method
     * returns {@code false}.</li>
     * <li>If both hits are in the same SiPM (i.e., their order is the same), or
     * have incorrect order, the method returns {@code false}.</li>
     * </ul>
     * If none of these conditions are violated, the method returns
     * {@code true}, indicating the two hits match.
     *
     * @param hit2match The RTOFRawHit object to compare with the current instance.
     * @return {@code true} if the hits match; {@code false} otherwise.
     */
    public boolean matchBar(RTOFRawHit hit2match) {
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
    public RTOFRawHit(int sector, int row, int column, int order, int tdc, int tot) {
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

    public RTOFRawHit() {
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}
