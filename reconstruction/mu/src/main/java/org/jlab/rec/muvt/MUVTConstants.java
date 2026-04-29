package org.jlab.rec.muvt;

import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.fmt.FMTLayer;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Transformation3D;
import org.jlab.geom.prim.Vector3D;

/**
 *
 * @author devita
 */
public class MUVTConstants {
    
    public final static int NSECTOR  = 6;
    public final static int NLAYER   = 12;
    public final static int NREGION  = 6;
    public final static int NCHAMBER = 1;
    public final static double PITCH = 0.5; // mm
    public final static double TILT  = 25; // deg
    public final static double[] STEREO = { 10.0, 10.0 };

    // strips
    public final static double THRESHOLD = 0;
    public final static double ADCTOENERGY = 25/1E4; // in eV, values from gemc ADC = (uRwellC.gain*1e6*tInfos.eTot/uRwellC.w_i); with gain = 10^4 and w_i = 25 eV
    public final static double TDCTOTIME = 1;

    // cluster
    public final static double COINCTIME = 100;
    
    // rMax difference between cross's clusters energy
    public static double CROSSDELTAE = 2000;

    // DC-tracks to FMT-clusters matching parameter
    public static double CIRCLECONFUSION = 12; // cm

    // min path for final swimming to beamline to reject failed swimming
    public static double MIN_SWIM_PATH = 0.2; 
    
    // small distance (cm) for derivatives calculations
    public static double EPSILON = 1e-4;
    
    public static int MAX_NB_CROSSES = 30;
    
//    private static Detector fmtDetector = null;

    
    
//    public static void setDetector(Detector detector) {
//        MUVTConstants.fmtDetector = detector;
//    }
    
//    public static Vector3D getDerivatives(int layer, double x, double y, double z) {
//        Vector3D p0 = new Vector3D(x,y,z);
//        Vector3D p1 = new Vector3D(x,y+MUVTConstants.EPSILON,z);
//        MUVTConstants.getInverseTransform(layer).apply(p0);
//        MUVTConstants.getInverseTransform(layer).apply(p1);
//        return p1.sub(p0).divide(MUVTConstants.EPSILON);
//    }
    
//    public static FMTLayer getLayer(int layer) {
//        if(layer<1 || layer>NLAYER)
//            throw new IllegalArgumentException("Error: invalid layer="+layer);
//        return (FMTLayer) MUVTConstants.fmtDetector.getSector(0).getSuperlayer(0).getLayer(layer-1);
//    }
//    
//    public static double getPitch() {
//        return MUVTConstants.getLayer(1).getComponent(0).getWidth();
//    }
//    
//    public static Line3D getStrip(int layer, int strip) {
//        if(strip<1 || strip>MUVTConstants.getLayer(layer).getNumComponents())
//            throw new IllegalArgumentException("Error: invalid strip="+strip);
//        return MUVTConstants.getLayer(layer).getComponent(strip-1).getLine();
//    }
    
//    public static Line3D getLocalStrip(int layer, int strip) {
//        Line3D local = new Line3D(MUVTConstants.getStrip(layer, strip));
//        MUVTConstants.getInverseTransform(layer).apply(local);
//        return local;
//    }
//    
//    public static double getThickness() {
//        return MUVTConstants.getLayer(1).getComponent(0).getThickness();
//    }
    
    public static Transformation3D toTiltedSectorFrame(int sector) {
        Transformation3D t = new Transformation3D();
        t.rotateZ(-2*Math.PI/NSECTOR * (sector-1));
        t.rotateY(Math.toRadians(-TILT));
        return t;
    }

    public static Transformation3D toLab(int sector) {
        Transformation3D inverse = new Transformation3D(MUVTConstants.toTiltedSectorFrame(sector));
        return inverse.inverse();
    }


    public static Point3D toLab(int sector, double x, double y, double z) {
        Point3D local = new Point3D(x,y,z);
        MUVTConstants.toLab(sector).apply(local);
        return local;
    }

    public static Point3D toTiltedSectorFrame(int sector, Point3D p) {
        Point3D local = new Point3D(p);
        MUVTConstants.toTiltedSectorFrame(sector).apply(local);
        return local;
    }

    public static Point3D toTiltedSectorFrame(int sector, double x, double y, double z) {
        Point3D local = new Point3D(x,y,z);
        MUVTConstants.toTiltedSectorFrame(sector).apply(local);
        return local;
    }

    public static Line3D toTiltedSectorFrame(int sector, Line3D l) {
        Line3D local = new Line3D(l);
        MUVTConstants.toTiltedSectorFrame(sector).apply(local);
        return local;
    }

}
