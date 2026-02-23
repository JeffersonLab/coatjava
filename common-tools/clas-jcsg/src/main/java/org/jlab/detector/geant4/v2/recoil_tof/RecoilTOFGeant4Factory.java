package org.jlab.detector.geant4.v2.rtof;

import eu.mihosoft.vrl.v3d.Vector3d;
import org.jlab.detector.geant4.v2.Geant4Factory;
import org.jlab.detector.volume.G4World;
import org.jlab.detector.volume.G4Box;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;

/**
 * Generate GEANT4 volume for the RECOIL TOF detector
 * 
 * @author Nilanga Wickramaarachchi
 */
public final class RTOFGeant4Factory extends Geant4Factory {
    
    private int nSectors  = RTOFConstants.NSECTORS;
    private int nRows = RTOFConstants.NROWS;
    private int nCols = RTOFConstants.NCOLUMNS;
    
    public RTOFGeant4Factory( DatabaseConstantProvider cp) {
        RTOFConstants.connect(cp );
        this.init(cp);
    }
    
    public void init(DatabaseConstantProvider cp) {
   
        motherVolume = new G4World("root");

	for (int isector = 0; isector < nSectors; isector++) {
	    Geant4Basic sectorVolume = createSector(isector, nRows, nCols);

	    sectorVolume.setName("recoil_tof_sector" + (isector + 1));
	    sectorVolume.setMother(motherVolume);
	}
    }


    public Vector3d getCenterCoordinate(int isector)
    {
        int is=isector;
        Vector3d vCenter = new Vector3d(0, 0, 0);
        
	vCenter.x = (-1+is*2)*(RTOFConstants.RADIUS)*Math.sin(Math.toRadians(RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE));
	vCenter.y = 0;
	vCenter.z =RTOFConstants.RADIUS*Math.cos(Math.toRadians(RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE));
        return vCenter;
    }


    
    public Geant4Basic createSector(int isector, int nRows, int nCols ) {

        double hlx = RTOFConstants.WIDTH/2+1;
        double hly  = RTOFConstants.LENGTH/2+1;
        double hlz = RTOFConstants.THICKNESS/2+1; 

        Vector3d vCenter = this.getCenterCoordinate(isector);

	Geant4Basic sectorVolume = new G4Box("recoil_tof_sector" + (isector + 1), hlx, hly, hlz);

	if(isector==0) sectorVolume.rotate("yxz",Math.toRadians((RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE)),0,0);
	if(isector==1) sectorVolume.rotate("yxz",Math.toRadians(-(RTOFConstants.HORIZONTAL_OPENING_ANGLE/2+RTOFConstants.HORIZONTAL_STARTING_ANGLE)),0,0);
        sectorVolume.translate(vCenter.x, vCenter.y, vCenter.z);
        sectorVolume.setId(isector + 1, 0, 0);
	
        // Bars construction
	for (int row = 0; row < nRows; row++) {
	    for (int col = 0; col < nCols; col++) {

		Geant4Basic barVolume = this.createBar(isector, row, col);

                barVolume.setName("bar_sector" + (isector + 1) + "_row" + (row + 1) + "_column" + (col + 1));
             
		barVolume.setMother(sectorVolume);

		barVolume.setId(isector + 1, row +1, col+1, 0);
	    }
	}
               
        return sectorVolume;
    }

    
    public Geant4Basic createBar(int iSector, int iRow, int iCol) {
        
	int nCols = RTOFConstants.NCOLUMNS;

	double barDX = RTOFConstants.BAR_WIDTH/2;
	double barDY;

	if (iRow == (nRows - 1) / 2) barDY = RTOFConstants.SHORT_BAR_LENGTH/2;
	else barDY = RTOFConstants.LONG_BAR_LENGTH/2;

	double barDZ = RTOFConstants.BAR_THICKNESS/2;
        
	Geant4Basic barVolume = new G4Box("bar_sector" + (iSector + 1) + "_row" + (iRow + 1) + "_column" + (iCol + 1), barDX, barDY, barDZ);

	// Constants for positioning
	double y_start = -(RTOFConstants.LENGTH - RTOFConstants.LONG_BAR_LENGTH)/2;  // Starting Y position
	double x_spacing = RTOFConstants.BAR_WIDTH;
	double x_start = -(RTOFConstants.WIDTH - x_spacing)/2;  // starting X position
	double dy_long = RTOFConstants.LONG_BAR_LENGTH;
	double dy_short = RTOFConstants.SHORT_BAR_LENGTH;

	//Position calculation
	double z_pos = 0;
	double x_pos = x_start + (iCol * x_spacing);
	    
        double y_pos;
	if(iRow < (nRows - 1) / 2)
	    {
		y_pos = y_start + (iRow * dy_long);
	    }
	else if (iRow == (nRows - 1) / 2) // middle row
	    {  
		y_pos = 0;
	    }
	else
	    {
		y_pos = y_start + (iRow -1) * dy_long + dy_short;
	    }
	    	
	barVolume.setPosition(x_pos, y_pos, z_pos);
	
	return barVolume;
    }
    
    
     
    
    public static void main(String[] args) {
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");

        RTOFConstants.connect(cp);
        
        RTOFGeant4Factory factory = new RTOFGeant4Factory(cp);
            
        factory.getAllVolumes().forEach(volume -> {
            System.out.println(volume.gemcString());
        });

    }    
}
