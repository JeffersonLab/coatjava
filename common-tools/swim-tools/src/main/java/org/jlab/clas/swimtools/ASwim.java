package org.jlab.clas.swimtools;

import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

/**
 * 
 * @author baltzell
 */
public abstract class ASwim extends SwimPars implements ISwim {

    @Override
    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToPlaneTiltSecSysBdlXZPlane(int sector, double z_cm) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double[] SwimToPlaneLab(double z_cm) {
        return SwimPlane(new Vector3D(0,0,1), new Point3D(0,0,z_cm), accuracy);
    }

    @Override
    public double[] SwimToCylinder(double radius) {
        return SwimGenCylinder(new Point3D(0,0,-1), new Point3D(0,0,1), radius, accuracy);
    }

    @Override
    public double[] SwimToZ(double Z, int dir) {
        return SwimPlane(new Vector3D(0,0,dir*1), new Point3D(0,0,Z), accuracy);
    }

    @Override
    public double[] SwimToBeamLine(double xB, double yB) {
        return SwimToLine(new Line3D(xB,yB,-1,xB,yB,1));
    }

}
