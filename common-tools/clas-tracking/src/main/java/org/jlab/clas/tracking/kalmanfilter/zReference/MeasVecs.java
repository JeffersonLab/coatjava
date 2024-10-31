package org.jlab.clas.tracking.kalmanfilter.zReference;

import java.util.ArrayList;
import java.util.List;

import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.tracking.kalmanfilter.AMeasVecs;
import org.jlab.clas.tracking.kalmanfilter.AStateVecs;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.clas.tracking.kalmanfilter.AStateVecs.StateVec;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;

/**
 *
 * @author tongtong cao
 */
public class MeasVecs extends AMeasVecs {
    
    public double[] H(double x, double y, double z, Line3D wireLine) {
        double[] hMatrix = new double[5];
        double Err = 0.025;
        double[][] Result = new double[2][2];
        for (int i = 0; i < 2; i++) {
            Point3D point = new Point3D(x + (double) Math.pow(-1, i) * Err, y, z);
            Result[i][0] = hDoca(point, wireLine);
        }
        for (int i = 0; i < 2; i++) {
            Point3D point = new Point3D(x, y + (double) Math.pow(-1, i) * Err, z);
            Result[i][1] = hDoca(point, wireLine);
        }

        hMatrix[0] = (Result[0][0] - Result[1][0]) / (2. * Err);
        hMatrix[1] = (Result[0][1] - Result[1][1]) / (2. * Err);
        hMatrix[2] = 0;
        hMatrix[3] = 0;
        hMatrix[4] = 0;

        return hMatrix;
    }
    
    public double[] HURWell(double stereo) {
        double[] hMatrix = new double[5];
        hMatrix[0] = Math.cos(Math.toRadians(stereo));
        hMatrix[1] = Math.sin(Math.toRadians(stereo));
        hMatrix[2] = 0;
        hMatrix[3] = 0;
        hMatrix[4] = 0;

        return hMatrix;
    }
    
    public double dhURWell(StateVec stateVec) {
        double value = Double.NaN;
        if (stateVec == null|| this.measurements.get(stateVec.k) == null) {
            return value;
        }
        
        double meas = this.measurements.get(stateVec.k).surface.measPoint.x();
        double stereo = this.measurements.get(stateVec.k).surface.stereo;
        double x = stateVec.x;
        double y = stateVec.y;
        double xx = Math.cos(Math.toRadians(stereo)) * x + Math.sin(Math.toRadians(stereo)) * y;  
        value = meas - xx;
        
        return value;
    }

    @Override
    public double[] H(AStateVecs.StateVec stateVec, AStateVecs sv, MeasVec mv, Swim swimmer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
