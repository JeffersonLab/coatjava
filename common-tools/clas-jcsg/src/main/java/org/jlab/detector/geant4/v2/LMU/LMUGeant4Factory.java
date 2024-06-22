package org.jlab.detector.geant4.v2.LMU;

import eu.mihosoft.vrl.v3d.Vector3d;
import org.jlab.detector.geant4.v2.Geant4Factory;
import org.jlab.detector.volume.G4World;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.volume.G4Box;


/**
 * Generate GEANT4 volume for the URWELL detector
 * 
 * @author bondi
 */
public final class LMUGeant4Factory extends Geant4Factory {
    
    private int nRegions  = LMUConstants.NREGIONS;
    
    

    /**
     * Create the URWELL full geometry
     * @param cp
     */
    public LMUGeant4Factory( DatabaseConstantProvider cp) {
        LMUConstants.connect(cp );
        this.init(cp);
    }
    
    public void init(DatabaseConstantProvider cp) {
   
        motherVolume = new G4World("root");
        nRegions = LMUConstants.NREGIONS;
        

        for (int iregion = 0; iregion <nRegions ; iregion++) {
            Geant4Basic sectorVolume = createRegion(iregion);
            sectorVolume.setMother(motherVolume);
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
         for (int i=0; i< LMUConstants.CHAMBERVOLUMESTHICKNESS.length; i++ )
             chamberT+=LMUConstants.CHAMBERVOLUMESTHICKNESS[i];
         return chamberT;
    }


    
    /**
     * Calculates the sector dimensions
     * @p    * @return an array of doubles containing trapezoid dimensions:     
     **  half thickness, half small base , half large base, half height, tilt angle
     */
    
    public double[] getRegionDimensions(){
        double[] regionDimension = new double[5];
        
        regionDimension[0] = LMUConstants.XSIZE/2 + LMUConstants.XENLARGEMENT ;
        regionDimension[1] = LMUConstants.YSIZE/2 + LMUConstants.YENLARGEMENT ;
        regionDimension[2] = (this.getChamberThickness())/2. + LMUConstants.ZENLARGEMENT ;
               
        return regionDimension;
    }
    
        // Baricenter coordinate in CLAS12 frame
    
    /**
    * Calculates sector baricenter coordinate in CLAS12 frame
     * @param iregion
     * @return Vector3d (X,Y,Z)
    */
    
    public Vector3d getRegionPosition(int iregion)
    {
        
        Vector3d vCenter = new Vector3d(0, 0, LMUConstants.DIST2TGT[iregion]);
        
        return vCenter;
    }
    

    /**
     * Creates and positions the region volume in the given sector, and 
        populates it with the three chamber volumes
     * @param iregion (0)
     * @return the region volume
     */
    public Geant4Basic createRegion(int iregion) {

        double[] dim = this.getRegionDimensions();
    
        double regionDX   = dim[0] ;
        double regionDY   = dim[1] ;
        double regionDZ   = dim[2] ;
        
        
        Vector3d vCenter = this.getRegionPosition(iregion);

        Geant4Basic regionVolume = new G4Box("LMU_region" + (iregion + 1), regionDX, regionDY, regionDZ);
        regionVolume.translate(vCenter.x, vCenter.y, vCenter.z);
        regionVolume.setId(iregion + 1, 0, 0);
        
        Geant4Basic chamberVolume = this.createChamber(iregion);

        chamberVolume.setName("region" + (iregion + 1));
           
        chamberVolume.setMother(regionVolume);
        chamberVolume.setId(iregion + 1, 1, 0);

               
        return regionVolume;
    }

    

    /**
     * Creates the chamber volume 
     * 
     * @param iRegion (0)
     * @return the chamber volume
     */  
    public Geant4Basic createChamber(int iRegion) {
                
        double chamberDX    = this.getChamberDimensions()[0];     
        double chamberDY    = this.getChamberDimensions()[1];
        double chamberDZ    = this.getChamberDimensions()[2];        

        Geant4Basic chamberVolume = new G4Box("region" + (iRegion + 1), chamberDX, chamberDY, chamberDZ);

        double daughterDX  = this.getDaughterChamberDimensions()[0];
        double daughterDY  = this.getDaughterChamberDimensions()[1];
        
        double  daughterVolumeZ =0;
       
        for (int i=0; i< LMUConstants.CHAMBERVOLUMESTHICKNESS.length; i++ ){
 
            if(i==0) {
                daughterVolumeZ = LMUConstants.CHAMBERVOLUMESTHICKNESS[i]/2 - (this.getChamberThickness())/2.;
             } 
            else {
                daughterVolumeZ += LMUConstants.CHAMBERVOLUMESTHICKNESS[i-1]/2 + LMUConstants.CHAMBERVOLUMESTHICKNESS[i]/2;
            }
            
            Geant4Basic daughterVolume = new G4Box("daughter_volume", daughterDX, daughterDY,
                LMUConstants.CHAMBERVOLUMESTHICKNESS[i]/2);
            
            daughterVolume.setName("region" + (iRegion + 1) +"_"+LMUConstants.CHAMBERVOLUMESNAME[i] );
            
            
            daughterVolume.setMother(chamberVolume);
            daughterVolume.setPosition(0.0, 0.0,daughterVolumeZ);
        }
        return  chamberVolume;
    }
    
    
    public double[] getChamberDimensions()
    {
        
        double[] dim = new double[3];
        
        dim[2] = (this.getChamberThickness())/2. + LMUConstants.ZENLARGEMENT/2;
        dim[1] = LMUConstants.YSIZE/2 + LMUConstants.YENLARGEMENT/2;
        dim[0] = LMUConstants.XSIZE/2 + LMUConstants.XENLARGEMENT/2;
        
        return dim;
        
    }
    /**
     * Calculates the daughter chamber dimensions
     * 
     * @return an array of doubles containing trapezoid dimensions: half small base , half large base, half height
     */
    public double[] getDaughterChamberDimensions(){
       
        double[] dim = new double[3];
        
        dim[2] = (this.getChamberThickness())/2.;
        dim[1] = LMUConstants.YSIZE/2 ;
        dim[0] = LMUConstants.XSIZE/2 ;
        
        return dim;
    }

    /**
     * Returns the chamber volume for the chosen sector and chamber
     * 
     * @param region (1-6)
     * @return the chamber volume
     */
    public Geant4Basic getChamberVolume(int region) {

        String volumeName = "region" + region + "_cathode_gas";
        
        return this.getAllVolumes().stream()
                      .filter(volume -> (volume.getName().contains(volumeName)))
                      .findAny()
                      .orElse(null);
    }

    /**
     * Returns the sector volume for the given sector number
     * 
     * @param region (1-6)
     * @return the region volume
     */
    public Geant4Basic getRegionVolume(int region) {

        String volName = "LMU_region" + region ;
        return this.getAllVolumes().stream()
                      .filter(volume -> (volume.getName().contains(volName)))
                      .findAny()
                      .orElse(null);
    }

    
 
    
    
    public static void main(String[] args) {
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");

        LMUConstants.connect(cp);
        
        LMUGeant4Factory factory = new LMUGeant4Factory(cp);
            
        factory.getAllVolumes().forEach(volume -> {
            System.out.println(volume.gemcString());
        });
        
     

    }

}
