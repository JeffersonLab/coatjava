package org.jlab.detector.geant4.v2.recoil.trk;

import eu.mihosoft.vrl.v3d.Vector3d;
import java.util.List;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.hits.DetHit;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.geometry.prim.Line3d;
import org.jlab.geometry.prim.Straight;
import org.jlab.utils.groups.IndexedList;

/**
 * Creates and handles the URWELL detector strips as 3D lines
 * 
 * @author bondi, niccolai
 */
public final class RtrkStripFactory {

    private RtrkGeant4Factory factory;
    private IndexedList<Line3D>  globalStrips = new IndexedList(3);
    private IndexedList<Line3D>  localStrips  = new IndexedList(3);
    private IndexedList<Plane3D> planeStrips  = new IndexedList(3);
    private int nRegions;
    private int nSectors;
    private int nChambers;
    private int nLayers;
    
    public RtrkStripFactory() {
    }
    
    /**
     * Create the strip factory based on constants from CCDB.
     * Currently constants are defined in the RtrkConstants class. 
     * They will be moved to CCDB when finalized).
     * @param cp database provide
     */
    public RtrkStripFactory(DatabaseConstantProvider cp) {
        this.init(cp);
    }
    
    /**
     * Initialize the factory by the strip maps
     * @param cp
     */
    public void init(DatabaseConstantProvider cp) {
        this.init(cp, 1);
    }
   
    /**
     * Create the strip factory based on constants from CCDB.
     * Currently constants are defined in the RtrkConstants class.
     * They will be moved to CCDB when finalized).
     * @param cp database provide
     * @param regions
     */
    public RtrkStripFactory(DatabaseConstantProvider cp, int regions) {
        this.init(cp, regions);
    }
    
    /**
     * Initialize the factory by the strip maps
     * @param cp
     * @param regions
     */
    public void init(DatabaseConstantProvider cp, int regions) {
        factory = new RtrkGeant4Factory(cp, regions);
	nRegions  = Math.min(RtrkConstants.NMAXREGIONS, regions);
	nSectors  = RtrkConstants.NSECTORS;
	nChambers = RtrkConstants.NCHAMBERS;
	nLayers   = RtrkConstants.NLAYERS;
        this.fillStripLists();
	//        this.fillPlaneLists();
    }

    /**
     * Calculates the total number of strips in a sector
     * 
     * @return the strip number
     */
    public int getNStripSector() {
        int nStrips = 0;
        for (int i = 0; i < nChambers; i++) {
	    for (int j = 0; j < nRegions; j++) {
		nStrips += getNStripChamber(i,j);
	    }
	}
        return nStrips;
    }

    /**
     * Calculates the number of strips in the given chamber
     * 
     * @param ichamber (0, 1, 2)
     * @return the strip number (1-N)
     */
    public int getNStripChamber(int ichamber, int regions) {

	int iregion = regions;
	int chamber = ichamber;
        double[] dim = factory.getChamber_daughter_Dimensions(iregion,chamber);

        double yHalf = dim[1];
	double xHalf = dim[0];

        // C-------------D //
        //  -------------  //
        //  -------------   //
        // A-------------B   //
        /**
         * * number of strip in AB**
         */
	
	int nAB = (int) (2 * xHalf / RtrkConstants.PITCH);
  	int nAC = (int) (2 * yHalf / RtrkConstants.PITCH);

	int nStrips = nAB + nAC;

        return nStrips;
    }

    /**
     * Provides the index of the chamber containing the strip with the given ID
     * 
     * @param strip (1 to N)
     * @return the chamber index (0, 1, 2)
     */
    public int getChamberIndex(int strip) {
        int nStripTotal = 0;

        for(int i=0; i<nChambers; i++) {
	    for(int j=0; j<nRegions; j++) {
		nStripTotal += this.getNStripChamber(i,j);
	    }
	
	    if(strip <= nStripTotal){
		return i;
	    }
        }      
        return -1;
    }

    private int getLocalStripId(int strip) {
        
        int chamberIndex = getChamberIndex(strip);

        //Strip ID wrt sector -> strip ID chamber (from 1 to getNStripChamber)
        int nStripTotal = 0;
        if (chamberIndex > 0) {
            for (int i = 0; i < chamberIndex; i++) {
		for (int j = 0; j < nRegions; j++) {
		    nStripTotal += this.getNStripChamber(i,j);		
		}
	    }
        }

        //Strip ID: from 1 to  getNStripChamber       
        int cStrip = strip - nStripTotal;
        
        return cStrip;
    }
        
    /**
     * Builds the given strip line in the CLAS12 frame
     * @param sector (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    private Line3d createStrip(int sector, int region, int layer, int strip) {

        int chamberIndex = getChamberIndex(strip);
        int cStrip = this.getLocalStripId(strip);
	
        // CHAMBER reference frame
        // new numeration with stri ID_strip=0 crossing (0,0,0) of chamber
        double[] dim = factory.getChamber_daughter_Dimensions(region-1,chamberIndex);
        
        double yHalf          = dim[1];
	double xHalf          = dim[0];

	double DY = -xHalf; //v strip
	                
        // Y coordinate of the intersection point between the x=0 and the strip line crossing for B

	if (layer % 2 != 0) { //u strip
	    DY = -yHalf;
	}
	// ID of the strip
	int nS =  (int) (DY / RtrkConstants.PITCH);	
        int nCStrip = nS + (cStrip - 1);
	double c = nCStrip * RtrkConstants.PITCH;

        // Take 2 points in the strip straight line. They needs to define Line object 
        //u strips
        double oX = -xHalf;
        double oY = c;
        double oZ = 0;

        double eX = xHalf;
        double eY = c;
        double eZ = 0;
	
        if (layer % 2 == 0) { //v strips
	    oX = c;
	    oY = -yHalf;
	    oZ = 0;
	
	    eX = c;
	    eY = yHalf;
	    eZ = 0;
	}
	
	Vector3d origin = new Vector3d(oX, oY, oZ);
	Vector3d end = new Vector3d(eX, eY, eZ);
	
        // Get Chamber Volume
        Geant4Basic chamberVolume = factory.getChamberVolume(sector, region, chamberIndex+1, layer);
            
        // 2 point defined before wrt the GLOBAL frame     
        Vector3d globalOrigin = chamberVolume.getGlobalTransform().transform(origin);
   
        Vector3d globalEnd    = chamberVolume.getGlobalTransform().transform(end);

        Straight line = new Line3d(globalOrigin, globalEnd);
         
        // CHECK intersections between line and volume
        chamberVolume.makeSensitive();
        List<DetHit> Hits = chamberVolume.getIntersections(line);
	
        if (Hits.size() >= 1) {
      
                Vector3d TestOrigin = Hits.get(0).origin();
                Vector3d TestEnd = Hits.get(0).end();

            return new Line3d(Hits.get(0).origin(), Hits.get(0).end());

        } else {
            return null;
        }
    }

     /**
     * Provides the given strip line in the Chamber local frame
     * @param region (1-2)
     * @param sector (1-6)
     * @param layer (1-4)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    
    private Line3d getChamberStrip(int region, int sector, int chamber, int layer, int strip) {

	Line3d globalStrip = createStrip(sector, region, layer, strip);
	Geant4Basic chamberVolume = factory.getChamberVolume(sector, region, chamber, layer);
	
	Vector3d origin = chamberVolume.getGlobalTransform().invert().transform(globalStrip.origin());
	Vector3d end    = chamberVolume.getGlobalTransform().invert().transform(globalStrip.end());
	
	Line3d localStrip = new Line3d(origin, end);
	
	return localStrip;
    }
    /**
     * Provides the given strip line in the sector local frame
     * @param sector (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    private Line3d getLocalStrip(int region, int sector, int layer, int strip) {

	
        Line3d globalStrip = createStrip(sector, region, layer, strip);
        Geant4Basic sVolume = factory.getSectorVolume(region, sector);

        Vector3d origin = sVolume.getGlobalTransform().invert().transform(globalStrip.origin());
        Vector3d end    = sVolume.getGlobalTransform().invert().transform(globalStrip.end());

        Line3d localStrip = new Line3d(origin, end);

        return localStrip;
    }
    
    
    private void fillStripLists() {
        
        for(int ir=0; ir<nRegions; ir++) {
            int region = ir+1;
            for(int is=0; is<nSectors; is++) {
                int sector = is+1;
                for(int il=0; il<nLayers; il++) {
                    int layer = (2*region-1) + il;
                    for(int ic=0; ic<this.getNStripSector(); ic++) {
                         int strip = ic+1;
                       
			 int chamberIndex = getChamberIndex(strip);
			 double[] dim = factory.getChamber_daughter_Dimensions(region-1,chamberIndex);

			 double yHalf          = dim[1];
			 double xHalf          = dim[0];
			 int cStrip = this.getLocalStripId(strip);
			 double DY = -xHalf; //v strip
			 if ((layer) % 2 != 0) { //u strip
			     DY = -yHalf;
			 }
			 int nS =  (int) (DY / RtrkConstants.PITCH);
			 int nCStrip = nS + (cStrip - 1);
			 double c = nCStrip * RtrkConstants.PITCH;
			 if (((layer) % 2 == 0 && c>-xHalf && c<xHalf)||((layer) % 2 != 0 && c>-yHalf && c<yHalf))
			     {
			 
				 Line3d line = this.createStrip(sector, region,layer, strip);                                       
				 Point3D origin = new Point3D(line.origin().x, line.origin().y, line.origin().z);
				 
				 Point3D end    = new Point3D(line.end().x,    line.end().y,    line.end().z);
                     
				 Line3D global = new Line3D(origin, end);
				 Line3D local = this.toLocal(sector, global);
                    
				 this.globalStrips.add(global, sector, layer, strip);
				 this.localStrips.add(local, sector, layer, strip);
			     }
                    }
                }
            }
        }
    }
    
    /**
     * Transform the given strip line to the tilted frame
     * @param sector (1-6)
     * @param global
     * @return the 3D strip line in the tilted frame as a Line3D
     */
    public Line3D toLocal(int sector, Line3D global) {
        Line3D local = new Line3D();
        local.copy(global);
	local.rotateY((-1+sector*2)*Math.toRadians(1.5*RtrkConstants.HORIZONTHAL_OPENING_ANGLE+270));
        
        return local;
    }
    
    private void fillPlaneLists() {

        for(int ir=0; ir<nRegions; ir++) {
            int region = ir+1;
            for(int is=0; is<nSectors; is++) {
                int sector = is+1;
		for(int il=0; il<nLayers; il++) {
 
                    int layer = (2*region-1) + il;

                    for(int ic=0; ic<this.getNStripSector(); ic++) {
                         int strip = ic+1;

			 int chamberIndex = getChamberIndex(strip);
			 double[] dim = factory.getChamber_daughter_Dimensions(region-1,chamberIndex);

			 double yHalf          = dim[1];
			 double xHalf          = dim[0];
			 int cStrip = this.getLocalStripId(strip);
			 double DY = -xHalf; //v strip
			 if (layer % 2 != 0) { //u strip
			     DY = -yHalf;
			 }
			 int nS =  (int) (DY / RtrkConstants.PITCH);
			 int nCStrip = nS + (cStrip - 1);
			 double c = nCStrip * RtrkConstants.PITCH;
			 if ((layer % 2 == 0 && c>-xHalf && c<xHalf)||(layer % 2 != 0 && c>-yHalf && c<yHalf))
			     {
				 Plane3D plane = this.createPLane(sector, region, layer, strip);
				 this.planeStrips.add(plane, sector, layer, strip);
			     }
                    }
                }
            }
        }
    }
        
    
    public Plane3D getPlane(int sector, int layer, int strip){
        
        return planeStrips.getItem(sector, layer, strip);
    }
    
    
    /**
     * Provides the 3D line for the given strip in the CLAS12 frame
     * @param sector (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line in the CLAS12 frame as a Line3D
     */
    public Line3D getStrip(int sector, int layer, int strip) {

        return globalStrips.getItem(sector, layer, strip);
    }
    
    /**
     * Provides the 3D line for the given strip in the tilted frame
     * @param sector (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line in the tilted frame as a Line3D
     */
    public Line3D getTiltedStrip(int sector, int layer, int strip) {
        return localStrips.getItem(sector, layer, strip);
    }
        
    private Plane3D createPLane(int sector, int region, int layer, int strip){

        int chamber = this.getChamberIndex(strip);
	int LastStripID = this.getNStripChamber(chamber, region);

	Line3D Last_strip = this.getStrip(sector, layer, LastStripID);
	
	Line3D First_strip = this.getStrip(sector, layer, 1);
	
	Line3D test_strip = this.getStrip(sector, layer, 1);
	
	Vector3D Dir_strip_test = First_strip.originDir();
	
	/* Line orthogonal to the 2 strip */
	Line3D line = First_strip.distance(Last_strip);
	
	Vector3D Dir_line = line.originDir();
	
	Vector3D normal_plane = Dir_strip_test.cross(Dir_line);
	
	Plane3D plane = new Plane3D(First_strip.origin(), normal_plane);
	
	return plane;
    }    
    
    public static void main(String[] args) {
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");

        RtrkConstants.connect(cp);

        RtrkGeant4Factory factory = new RtrkGeant4Factory(cp,1);

        RtrkStripFactory factory2 = new RtrkStripFactory(cp,1);
  
	//        Plane3D plane = factory2.getPlane(6, 1, 200);
        //System.out.println(plane.toString());

        //int strip =20;
        //System.out.println((strip) + " " + factory2.getLocalStripId(strip) + "\n" + factory2.getChamberStrip(1, 6,1,2,strip)) ; 
        
	for(int istrip=0; istrip<factory2.getNStripSector(); istrip++)  {
        System.out.println((istrip+1) + " " + factory2.getChamberIndex(istrip+1) + "\n" + factory2.getStrip(1, 1, istrip+1) + "\n" + factory2.getStrip(1, 2, istrip+1));}
        
        
    }

}
