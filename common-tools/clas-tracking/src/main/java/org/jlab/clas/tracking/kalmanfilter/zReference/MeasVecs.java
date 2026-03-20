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
import org.jlab.geom.prim.Vector3D;

/**
 *
 * @author tongtong cao
 */
public class MeasVecs extends AMeasVecs {
    
    public double[] H(double x, double y, double z, double tx, double ty, Line3D wireLine) {
        double[] hMatrix = new double[5];
        double Err = 0.025;
        double Err_dir = 0.00025;
        double[][] Result = new double[2][4];
        for (int i = 0; i < 2; i++) {
            Point3D point = new Point3D(x + (double) Math.pow(-1, i) * Err, y, z);
            Vector3D dir = new Vector3D(tx, ty, 1);
            Line3D line = new Line3D(point, dir);
            Result[i][0] = hDoca(line, wireLine);
        }
        for (int i = 0; i < 2; i++) {
            Point3D point = new Point3D(x, y + (double) Math.pow(-1, i) * Err, z);
            Vector3D dir = new Vector3D(tx, ty, 1);
            Line3D line = new Line3D(point, dir);
            Result[i][1] = hDoca(line, wireLine);
        }
        for (int i = 0; i < 2; i++) {
            Point3D point = new Point3D(x, y, z);
            Vector3D dir = new Vector3D(tx + (double) Math.pow(-1, i) * Err_dir, ty, 1);
            Line3D line = new Line3D(point, dir);
            Result[i][2] = hDoca(line, wireLine);
        }
        for (int i = 0; i < 2; i++) {
            Point3D point = new Point3D(x, y, z);
            Vector3D dir = new Vector3D(tx, ty + (double) Math.pow(-1, i) * Err_dir, 1);
            Line3D line = new Line3D(point, dir);
            Result[i][3] = hDoca(line, wireLine);
        }        
        
        hMatrix[0] = (Result[0][0] - Result[1][0]) / (2. * Err);
        hMatrix[1] = (Result[0][1] - Result[1][1]) / (2. * Err);
        hMatrix[2] = (Result[0][2] - Result[1][2]) / (2. * Err_dir);
        hMatrix[3] = (Result[0][3] - Result[1][3]) / (2. * Err_dir);
        hMatrix[4] = 0;

        return hMatrix;
    }
    
    public double[] HURWell(StateVec stateVec) {        
        StateVecs sv = new StateVecs();
        double[] hMatrix = new double[5];
        double Err = 0.01;
        double[][] Result = new double[2][2];
        for (int i = 0; i < 2; i++) {
            StateVec svc = sv.new StateVec(stateVec.k);
            svc.copy(stateVec);
            svc.x = stateVec.x + (double) Math.pow(-1, i) * Err;
            Result[i][0] = dhURWell(svc);
        }
        for (int i = 0; i < 2; i++) {
            StateVec svc = sv.new StateVec(stateVec.k);
            svc.copy(stateVec);
            svc.y = stateVec.y + (double) Math.pow(-1, i) * Err;
            Result[i][1] = dhURWell(svc);
        }

        hMatrix[0] = -(Result[0][0] - Result[1][0]) / (2. * Err); // Add negative sign since dh = meas - h; here use dh to replace h since meas is cancelled when derivative
        hMatrix[1] = -(Result[0][1] - Result[1][1]) / (2. * Err); // Add negative sign since dh = meas - h; here use dh to replace h since meas is cancelled when derivative
        hMatrix[2] = 0;
        hMatrix[3] = 0;
        hMatrix[4] = 0;
                
        return hMatrix;
    }    
    
    @Override
    public double[] H(AStateVecs.StateVec stateVec, AStateVecs sv, MeasVec mv, Swim swimmer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
