package org.jlab.clas.swimtools;

import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

/**
 * 
 * @author baltzell
 */
interface ISwim {

    public double[] SwimToPlaneLab(double z_cm);

    //public double[] SwimToCylinder(double Rad);

    public double[] SwimRho(double radius, double accuracy);

    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy);

    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy);
    
    public double[] SwimToSphere(double Rad);

    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n);

    public double[] SwimToBeamLine(double xB, double yB);

    public double[] SwimToLine(Line3D l);

    public double[] SwimToZ(double Z, int dir);

}
