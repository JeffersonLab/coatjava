package org.jlab.clas.swimtools;

import cnuphys.swim.SwimTrajectory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

/**
 * FIXME:  Can any of these be removed?
 * 
 * @author baltzell
 */
interface ISwim {

    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm);
    
    public double[] SwimToPlaneTiltSecSysBdlXZPlane(int sector, double z_cm);
    
    public double[] SwimToPlaneLab(double z_cm);

    public double[] SwimToCylinder(double Rad);

    public double[] SwimRho(double radius, double accuracy);

    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy);

    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy);
    
    public double[] SwimToSphere(double Rad);

    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir);

    public double[] SwimToBeamLine(double xB, double yB);

    public double[] SwimToLine(Line3D l);

    public double[] SwimToZ(double Z, int dir);

    public double[] SwimToDCA(SwimTrajectory trk2);

}
