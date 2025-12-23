package org.jlab.clas.swimtools;

import cnuphys.swim.SwimTrajectory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

public class Swim2 extends SwimPars implements ISwim {

    @Override
    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToPlaneTiltSecSysBdlXZPlane(int sector, double z_cm) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToPlaneLab(double z_cm) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToCylinder(double Rad) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimRho(double radius, double accuracy) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToSphere(double Rad) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToBeamLine(double xB, double yB) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToLine(Line3D l) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] AdaptiveSwimPlane(double px, double py, double pz, double nx, double ny, double nz, double accuracy) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] AdaptiveSwimCylinder(double a1x, double a1y, double a1z, double a2x, double a2y, double a2z, double radius, double accuracy) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] AdaptiveSwimRho(double radius, double accuracy) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToZ(double Z, int dir) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToDCA(SwimTrajectory trk2) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
