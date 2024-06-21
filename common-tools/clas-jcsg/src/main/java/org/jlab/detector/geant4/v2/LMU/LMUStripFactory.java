package org.jlab.detector.geant4.v2.LMU;


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
public final class LMUStripFactory {

    private LMUGeant4Factory factory;
    private IndexedList<Line3D>  globalStrips = new IndexedList(3);
    private IndexedList<Line3D>  localStrips  = new IndexedList(3);
    private IndexedList<Plane3D> planeStrips  = new IndexedList(3);
    private int nRegions;
    private int nLayers;
    
    
    public LMUStripFactory() {
    }
    
    /**
     * Create the strip factory based on constants from CCDB.
     * Currently constants are defined in the LDRDConstants class. 
     * They will be moved to CCDB when finalized).
     * @param cp database provide
     */
    public LMUStripFactory(DatabaseConstantProvider cp) {
        this.init(cp);
    }
    
    /**
     * Initialize the factory by the strip maps
     * @param cp
     */
    public void init(DatabaseConstantProvider cp) {
        factory = new LMUGeant4Factory(cp);
        nRegions  = LMUConstants.NREGIONS;
        nLayers   = LMUConstants.NLAYERS;
        this.fillStripLists();
        this.fillPlaneLists();
    }

    

        
    /**
     * Builds the given strip line in the global frame
     * @param sector (1-6)
     * @param layer (1-2) or (1-2-3)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    private Line3d createStrip(int region, int layer, int strip) {

       
        // CHAMBER reference frame
        // new numeration with stri ID_strip=0 crossing (0,0,0) of chamber
        double[] dim = factory.getChamberDimensions();
        
        double dimx = dim[0];
        double dimy = dim[1];
        double dimz = dim[2];
        
        Vector3d norm = new Vector3d(0, 0, 1);
        Vector3d stripdir = new Vector3d(1, 0, 0);
        Vector3d midline = new Vector3d(0, 1, 0);
        
        stripdir.rotate(norm, LMUConstants.STEREOANGLE[layer-1]);
        midline.rotate(norm, LMUConstants.STEREOANGLE[layer-1]);

        double stripmid = -dimy +2*dimy/LMUConstants.PITCH*(strip-0.5);
        
        // Take 2 points in the strip straight line. They needs to define Line object 
        Vector3d origin = midline.times(stripmid).add(stripdir.times(dimx));
        Vector3d end = midline.times(stripmid).add(stripdir.times(-dimx));

        // Get Chamber Volume
        Geant4Basic chamberVolume = factory.getChamberVolume(region);
            
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
     * @param layer (1-4)
     * @param strip (1-N)
     * @return the 3D strip line as a Line3d
     */
    
    private Line3d toLocal(int region, Line3d strip) {

         
        Geant4Basic chamberVolume = factory.getChamberVolume(region);
        Vector3d origin = chamberVolume.getGlobalTransform().invert().transform(strip.origin());
        Vector3d end    = chamberVolume.getGlobalTransform().invert().transform(strip.end());

        Line3d localStrip = new Line3d(origin, end);

       
        return localStrip;
    }

    private int getNStrips(int region, int layer) {
        return ((int) (2*factory.getChamberDimensions()[layer-1]/LMUConstants.PITCH));
    }
    
    private void fillStripLists() {
        
        for(int ir=0; ir<nRegions; ir++) {
            int region = ir+1;
            for(int il=0; il<nLayers; il++) {
                    int layer = (2*region-1) + il;

                    for(int ic=0; ic<this.getNStrips(region, layer); ic++) {
                         int strip = ic+1;
                       
                        Line3d global = this.createStrip(region, layer, strip);
                        Point3D globalO = new Point3D(global.origin().x, global.origin().y, global.origin().z);
                        Point3D globalE    = new Point3D(global.end().x,    global.end().y,    global.end().z);

                        Line3d local = this.toLocal(region, global);
                        Point3D localO = new Point3D(local.origin().x, local.origin().y, local.origin().z);
                        Point3D localE    = new Point3D(local.end().x,    local.end().y,    local.end().z);
                    
                        this.globalStrips.add(new Line3D(globalO, globalE), region, layer, strip);
                        this.localStrips.add(new Line3D(localO, localE), region, layer, strip);
                    }
            }
        }
    }
    

    
    private void fillPlaneLists() {

        for(int ir=0; ir<nRegions; ir++) {
            int region = ir+1;
                for(int il=0; il<nLayers; il++) {
                    int layer = il=1;
                    Plane3D plane = this.createPLane(region, layer);
                    this.planeStrips.add(plane, region, layer, 0);
                }
        }
    }
        
    
    public Plane3D getPlane(int region, int layer){
        
        return planeStrips.getItem(region, layer, 0);
    }
    
    
    /**
     * Provides the 3D line for the given strip in the CLAS12 frame
     * @param region (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line in the CLAS12 frame as a Line3D
     */
    public Line3D getStrip(int region, int layer, int strip) {

        return globalStrips.getItem(region, layer, strip);
    }
    
    /**
     * Provides the 3D line for the given strip in the CLAS12 frame
     * @param region (1-6)
     * @param layer (1-2)
     * @param strip (1-N)
     * @return the 3D strip line in the CLAS12 frame as a Line3D
     */
    public Line3D getLocalStrip(int region, int layer, int strip) {

        return localStrips.getItem(region, layer, strip);
    }
    


    
    private Plane3D createPLane(int region, int layer){
     
        int nStrip = this.getNStrips(region, layer);
        Line3D lastStrip = this.getStrip(region, layer, nStrip);
        Line3D firstStrip = this.getStrip(region, layer, 1);

        
        /* Line orthogonal to the 2 strip */
        Line3D line = firstStrip.distance(lastStrip);
       
        Vector3D planeNorm = firstStrip.originDir().cross(line.originDir());
        
        Plane3D plane = new Plane3D(firstStrip.origin(), planeNorm);

        return plane;
    }
    
    
    public static void main(String[] args) {
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");

        LMUConstants.connect(cp);

        LMUGeant4Factory factory = new LMUGeant4Factory(cp);
        LMUStripFactory factory2 = new LMUStripFactory(cp);
  
        Plane3D plane = factory2.getPlane(6, 1);
        System.out.println(plane.toString());

       int strip =20;
        
        
    // for(int istrip=0; istrip<factory2.getNStripSector(); istrip++)  {
        //    System.out.println((istrip+1) + " " + factory2.getChamberIndex(istrip+1) + "\n" + factory2.getStrip(1, 1, istrip+1) + "\n" + factory2.getStrip(1, 2, istrip+1));
       // }
        
        
    }

}
