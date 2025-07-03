package org.jlab.detector.geant4.v2.URWELL;

import eu.mihosoft.vrl.v3d.Transform;
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
 * @author bondi
 */
public final class URWellStripFactory {

    private URWellGeant4Factory factory;
    private IndexedList<Line3D> globalStrips = new IndexedList(3);
    private IndexedList<Line3D> localStrips = new IndexedList(3);
    private IndexedList<Plane3D> planeStrips = new IndexedList(3);
    private int nRegions;
    private int nSectors;
    private int nChambers;
    private int nLayers;
    private int Nconfig;
    private double pitch;
    private boolean isProto;

    public URWellStripFactory() {
    }

    /**
     * Create the strip factory based on constants from CCDB. Currently
     * constants are defined in the URWellConstants class. They will be moved to
     * CCDB when finalized).
     *
     * @param cp database provide
     */
    public URWellStripFactory(DatabaseConstantProvider cp) {
        this.init(cp);
    }

    /**
     * Initialize the factory by the strip maps
     *
     * @param cp
     */
    public void init(DatabaseConstantProvider cp) {
        this.init(cp, false, 1);
    }

    /**
     * Create the strip factory based on constants from CCDB. Currently
     * constants are defined in the URWellConstants class. They will be moved to
     * CCDB when finalized).
     *
     * @param cp database provide
     * @param prototype
     * @param regions
     */
    public URWellStripFactory(DatabaseConstantProvider cp, boolean prototype, int regions) {
        this.init(cp, prototype, regions);
    }

    /**
     * Create the strip factory based on constants from CCDB. Currently
     * constants are defined in the URWellConstants class. They will be moved to
     * CCDB when finalized).
     *
     * @param cp database provide
     * @param config 0 standard, 1 ddvcs proposal, 2 prototype
     * @param regions
     */
    public URWellStripFactory(DatabaseConstantProvider cp, int config, int regions) {
        this.init(cp, config, regions);
    }

    /**
     * Initialize the factory by the strip maps
     *
     * @param cp
     * @param prototype
     * @param regions
     */
    public void init(DatabaseConstantProvider cp, boolean prototype, int regions) {
        factory = new URWellGeant4Factory(cp, prototype, regions);
        isProto = prototype;
        if (!isProto) {
            nRegions = Math.min(URWellConstants.NMAXREGIONS, regions);
            nSectors = URWellConstants.NSECTORS;
            nChambers = URWellConstants.NCHAMBERS;
            nLayers = URWellConstants.NLAYERS;
            pitch =  URWellConstants.PITCH;
        } else {
            nRegions = URWellConstants.NREGIONS_PROTO;
            nSectors = URWellConstants.NSECTORS_PROTO;
            nChambers = URWellConstants.NCHAMBERS_PROTO;
            nLayers = URWellConstants.NLAYERS;
            pitch =  URWellConstants.PITCH;
        }
        this.fillStripLists();
        this.fillPlaneLists();
    }

    /**
     * Initialize the factory by the strip maps
     *
     * @param cp
     * @param config 0 standard, 1 ddvcs proposal, 2 prototype
     * @param regions
     */
    public void init(DatabaseConstantProvider cp, int config, int regions) {
        factory = new URWellGeant4Factory(cp, config, regions);

        switch (config) {
            case 0 -> {

                nSectors = URWellConstants.NSECTORS;
                nChambers = URWellConstants.NCHAMBERS;
                nRegions = Math.min(URWellConstants.NMAXREGIONS, regions);
                nLayers = URWellConstants.NLAYERS;
                isProto = false;
                Nconfig = config;
                pitch =  URWellConstants.PITCH;
            }
            case 1 -> {

                nRegions = URWellConstants.NREGIONS_PROTO;
                nSectors = URWellConstants.NSECTORS_PROTO;
                nChambers = URWellConstants.NCHAMBERS_PROTO;
                nLayers = URWellConstants.NLAYERS;
                isProto = true;
                Nconfig = config;
                pitch =  URWellConstants.PITCH;
            }
            case 2 -> {
                nRegions = Math.min(URWellConstants.NMAXREGIONS, regions);
                nSectors = URWellConstants.NSECTORS;
                nChambers = URWellConstants.NCHAMBERS_ddvcs;
                nLayers = URWellConstants.NLAYERS;
                isProto = false;
                Nconfig = config;
                pitch =  URWellConstants.PITCH_ddvcs;

            }
            default -> {
            }
        }

        this.fillStripLists();
        this.fillPlaneLists();
    }

    public double getStereoAngle(int layer, int ichamber) {

        double stereoAngle = Math.toRadians(URWellConstants.STEREOANGLE);

        double[] dim = factory.getChamber_daughter_Dimensions(layer, ichamber);

        double yHalf = dim[0];
        double xHalfSmallBase = dim[1];
        double xHalfLargeBase = dim[2];

        if (Nconfig == 2) {

            stereoAngle = Math.abs(Math.atan2(2 * yHalf, (-xHalfSmallBase + xHalfLargeBase)));

        }

        
        if (layer % 2 != 0) {
            stereoAngle = -stereoAngle;
        }
        
        return stereoAngle;

    }

    /**
     * Calculates the total number of strips in a sector
     *
     * @param layer
     * @return the strip number
     */
    public int getNStripSector(int layer) {
        int nStrips = 0;
        for (int i = 0; i < nChambers; i++) {
            // System.out.println("indice camera "+i+" numero strip "+getNStripChamber(i));
      
            nStrips += getNStripChamber(layer, i);
            
        }
        return nStrips;
    }

    /**
     * Calculates the number of strips in the given chamber
     *
     * @param layer (1 to N)
     * @param ichamber (0, 1, 2)
     * @return the strip number (1-N)
     */
    public int getNStripChamber(int layer, int ichamber) {
        
        
        double[] dim = factory.getChamber_daughter_Dimensions(layer, ichamber);

        double yHalf = dim[0];
        double xHalfSmallBase = dim[1];
        double xHalfLargeBase = dim[2];

        double stereoAngle = Math.abs(getStereoAngle(layer, ichamber));

        // C-------------D //
        //  -------------  //
        //   -----------   //
        //    A-------B   //
        /**
         * * number of strip in AB**
         */

        
        int nAB = (int) Math.round(2 * xHalfSmallBase / (pitch / Math.sin(stereoAngle)));

       
    
        double AC = Math.sqrt((Math.pow((xHalfSmallBase - xHalfLargeBase), 2) + Math.pow((2 * yHalf), 2)));
        double theta = Math.acos(2 * yHalf / AC);

        int nAC = (int) Math.round(AC / (pitch / Math.cos(theta - stereoAngle)));
          
        int nStrips = nAB + nAC;

        return nStrips;
    }

    /**
     * Provides the index of the chamber containing the strip with the given ID
     *
     * @param layer (1 to N)
     * @param strip (1 to N)
     * @return the chamber index (0, 1, 2)
     */
    public int getChamberIndex(int layer, int strip) {
        int nStripTotal = 0;

        for (int i = 0; i < nChambers; i++) {
            nStripTotal += this.getNStripChamber(layer, i);

            if (strip <= nStripTotal) {
                
                return i;
            }

        }
        return -1;
    }

    private int getLocalStripId(int layer, int strip) {

        int chamberIndex = getChamberIndex(layer, strip);
            
        //Strip ID wrt sector -> strip ID chamber (from 1 to getNStripChamber)
        int nStripTotal = 0;
 
            for (int i = 0; i < chamberIndex; i++) {
               
                nStripTotal += this.getNStripChamber(layer, i);
            }
        
       
        //Strip ID: from 1 to  getNStripChamber       
        int cStrip = strip - nStripTotal;

        return cStrip;
    }

    /**
     * Builds the given strip line in the CLAS12 frame
     *
     * @param sector (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    private Line3d createStrip(int sector, int layer, int strip) {

        int chamberIndex = getChamberIndex(layer, strip);
        double stereoAngle = getStereoAngle(layer,chamberIndex);

        int cStrip = this.getLocalStripId(layer, strip);
        // CHAMBER reference frame
        // new numeration with stri ID_strip=0 crossing (0,0,0) of chamber
        double[] dim = factory.getChamber_daughter_Dimensions(layer, chamberIndex);

        double yHalf = dim[0];
        double xHalfSmallBase = dim[1];
        double xHalfLargeBase = dim[2];
        
         
        // Y coordinate of the intersection point between the x=0 and the strip line crossing for B
        double DY = -yHalf - Math.tan(Math.abs(stereoAngle)) * xHalfSmallBase;

        // ID of the strip 
        int nS = (int) (DY * Math.cos(Math.abs(stereoAngle)) / pitch);
        int nCStrip = nS + (cStrip - 1);



        double m = Math.tan(stereoAngle);
        double c = nCStrip * pitch / Math.cos(stereoAngle);
         
        // Take 2 points in the strip straight line. They needs to define Line object 
        double oX = -xHalfLargeBase;
        double oY = -xHalfLargeBase * m + c;
        double oZ = 0;
        Vector3d origin = new Vector3d(oX, oY, oZ);
       
        double eX = xHalfLargeBase;
        double eY = xHalfLargeBase * m + c;
        double eZ = 0;
        Vector3d end = new Vector3d(eX, eY, eZ);
        
        // Get Chamber Volume
        Geant4Basic chamberVolume = factory.getChamberVolume(sector, chamberIndex + 1, layer);

        // 2 point defined before wrt the GLOBAL frame     
        Vector3d globalOrigin = chamberVolume.getGlobalTransform().transform(origin);

        Vector3d globalEnd = chamberVolume.getGlobalTransform().transform(end);

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
     *
     *
     * @param sector (1-6)
     * @param layer (1-4)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    private Line3d getChamberStrip(int sector, int layer, int strip) {

        Line3d globalStrip = createStrip(sector, layer, strip);
        int chamber = getChamberIndex(layer, strip);
  
        Geant4Basic chamberVolume = factory.getChamberVolume(sector, chamber+1, layer);

        Vector3d origin = chamberVolume.getGlobalTransform().invert().transform(globalStrip.origin());
        Vector3d end = chamberVolume.getGlobalTransform().invert().transform(globalStrip.end());

        Line3d localStrip = new Line3d(origin, end);

        return localStrip;
    }

    /**
     * Provides the given strip line in the sector local frame
     *
     * @param sector (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    private Line3d getLocalStrip(int sector, int layer, int strip) {

        int region = (layer - 1) / 2 + 1;

        Line3d globalStrip = createStrip(sector, layer, strip);
        Geant4Basic sVolume = factory.getSectorVolume(region, sector);

        Vector3d origin = sVolume.getGlobalTransform().invert().transform(globalStrip.origin());
        Vector3d end = sVolume.getGlobalTransform().invert().transform(globalStrip.end());

        Line3d localStrip = new Line3d(origin, end);

        return localStrip;
    }

    private void fillStripLists() {

        for (int ir = 0; ir < nRegions; ir++) {
            int region = ir + 1;
            
            for (int is = 0; is < nSectors; is++) {
                int sector = is + 1;
               
                if (isProto == true) {
                    sector = 6;
                }
                
                for (int il = 0; il < nLayers; il++) {
                    int layer = (2 * region - 1) + il;
                  
                    for (int ic = 0; ic < this.getNStripSector(layer); ic++) {
                        int strip = ic + 1;
                       
                        Line3d line = this.createStrip(sector, layer, strip);
                        
                        Point3D origin = new Point3D(line.origin().x, line.origin().y, line.origin().z);

                        Point3D end = new Point3D(line.end().x, line.end().y, line.end().z);

                        Line3D global = new Line3D(origin, end);
                        Line3D local = this.toLocal(sector, global);
                        
                        this.globalStrips.add(global, sector, layer, strip);
                        this.localStrips.add(local, sector, layer, strip);
                    }
                }
            }
        }
    }

    /**
     * Transform the given strip line to the tilted frame
     *
     * @param sector (1-6)
     * @param global
     * @return the 3D strip line in the tilted frame as a Line3D
     */
    public Line3D toLocal(int sector, Line3D global) {
        Line3D local = new Line3D();
        local.copy(global);

        local.rotateZ(Math.toRadians(-60 * (sector - 1)));
        local.rotateY(Math.toRadians(-URWellConstants.THTILT));

        return local;
    }

    private void fillPlaneLists() {

        for (int ir = 0; ir < nRegions; ir++) {
            int region = ir + 1;
           
            for (int is = 0; is < nSectors; is++) {
                int sector = is + 1;
                if (isProto == true) {
                    sector = 6;
                }
                
                for (int il = 0; il < nLayers; il++) {
                   
                    int layer = (2 * region - 1) + il;
                    
                    for (int ic = 0; ic < this.getNStripSector(layer); ic++) {
                        int strip = ic + 1;

                        Plane3D plane = this.createPLane(sector, layer, strip);
                        this.planeStrips.add(plane, sector, layer, strip);

                    }
                }
            }
        }
    }

    public Plane3D getPlane(int sector, int layer, int strip) {

        return planeStrips.getItem(sector, layer, strip);
    }

    /**
     * Provides the 3D line for the given strip in the CLAS12 frame
     *
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
     *
     * @param sector (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line in the tilted frame as a Line3D
     */
    public Line3D getTiltedStrip(int sector, int layer, int strip) {
        return localStrips.getItem(sector, layer, strip);
    }

    private Plane3D createPLane(int sector, int layer, int strip) {

        int chamber = this.getChamberIndex(layer, strip);

        int LastStripID = this.getNStripChamber(layer, chamber);

        Line3D Last_strip = this.getStrip(sector, layer, LastStripID);

        Line3D First_strip = this.getStrip(sector, layer, 1);

        Line3D test_strip = this.getStrip(sector, layer, 1);

        Vector3D Dir_strip_test = First_strip.originDir();

        /* Line orthogonal to the 2 strip */
        Line3D line = First_strip.distance(Last_strip);

        Vector3D Dir_line = line.originDir();

        Vector3D normal_plane = Dir_strip_test.cross(Dir_line);
        if (isProto == true) {
            normal_plane = Dir_line.cross(Dir_strip_test);
        }

        Plane3D plane = new Plane3D(First_strip.origin(), normal_plane);

        return plane;
    }

    public static void main(String[] args) {
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");

        URWellConstants.connect(cp);

        //    URWellGeant4Factory factory2 = new URWellGeant4Factory(cp, true, 2);
        //           URWellStripFactory factory2 = new URWellStripFactory(cp, false, 1);
        URWellStripFactory factory2 = new URWellStripFactory(cp, 0, 2);

        double angle = factory2.getStereoAngle(0,0);

        int strip = 1418;
        int layer = 2;
        int sector =2;
        
        System.out.println("stereo angle");
        System.out.println(angle * 180 / 3.1415);
        System.out.println("Numero di strip "+factory2.getNStripSector(layer));

        System.out.println("Numero di strip chamber  "+factory2.getNStripChamber(layer,0));
        
        //   Plane3D plane = factory2.getPlane(6, 1, 200);
        //   System.out.println(plane.toString());
 

        System.out.println((strip) + " " + factory2.getLocalStripId(layer,strip) + "\n" + factory2.getChamberStrip(sector, layer, strip) + "\n" + factory2.getLocalStrip(sector, layer, strip) + "\n" + factory2.getStrip(sector, layer, strip))    ;

        /*
         for(int istrip=0; istrip<factory2.getNStripSector(); istrip++)  {
             // System.out.println((istrip+1) + " " + factory2.getChamberIndex(istrip+1) + "\n" + factory2.getStrip(1, 1, istrip+1) + "\n" + factory2.getStrip(1, 2, istrip+1));
             System.out.println((istrip+1) + " " + factory2.getLocalStripId(istrip+1) + "\n" + factory2.getStrip(2, 2, istrip+1) + "\n" + factory2.getLocalStrip(2, 2, istrip+1) );
         */
    }

}
