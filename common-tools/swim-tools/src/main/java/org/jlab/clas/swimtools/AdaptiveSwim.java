package org.jlab.clas.swimtools;

import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

import cnuphys.adaptiveSwim.geometry.Line;
import cnuphys.adaptiveSwim.geometry.Point;
import cnuphys.adaptiveSwim.geometry.Vector;

import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;

import cnuphys.swim.SwimTrajectory;

public class AdaptiveSwim extends SwimPars implements ISwim {

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
        return SwimPlane(new Vector3D(0,0,1), new Point3D(0,0,z_cm), accuracy);
    }

    @Override
    public double[] SwimToCylinder(double radius) {
        return SwimGenCylinder(new Point3D(0,0,-1), new Point3D(0,0,1), radius, accuracy);
    }

    @Override
    public double[] SwimRho(double radius, double accuracy) {

        double[] value = new double[8];

        // convert to meters:
        radius = radius/100;

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.AS.swimRho(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, radius, 
                          accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);

            if(result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
            else {
                return null;
            }
        } catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }
        return value;
    }

    @Override
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy) {

        double[] value = new double[8];
       
        // convert to meters:
        radius = radius/100;
        Point a1 = new Point(axisPoint1.x()/100, axisPoint1.y()/100, axisPoint1.z()/100);
        Point a2 = new Point(axisPoint2.x()/100, axisPoint2.y()/100, axisPoint2.z()/100);
        Line centerLine = new Line(a1, a2);

        cnuphys.adaptiveSwim.geometry.Cylinder targetCylinder = new cnuphys.adaptiveSwim.geometry.Cylinder(centerLine, radius);
        
        try {
        
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.AS.swimCylinder(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, targetCylinder,
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);

            if(result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
            else {
                return null;
            }
                    
        } catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }        
        return value;
    }

    @Override
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy) {

        double[] value = new double[8];

        // convert to meters:
        Vector norm = new Vector(n.asUnit().x(), n.asUnit().y(), n.asUnit().z());
        Point point = new Point(p.x()/100, p.y()/100, p.z()/100);
 
        cnuphys.adaptiveSwim.geometry.Plane targetPlane = new cnuphys.adaptiveSwim.geometry.Plane(norm, point);

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.AS.swimPlane(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, targetPlane,
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.getEps(), result);
            
            if(result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
            else {
                return null;
            }
                    
        } catch (AdaptiveSwimException e) {
            e.printStackTrace();
        }        
        return value;
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
    public double[] SwimToZ(double Z, int dir) {
        return SwimPlane(new Vector3D(0,0,dir*1), new Point3D(0,0,Z), accuracy);
    }

    @Override
    public double[] SwimToDCA(SwimTrajectory trk2) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
