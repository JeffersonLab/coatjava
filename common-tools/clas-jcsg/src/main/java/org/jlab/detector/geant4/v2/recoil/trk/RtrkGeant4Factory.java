package org.jlab.detector.geant4.v2.recoil.trk;

import eu.mihosoft.vrl.v3d.Vector3d;
import org.jlab.detector.geant4.v2.Geant4Factory;
import org.jlab.detector.volume.G4Trap;
import org.jlab.detector.volume.G4World;
import org.jlab.detector.volume.G4Box;
import org.jlab.detector.volume.G4Trd;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.geometry.prim.Line3d;
import org.jlab.geometry.prim.Straight;

/**
 * Generate GEANT4 volume for the RECOIL tracker
 * 
 * @author bondi, niccolai
 */
public final class RtrkGeant4Factory extends Geant4Factory {
    
    private int nRegions  = RtrkConstants.NREGIONS;
    private int nSectors  = RtrkConstants.NSECTORS;
    private int nChambers = RtrkConstants.NCHAMBERS;
    
     /**
     * Create the URWELL full geometry
     * @param cp
     * @param nRegions
     */
    public RtrkGeant4Factory( DatabaseConstantProvider cp, int nRegions) {
        RtrkConstants.connect(cp );
        this.init(cp, nRegions);
    }
    
    public void init(DatabaseConstantProvider cp, int regions ) {
   
        motherVolume = new G4World("root");
            nRegions = Math.min(RtrkConstants.NMAXREGIONS, regions);

        for (int iregion = 0; iregion <regions ; iregion++) {
            for (int isector = 0; isector < nSectors; isector++) {
                Geant4Basic sectorVolume = createSector(isector, iregion, nChambers);
                sectorVolume.setMother(motherVolume);
            }
        }
    }

    /**
     * Calculates the total detector thickness from the sum of the individual
     * layers thicknesses
     *   
     * @return thickness in cm
     */
    public double getChamberThickness(){
        double chamberT =0;
         for (int i=0; i< RtrkConstants.CHAMBERVOLUMESTHICKNESS.length; i++ )
             chamberT+=RtrkConstants.CHAMBERVOLUMESTHICKNESS[i];
         return chamberT;
    }
    
    /**
     * Calculates the sector dimensions
     * @p    * @return an array of doubles containing trapezoid dimensions:     
     **  half thickness, half small base , half large base, half height, tilt angle
     */
    /*    Half-length along x at the surface positioned at -dz
     *    Half-length along x at the surface positioned at +dz
     *    Half-length along y at the surface positioned at -dz
     *    Half-length along y at the surface positioned at +dz
     *    Half-length along z axis
     */

    public double[] getSectorDimensions(int iregion){

	int i=iregion;
        double[] SectorDimensions = new double[5];

	SectorDimensions[0] = RtrkConstants.WIDTH[i]/2+1.;
	SectorDimensions[1] = RtrkConstants.HEIGHT[i]/2+1.;
	SectorDimensions[2] = (this.getChamberThickness())/2.+1;
	
	/*	SectorDimensions[0] = (this.getChamberThickness())/2. + RtrkConstants.ZENLARGEMENT ;
	SectorDimensions[1] = RtrkConstants.SECTORHEIGHT/2 + RtrkConstants.YENLARGEMENT ;
	SectorDimensions[2] = RtrkConstants.DX0CHAMBER0 + RtrkConstants.XENLARGEMENT ;
	SectorDimensions[3] = (SectorDimensions[1]*2)*Math.tan(Math.toRadians(RtrkConstants.THOPEN/2))+SectorDimensions[2];  
	SectorDimensions[4] = Math.toRadians(RtrkConstants.THTILT);  
        */
	
        return SectorDimensions;
    }
    
        // Baricenter coordinate in CLAS12 frame
    
    /**
    * Calculates sector baricenter coordinate in CLAS12 frame
     * @param isector
     * @param iregion
     * @return Vector3d (X,Y,Z)
    */
    
    public Vector3d getCenterCoordinate(int isector, int iregion)
    {
        int is=isector;
	int ir=iregion;
        Vector3d vCenter = new Vector3d(0, 0, 0);
        
	vCenter.x = (-1+is*2)*(RtrkConstants.RADIUS[iregion])*Math.sin(Math.toRadians(1.5*RtrkConstants.HORIZONTHAL_OPENING_ANGLE));
	vCenter.y = 0;
	vCenter.z =RtrkConstants.RADIUS[iregion]*Math.cos(Math.toRadians(1.5*RtrkConstants.HORIZONTHAL_OPENING_ANGLE));
        return vCenter;
    }
    

    /**
     * Creates and positions the region volume in the given sector, and 
        populates it with the three chamber volumes
     * @param isector (0-5)
     * @param iregion (0)
     * @param Nchambers : number of chambers in each sector
     * @return the region volume
     */
    public Geant4Basic createSector(int isector, int iregion, int Nchambers) {

        double[] dimSect = this.getSectorDimensions(iregion);
        double hlx    = dimSect[0] ;
        double hly   = dimSect[1] ;
        double hlz = dimSect[2] ;
        // baricenter coordinate in CLAS12 frame 

        Vector3d vCenter = this.getCenterCoordinate(isector,iregion);
                // Sector construction
	/*        Geant4Basic sectorVolume = new G4Trap("region_Rtrk_" + (iregion + 1) + "_s" + (isector + 1),
                regionDZ, -regionThilt, Math.toRadians(90.0),
                regionDY, regionDX0, regionDX1, 0.0,
                regionDY, regionDX0, regionDX1, 0.0);*/

	Geant4Basic sectorVolume = new G4Box("region_rtrk_" + (iregion + 1) + "_s" + (isector + 1),hlx,hly,hlz);
  	/*       sectorVolume.rotate("yxz", 0.0, regionThilt, Math.toRadians(90.0 - isector * 60.0));*/
	sectorVolume.rotate("yxz",(-1+isector*2)*Math.toRadians(1.5*RtrkConstants.HORIZONTHAL_OPENING_ANGLE+270),0,0);
        sectorVolume.translate(vCenter.x, vCenter.y, vCenter.z);
        sectorVolume.setId(isector + 1, iregion +1, 0, 0);
	
               // Chambers construction
        for (int ich = 0; ich < Nchambers; ich++) {

	    //           double y_chamber = (2*ich+1)*(RtrkConstants.SECTORHEIGHT/RtrkConstants.NCHAMBERS/2+0.05);

            Geant4Basic chamberVolume = this.createChamber(isector, iregion, ich);

                chamberVolume.setName("rg" + (iregion + 1) + "_s" + (isector + 1) + "_c" + (ich +1));
             
            chamberVolume.setMother(sectorVolume);
            //chamberVolume.translate(0.0,y_chamber-RtrkConstants.SECTORHEIGHT/2,0. );
            chamberVolume.setId(isector + 1, iregion + 1, ich +1, 0);
         }
               
        return sectorVolume;
    }

    /**
     * Creates the chamber volume 
     * 
     * @param iSector (0-5)
     * @param iRegion (0)
     * @param iChamber (0, 1, 2)
     * @return the chamber volume
     */
    
    public Geant4Basic createChamber(int iSector, int iRegion, int iChamber) {
        
        double[] dimChamb = this.getChamber_Dimensions(iRegion,iChamber);
        
	double chamberDX    = dimChamb[0];
        double chamberDY    = dimChamb[1];
        double chamberDZ   = dimChamb[2];     
	
	/*        Geant4Basic chamberVolume = new G4Trap("r" + (iRegion + 1) + "_s" + (iSector + 1) + "_c" + (iChamber+1),
                chamberDZ, -chamberThilt, Math.toRadians(90.0),
                chamberDY, chamberDX0, chamberDX1, 0.0,
                chamberDY, chamberDX0, chamberDX1, 0.0);*/
	Geant4Basic chamberVolume = new G4Box("r" + (iRegion + 1) + "_s" + (iSector + 1) + "_c" + (iChamber+1), chamberDX, chamberDY, chamberDZ);

        double  daughterVolumeZ =0;
        double  daughterVolumeY =0;
        double[] chamberDim = getChamber_daughter_Dimensions(iRegion,iChamber);
       
        double daughterDX  = chamberDim[0];
        double daughterDY = chamberDim[1];
        //double daughterDZ = chamberDim[2];
        
       
        for (int i=0; i< RtrkConstants.CHAMBERVOLUMESTHICKNESS.length; i++ ){
 
            if(i==0) {daughterVolumeZ = RtrkConstants.CHAMBERVOLUMESTHICKNESS[i]/2 - (this.getChamberThickness())/2.;
             } else daughterVolumeZ += RtrkConstants.CHAMBERVOLUMESTHICKNESS[i-1]/2 + RtrkConstants.CHAMBERVOLUMESTHICKNESS[i]/2;
            
            //daughterVolumeY = -daughterVolumeZ *Math.tan(Math.toRadians(RtrkConstants.THTILT));
          
            
	    Geant4Basic daughterVolume = new G4Box("daughter_volume", daughterDX, daughterDY, RtrkConstants.CHAMBERVOLUMESTHICKNESS[i]/2);
            
	    daughterVolume.setName("rg" + (iRegion + 1) + "_s" + (iSector + 1) + "_c" + (iChamber +1) +"_"+RtrkConstants.CHAMBERVOLUMESNAME[i] );
            
	    daughterVolume.setMother(chamberVolume);
	    daughterVolume.setPosition(0.0, daughterVolumeY,daughterVolumeZ);
        }
        return  chamberVolume;
    }
    
    
    public double[] getChamber_Dimensions(int iregion, int ichamber)
    {
        
        double[] chamber_Dimensions = new double[5];
        int i = iregion;
	chamber_Dimensions[0] = RtrkConstants.WIDTH[i]/2+0.1;
	chamber_Dimensions[1] = RtrkConstants.HEIGHT[i]/2+0.1;
	chamber_Dimensions[2] = (this.getChamberThickness())/2. + 0.1;

        return chamber_Dimensions;
        
    }
    /**
     * Calculates the chamber daughter dimensions
     * 
     * @param ichamber (0, 1, 2)
     * @return an array of doubles containing trapezoid dimensions: half small base , half large base, half height
     */
    public double[] getChamber_daughter_Dimensions(int iregion, int ichamber){

	int i = iregion;
        double[] chamber_daughter_Dimensions = new double[3];
        
	/*	chamber_daughter_Dimensions[0] = RtrkConstants.SECTORHEIGHT/RtrkConstants.NCHAMBERS/2 ;
	chamber_daughter_Dimensions[1] = (ichamber*RtrkConstants.SECTORHEIGHT/RtrkConstants.NCHAMBERS)
	    * Math.tan(Math.toRadians(RtrkConstants.THOPEN/2.))
	    + RtrkConstants.DX0CHAMBER0 ;
	
	chamber_daughter_Dimensions[2] = (RtrkConstants.SECTORHEIGHT/RtrkConstants.NCHAMBERS)
	    * Math.tan(Math.toRadians(RtrkConstants.THOPEN/2.))+chamber_daughter_Dimensions[1];
	    */

	chamber_daughter_Dimensions[0] = RtrkConstants.WIDTH[i]/2;
	chamber_daughter_Dimensions[1] = RtrkConstants.HEIGHT[i]/2;
	
        return chamber_daughter_Dimensions;
    }

    /**
     * Returns the chamber volume for the chosen sector and chamber
     * 
     * @param sector (1-6)
     * @param chamber (1, 2, 3)
     * @return the chamber volume
     */
    public Geant4Basic getChamberVolume(int sector, int region, int chamber,int layer) {

        int r = (layer-1)/2 +1;
        int s = sector;
        int c = chamber;
	int re = region;

        String volumeName;
 
	//	volumeName = "rg" + r + "_s" + s + "_r" + re + "_c" + c + "_cathode_gas";
	volumeName = "rg" + r + "_s" + s + "_c" + c + "_cathode_gas";
	return this.getAllVolumes().stream()
                      .filter(volume -> (volume.getName().contains(volumeName)))
                      .findAny()
                      .orElse(null);
    }

    /**
     * Returns the sector volume for the given sector number
     * 
     * @param sector (1-6)
     * @return the sector volume
     */
    public Geant4Basic getSectorVolume(int region, int sector) {

        int r = region;
        int s = sector;

        String volName = "region_Rtrk_" + r + "_s" + s;
        return this.getAllVolumes().stream()
                      .filter(volume -> (volume.getName().contains(volName)))
                      .findAny()
                      .orElse(null);
    }
    
    public static void main(String[] args) {
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");

        RtrkConstants.connect(cp);
        
        RtrkGeant4Factory factory = new RtrkGeant4Factory(cp, 1);
            
        factory.getAllVolumes().forEach(volume -> {
            System.out.println(volume.gemcString());
        });

    }    
}
