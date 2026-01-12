package org.jlab.clas.swimtools;

import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

import cnuphys.adaptiveSwim.geometry.Line;
import cnuphys.adaptiveSwim.geometry.Point;
import cnuphys.adaptiveSwim.geometry.Vector;
import cnuphys.adaptiveSwim.geometry.Sphere;
import cnuphys.adaptiveSwim.geometry.Cylinder;

import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.adaptiveSwim.geometry.Plane;

import cnuphys.swim.SwimTrajectory;

public class AdaptiveSwim extends ASwim {

    private static double[] convert(AdaptiveSwimResult result, double p) {

        double[] value = null;

        if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
            value = new double[8];
            value[0] = result.getUf()[0] * 100; // convert back to cm
            value[1] = result.getUf()[1] * 100; // convert back to cm
            value[2] = result.getUf()[2] * 100; // convert back to cm
            value[3] = result.getUf()[3] * p; // normalized values
            value[4] = result.getUf()[4] * p;
            value[5] = result.getUf()[5] * p;
            value[6] = result.getFinalS() * 100;
            value[7] = 0; // Conversion from kG.m to T.cm
        }

        return value;
    }

    @Override
    public double[] SwimRho(double radius, double accuracy) {

        // convert to meters:
        radius = radius/100;

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            PC.AS.swimRho(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, radius, 
                          accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);
            return convert(result, _pTot);
        }
        catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy) {

        // convert to meters:
        radius = radius/100;
        Point a1 = new Point(axisPoint1.x()/100, axisPoint1.y()/100, axisPoint1.z()/100);
        Point a2 = new Point(axisPoint2.x()/100, axisPoint2.y()/100, axisPoint2.z()/100);
        Cylinder targetCylinder = new Cylinder(new Line(a1,a2), radius);

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            PC.AS.swimCylinder(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, targetCylinder,
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);
            return convert(result, _pTot);
        }
        catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }        
        return null;
    }

    @Override
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy) {

        // convert to meters:
        Vector norm = new Vector(n.asUnit().x(), n.asUnit().y(), n.asUnit().z());
        Point point = new Point(p.x()/100, p.y()/100, p.z()/100);
        Plane targetPlane = new Plane(norm, point);

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            PC.AS.swimPlane(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, targetPlane,
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);
            return convert(result, _pTot);
        }
        catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }        
        return null;
    }

    @Override
    public double[] SwimToSphere(double Rad) {

        // convert to meters:
        Sphere targetSphere = new Sphere(new Point(0,0,0), Rad/100);

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            PC.AS.swimSphere(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, targetSphere,
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);
            return convert(result, _pTot);
        }
        catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }        
        return null;
    }

    @Override
    public double[] SwimToLine(Line3D l) {

        // convert to meters:
        Point a1 = new Point(l.origin().x()/100, l.origin().y()/100, l.origin().z()/100);
        Point a2 = new Point(l.end().x()/100, l.end().y()/100, l.end().z()/100);
        Line targetLine = new Line(a1, a2);

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            PC.AS.swimLine(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, targetLine,
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);
            return convert(result, _pTot);
        }
        catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }        
        return null;
    }


}
