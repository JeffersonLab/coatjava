package org.jlab.rec.muvt.track.fit;

import java.util.ArrayList;
import java.util.List;
import org.jlab.rec.muvt.track.fit.StateVecs.StateVec;

import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.rec.muvt.MUVTCluster;

/**
 * @author ziegler
 */

public class MeasVecs {

    public List<MeasVec> measurements ;

    public class MeasVec implements Comparable<MeasVec> {
        public double z = Double.NaN;
        public Point3D lineEndPoint1 = null;
        public Point3D lineEndPoint2 = null;
        public double seed;
        public double error;
        public int layer;
        public int k;
        public int size;

        MeasVec() {}

        @Override
        public int compareTo(MeasVec arg) {
            int CompLay = this.layer < arg.layer ? -1 : this.layer == arg.layer ? 0 : 1;
            return CompLay;
        }
    }

    public void setMeasVecs(List<MUVTCluster> clusters) {
        measurements = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            int l = clusters.get(i).getLayer()-1;
            Point3D lineEndPoint1  = clusters.get(i).getLineTS().origin();
            Point3D lineEndPoint2  = clusters.get(i).getLineTS().end();
            double error = 0.0144; // = pitch/sqrt(12), where pitch is 500 um
            double z     = lineEndPoint1.z();
            int seed = clusters.get(i).getMaxStrip();
            MeasVec meas = this.setMeasVec(l, lineEndPoint1, lineEndPoint2, error, z, seed);
            measurements.add(meas);
        }
    }

    public MeasVec setMeasVec(int l, Point3D lineEndPoint1, Point3D lineEndPoint2, double error, double z, int seed) {

        MeasVec meas     = new MeasVec();
        meas.layer       = l+1;
        meas.error       = error;
        meas.z           = z; 
        meas.seed        = seed;

        meas.lineEndPoint1 = lineEndPoint1;
        meas.lineEndPoint2 = lineEndPoint2;

        return meas;
    }

    public double dhMUVT(StateVec stateVec) {
        double value = Double.NaN;
        if (stateVec == null|| this.measurements.get(stateVec.k) == null) {
            return value;
        }
                       
        Line3D l = new Line3D(this.measurements.get(stateVec.k).lineEndPoint1, 
        this.measurements.get(stateVec.k).lineEndPoint2);
        Line3D WL = new Line3D();
        WL.copy(l);
        Point3D svP = new Point3D(stateVec.x, stateVec.y, stateVec.z);
        WL.copy(WL.distance(svP));
        Plane3D plane = new Plane3D(0, 0, this.measurements.get(stateVec.k).z, 0, 0, 1); // plan perpenticular to z axis in TSC
        double sideStrip = -Math.signum(l.direction().cross(WL.direction()).
                dot(plane.normal())); 
        value = WL.length()*sideStrip;
                
        return value;
    }
    
    public double[] HMUVT(StateVec stateVec, StateVecs sv) {        
        double[] hMatrix = new double[5];
        double Err = 0.01;
        double[][] Result = new double[2][2];
        for (int i = 0; i < 2; i++) {
            StateVec svc = sv.new StateVec(stateVec.k);
            svc.x = stateVec.x;
            svc.y = stateVec.y;
            svc.z = stateVec.z;
            svc.x = stateVec.x + (double) Math.pow(-1, i) * Err;
            Result[i][0] = dhMUVT(svc);
        }
        for (int i = 0; i < 2; i++) {
            StateVec svc = sv.new StateVec(stateVec.k);
            svc.x = stateVec.x;
            svc.y = stateVec.y;
            svc.z = stateVec.z;
            svc.y = stateVec.y + (double) Math.pow(-1, i) * Err;
            Result[i][1] = dhMUVT(svc);
        }

        hMatrix[0] = -(Result[0][0] - Result[1][0]) / (2. * Err); // Add negative sign since dh = meas - h; here use dh to replace h since meas is cancelled when derivative
        hMatrix[1] = -(Result[0][1] - Result[1][1]) / (2. * Err); // Add negative sign since dh = meas - h; here use dh to replace h since meas is cancelled when derivative
        hMatrix[2] = 0;
        hMatrix[3] = 0;
        hMatrix[4] = 0;
                
        return hMatrix;
    }     

    private StateVec reset(StateVec SVplus, StateVec stateVec, StateVecs sv) {
        SVplus    = sv.new StateVec(stateVec.k);
        SVplus.x  = stateVec.x;
        SVplus.y  = stateVec.y;
        SVplus.z  = stateVec.z;
        SVplus.tx = stateVec.tx;
        SVplus.ty = stateVec.ty;
        SVplus.Q  = stateVec.Q;

        return SVplus;
    }
}
