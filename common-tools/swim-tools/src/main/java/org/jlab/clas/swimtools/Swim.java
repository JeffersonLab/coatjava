package org.jlab.clas.swimtools;

import org.apache.commons.math3.util.FastMath;

import org.jlab.geom.prim.Vector3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Line3D;

import cnuphys.rk4.RungeKuttaException;

import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.util.Plane;

import cnuphys.swimZ.SwimZException;
import cnuphys.swimZ.SwimZResult;
import cnuphys.swimZ.SwimZStateVector;

import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.adaptiveSwim.geometry.Line;
import cnuphys.adaptiveSwim.geometry.Point;
import cnuphys.adaptiveSwim.geometry.Vector;
import org.jlab.clas.swimtools.Stoppers.BeamLineSwimStopper;

import org.jlab.clas.swimtools.Stoppers.CylindricalBoundarySwimStopper;
import org.jlab.clas.swimtools.Stoppers.DCASwimStopper;
import org.jlab.clas.swimtools.Stoppers.LineSwimStopper;
import org.jlab.clas.swimtools.Stoppers.SphericalBoundarySwimStopper;
import org.jlab.clas.swimtools.Stoppers.ZSwimStopper;

/**
 *
 * @author ziegler
 */
public class Swim {

    final double SWIMZMINMOM = 0.75; // GeV/c
    final double MINTRKMOM = 0.05; // GeV/c

    private double _x0;
    private double _y0;
    private double _z0;
    private double _phi;
    private double _theta;
    private double _pTot;
    private final double _rMax = 5 + 3;
    private double _maxPathLength = 9;
    private boolean SwimUnPhys = false; //Flag to indicate if track is swimmable
    private int _charge;
    double accuracy = 20e-6; // 20 microns
    public double stepSize = 5.00 * 1.e-4; // 500 microns
    public double distanceBetweenSaves= 100*stepSize;
    
    private ProbeCollection PC;
    
    /**
     * Class for swimming to various surfaces.  The input and output units are cm and GeV/c
     */
    public Swim() {
        PC = Swimmer.getProbeCollection(Thread.currentThread());
        if (PC == null) {
            PC = new ProbeCollection();
            Swimmer.put(Thread.currentThread(), PC);
        }
    }

    /**
     * Set max swimming path length
     *
     * @param _maxPathLength
     */
    public void setMaxPathLength(double _maxPathLength) {
        this._maxPathLength = _maxPathLength;
    }

    /**
     *
     * @param direction +1 for out -1 for in
     * @param x0 (cm)
     * @param y0 (cm)
     * @param z0 (cm)
     * @param thx (units?)
     * @param thy (units?)
     * @param p (units?)
     * @param charge
     */
    public void SetSwimParameters(int direction, double x0, double y0, double z0,
                                  double thx, double thy, double p, int charge) {
        _x0 = x0 / 100; // convert to meters
        _y0 = y0 / 100;
        _z0 = z0 / 100;
        this.checkR(_x0, _y0, _z0);
        double pz = direction * p / Math.sqrt(thx * thx + thy * thy + 1);
        double px = thx * pz;
        double py = thy * pz;
        _phi = Math.toDegrees(FastMath.atan2(py, px));
        _pTot = Math.sqrt(px * px + py * py + pz * pz);
        _theta = Math.toDegrees(Math.acos(pz / _pTot));
        _charge = direction * charge;
    }

    /**
     * Sets the parameters used by swimmer based on the input track state vector
     * parameters swimming outwards
     *
     * // z at a given DC plane in the tilted coordinate system
     *
     * @param superlayerIdx
     * @param layerIdx
     * @param x0 (cm)
     * @param y0 (cm)
     * @param z0 (cm)
     * @param thx
     * @param thy
     * @param p
     * @param charge
     */
    public void SetSwimParameters(int superlayerIdx, int layerIdx,
                                  double x0, double y0, double z0,
                                  double thx, double thy, double p, int charge) {
        _x0 = x0 / 100; // convert to meters
        _y0 = y0 / 100;
        _z0 = z0 / 100;
        this.checkR(_x0, _y0, _z0);
        double pz = p / Math.sqrt(thx * thx + thy * thy + 1);
        double px = thx * pz;
        double py = thy * pz;
        _phi = Math.toDegrees(FastMath.atan2(py, px));
        _pTot = Math.sqrt(px * px + py * py + pz * pz);
        _theta = Math.toDegrees(Math.acos(pz / _pTot));
        _charge = charge;
    }

    /**
     * Sets the parameters used by swimmer based on the input track parameters
     *
     * @param x0 (cm)
     * @param y0 (cm)
     * @param z0 (cm)
     * @param px
     * @param py
     * @param pz
     * @param charge
     */
    public void SetSwimParameters(double x0, double y0, double z0,
                                  double px, double py, double pz, int charge) {
        _x0 = x0 / 100; // convert to meters
        _y0 = y0 / 100;
        _z0 = z0 / 100;
         this.checkR(_x0, _y0, _z0);
        _phi = Math.toDegrees(FastMath.atan2(py, px));
        _pTot = Math.sqrt(px * px + py * py + pz * pz);
        _theta = Math.toDegrees(Math.acos(pz / _pTot));
        _charge = charge;
    }

    /**
     * 
     * @param xcm
     * @param ycm
     * @param zcm
     * @param phiDeg
     * @param thetaDeg
     * @param p
     * @param charge
     * @param maxPathLength
     */
    public void SetSwimParameters(double xcm, double ycm, double zcm,
                                  double phiDeg, double thetaDeg,
                                  double p, int charge, double maxPathLength) {
        _maxPathLength = maxPathLength;
        _charge = charge;
        _phi = phiDeg;
        _theta = thetaDeg;
        _pTot = p;
        _x0 = xcm / 100;
        _y0 = ycm / 100;
        _z0 = zcm / 100;
        this.checkR(_x0, _y0, _z0);
    }

    /**
     * 
     * @param xcm
     * @param ycm
     * @param zcm
     * @param phiDeg
     * @param thetaDeg
     * @param p
     * @param charge
     * @param maxPathLength
     * @param Accuracy
     * @param StepSize
     */
    public void SetSwimParameters(double xcm, double ycm, double zcm,
                                  double phiDeg, double thetaDeg,
                                  double p, int charge,
                                  double maxPathLength, double Accuracy, double StepSize) {
        _maxPathLength = maxPathLength;
         accuracy = Accuracy/100;
         stepSize = StepSize/100;
        _charge = charge;
        _phi = phiDeg;
        _theta = thetaDeg;
        _pTot = p;
        _x0 = xcm / 100;
        _y0 = ycm / 100;
        _z0 = zcm / 100;
        this.checkR(_x0, _y0, _z0);
    }

    /**
     * 
     * @param sector
     * @param z_cm
     * @return 
     */
    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {

        // Fiducial Cut:
        if (_pTot < MINTRKMOM || this.SwimUnPhys==true) return null;

        double[] value = new double[8];
        double hdata[] = new double[3];

        try {

            // Try to use new Z-Swimmer: 
            SwimZResult szr = null;
            if (_pTot > SWIMZMINMOM) {
                double stepSizeCM = stepSize * 100; // convert to cm
                SwimZStateVector start = new SwimZStateVector(_x0 * 100, _y0 * 100, _z0 * 100, _pTot, _theta, _phi);
                try {
                    szr = PC.RCF_z.sectorAdaptiveRK(sector, _charge, _pTot, start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                    szr = null;
                }
            }
            if (szr != null) {
                double bdl = szr.sectorGetBDL(sector, PC.RCF_z.getProbe());
                double pathLength = szr.getPathLength(); // already in cm
                SwimZStateVector last = szr.last();
                double p3[] = szr.getThreeMomentum(last);
                value[0] = last.x; // cm
                value[1] = last.y; // cm
                value[2] = last.z; // cm
                value[3] = p3[0];
                value[4] = p3[1];
                value[5] = p3[2];
                value[6] = pathLength;
                value[7] = bdl / 10; // convert from kg*cm to T*cm
            }
            
            // Use older swimmer:
            else {
                final double z = z_cm / 100; // convert to meters
                SwimTrajectory traj = PC.RCF.sectorSwim(sector, _charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax,
                                _maxPathLength, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
                if(traj==null) return null;
                traj.sectorComputeBDL(sector, PC.RCP);
                double lastY[] = traj.lastElement();
                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * _pTot;
                value[4] = lastY[4] * _pTot;
                value[5] = lastY[5] * _pTot;
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;

    }

    /**
     * 
     * @param sector
     * @param z_cm
     * @return 
     */
    public double[] SwimToPlaneTiltSecSysBdlXZPlane(int sector, double z_cm) {

        // Fiducial Cut:
        if (_pTot < MINTRKMOM || this.SwimUnPhys==true) return null;

        double hdata[] = new double[3];
        double[] value = new double[8];

        try {

            // Try to use new Z-Swimmer: 
            SwimZResult szr = null;
            if (_pTot > SWIMZMINMOM) {
                double stepSizeCM = stepSize * 100; // convert to cm
                SwimZStateVector start = new SwimZStateVector(_x0 * 100, _y0 * 100, _z0 * 100, _pTot, _theta, _phi);
                try {
                    szr = PC.RCF_z.sectorAdaptiveRK(sector, _charge, _pTot, start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                    szr = null;
                }
            }
            if (szr != null) {
                double bdl = szr.sectorGetBDLXZPlane(sector, PC.RCF_z.getProbe());
                double pathLength = szr.getPathLength(); // already in cm
                SwimZStateVector last = szr.last();
                double p3[] = szr.getThreeMomentum(last);
                value[0] = last.x; // xf in cm
                value[1] = last.y; // yz in cm
                value[2] = last.z; // zf in cm
                value[3] = p3[0];
                value[4] = p3[1];
                value[5] = p3[2];
                value[6] = pathLength;
                value[7] = bdl / 10; // convert from kg*cm to T*cm
            }
           
            // Use older swimmer:
            else {
                double z = z_cm / 100; // convert to meters
                SwimTrajectory traj = PC.RCF.sectorSwim(sector, _charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax,
                                _maxPathLength, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
                if (traj==null) return null;
                traj.sectorComputeBDL(sector, PC.RCP);
                double lastY[] = traj.lastElement();
                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * _pTot;
                value[4] = lastY[4] * _pTot;
                value[5] = lastY[5] * _pTot;
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }
        
    /**
     * 
     * @param z_cm
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the plane surface
     */
    public double[] SwimToPlaneLab(double z_cm) {

        // Fiducial Cut:
        if (_pTot < MINTRKMOM || this.SwimUnPhys==true) return null;

        double hdata[] = new double[3];
        double[] value = new double[8];
        
        try {

            // Try to use new Z-Swimmer: 
            SwimZResult szr = null;
            if (_pTot > SWIMZMINMOM) {
                double stepSizeCM = stepSize * 100; // convert to cm
                SwimZStateVector start = new SwimZStateVector(_x0 * 100, _y0 * 100, _z0 * 100, _pTot, _theta, _phi);
                try {
                    szr = PC.CF_z.adaptiveRK(_charge, _pTot, start, z_cm, stepSizeCM, hdata);
                } catch (SwimZException e) {
                    szr = null;
                }
            }
            if (szr != null) {
                double bdl = szr.getBDL(PC.CF_z.getProbe());
                double pathLength = szr.getPathLength(); // already in cm
                SwimZStateVector last = szr.last();
                double p3[] = szr.getThreeMomentum(last);
                value[0] = last.x; // xf in cm
                value[1] = last.y; // yz in cm
                value[2] = last.z; // zf in cm
                value[3] = p3[0];
                value[4] = p3[1];
                value[5] = p3[2];
                value[6] = pathLength;
                value[7] = bdl / 10; // convert from kg*cm to T*cm
            }
            
            // Use older swimmer:
            else {
                double z = z_cm / 100; // the magfield method uses meters
                SwimTrajectory traj = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, z, accuracy, _rMax, _maxPathLength,
                                stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);
                if (traj==null) return null;
                traj.computeBDL(PC.CP);
                double lastY[] = traj.lastElement();
                value[0] = lastY[0] * 100; // convert back to cm
                value[1] = lastY[1] * 100; // convert back to cm
                value[2] = lastY[2] * 100; // convert back to cm
                value[3] = lastY[3] * _pTot;
                value[4] = lastY[4] * _pTot;
                value[5] = lastY[5] * _pTot;
                value[6] = lastY[6] * 100;
                value[7] = lastY[7] * 10;
            } // old swimmer

        } catch (RungeKuttaException e) {
            e.printStackTrace();
        }
        return value;
    }

    /**
     * 
     * @param _x0
     * @param _y0
     * @param _z0 
     */
    private void checkR(double _x0, double _y0, double _z0) {
        this.SwimUnPhys=false;
        if(Math.sqrt(_x0*_x0 + _y0*_y0)>this._rMax || 
                Math.sqrt(_x0*_x0 + _y0*_y0 + _z0*_z0)>this._maxPathLength)
            this.SwimUnPhys=true;
    }

    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToCylinder(double Rad) {
        
        if (this.SwimUnPhys) return null;
        double[] value = new double[8];
        
        CylindricalBoundarySwimStopper stopper = new CylindricalBoundarySwimStopper(Rad);
        
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    /**
     * 
     * @param radius in cm
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimRho(double radius)  {
        return SwimRho(radius, accuracy*100);
    }
    
    /**
     * 
     * @param radius   in cm
     * @param accuracy in cm 
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimRho(double radius, double accuracy)  {

        if(this.SwimUnPhys) return null;
        double[] value = null;

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimRho(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, radius/100, accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, result);

            if(result.getStatus()==0) {
                value = new double[8];   
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
        } catch (RungeKuttaException e) {
            System.out.println(_charge + " " + _x0 + " " + _y0 + " " + _z0 + " " + _pTot + " " + _theta + " " + _phi);
            e.printStackTrace();
        }
        return value;
    }
    
    /**
     * 
     * @param axisPoint1 in cm
     * @param axisPoint2 in cm 
     * @param radius in cm 
     * @return swam trajectory to the cylinder
     */
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius)  {
        return SwimGenCylinder(axisPoint1, axisPoint2, radius, accuracy*100);
    }
    
    /**
     * 
     * @param axisPoint1 in cm
     * @param axisPoint2 in cm 
     * @param radius in cm 
     * @param accuracy in cm
     * @return swam trajectory to the cylinder
     */
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy)  {

        if(this.SwimUnPhys) return null;

        double[] value = null;
        double[] p1 = new double[3];
        double[] p2 = new double[3];
        p1[0] = axisPoint1.x()/100;
        p1[1] = axisPoint1.y()/100;
        p1[2] = axisPoint1.z()/100;
        p2[0] = axisPoint2.x()/100;
        p2[1] = axisPoint2.y()/100;
        p2[2] = axisPoint2.z()/100;
        
        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimCylinder(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, 
                    p1, p2, radius/100, accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, result);
            
            if(result.getStatus()==0) {
                value = new double[8];            
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
        } catch (RungeKuttaException e) {
            System.out.println(_charge + " " + _x0 + " " + _y0 + " " + _z0 + " " + _pTot + " " + _theta + " " + _phi);
            e.printStackTrace();
        }
        return value;

    }

    /**
     * 
     * @param n
     * @param p
     * @param accuracy
     * @return 
     */
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy)  {

        if (this.SwimUnPhys) return null;
        
        double[] value = null;

        try {
            AdaptiveSwimResult result = new AdaptiveSwimResult(false);
            
            PC.CF.swimPlane(_charge, _x0, _y0, _z0, _pTot, _theta, _phi, 
                            n.x(),n.y(),n.z(),p.x()/100,p.y()/100,p.z()/100, 
                            accuracy/100, _rMax, stepSize, cnuphys.swim.Swimmer.CLAS_Tolerance, result);
            
            if(result.getStatus()==0) {
                value = new double[8];   
                value[0] = result.getUf()[0] * 100; // convert back to cm
                value[1] = result.getUf()[1] * 100; // convert back to cm
                value[2] = result.getUf()[2] * 100; // convert back to cm
                value[3] = result.getUf()[3] * _pTot; // normalized values
                value[4] = result.getUf()[4] * _pTot;
                value[5] = result.getUf()[5] * _pTot;
                value[6] = result.getFinalS() * 100;
                value[7] = 0; // Conversion from kG.m to T.cm
            }
        } catch (RungeKuttaException e) {
            System.out.println(_charge + " " + _x0 + " " + _y0 + " " + _z0 + " " + _pTot + " " + _theta + " " + _phi);
            e.printStackTrace();
        }
        return value;
    }
    
    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToSphere(double Rad) {

        if (this.SwimUnPhys==true) return null;
        double[] value = new double[8];
        
        SphericalBoundarySwimStopper stopper = new SphericalBoundarySwimStopper(Rad);
            
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    /**
     * 
     * @param d_cm
     * @param n
     * @param dir
     * @return return state  x,y,z,px,py,pz, pathlength, iBdl at the plane surface in the lab frame
     */
    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir) {

        if (this.SwimUnPhys) return null;

        double[] value = new double[8];
        double hdata[] = new double[3];
        double d = d_cm / 100; // convert to meters

        Plane plane = new Plane(n.x(), n.y(), n.z(), d);
        try {

            SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
                plane, accuracy, _maxPathLength, stepSize,
                cnuphys.swim.Swimmer.CLAS_Tolerance, hdata);

            st.computeBDL(PC.CP);

            double[] lastY = st.lastElement();
            
            value[0] = lastY[0] * 100; // convert back to cm
            value[1] = lastY[1] * 100; // convert back to cm
            value[2] = lastY[2] * 100; // convert back to cm
            value[3] = lastY[3] * _pTot; // normalized values
            value[4] = lastY[4] * _pTot;
            value[5] = lastY[5] * _pTot;
            value[6] = lastY[6] * 100;
            value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm

        } catch (RungeKuttaException e) {
            e.printStackTrace();
        }
        return value;
    }

    public double[] SwimToBeamLine(double xB, double yB) {

        if(this.SwimUnPhys==true) return null;
        
        double[] value = new double[8];
        
        BeamLineSwimStopper stopper = new BeamLineSwimStopper(xB, yB);

        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }
   
    public double[] SwimToLine(Line3D l) {
        
        if (this.SwimUnPhys==true) return null;

        double[] value = new double[8];
        
        LineSwimStopper stopper = new LineSwimStopper(l);

        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
        st.computeBDL(PC.CP);

        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    private void printV(String pfx, double v[]) {
        double x = v[0] / 100;
        double y = v[1] / 100;
        double z = v[2] / 100;
        double r = Math.sqrt(x * x + y * y + z * z);
        System.out.println(String.format("%s: (%-8.5f, %-8.5f, %-8.5f) R: %-8.5f", pfx, z, y, z, r));
    }

    /**
     * 
     * @param sector
     * @param x_cm
     * @param y_cm
     * @param z_cm
     * @param result B field components in T in the tilted sector system
     */
    public void Bfield(int sector, double x_cm, double y_cm, double z_cm, float[] result) {
        PC.RCP.field(sector, (float) x_cm, (float) y_cm, (float) z_cm, result);
        result[0] = result[0] / 10;
        result[1] = result[1] / 10;
        result[2] = result[2] / 10;
    }

    /**
     * 
     * @param x_cm
     * @param y_cm
     * @param z_cm
     * @param result B field components in T in the lab frame
     */
    public void BfieldLab(double x_cm, double y_cm, double z_cm, float[] result) {
        PC.CP.field((float) x_cm, (float) y_cm, (float) z_cm, result);
        result[0] = result[0] / 10;
        result[1] = result[1] / 10;
        result[2] = result[2] / 10;
    }

    public double[] AdaptiveSwimPlane(double px, double py, double pz, double nx, double ny, double nz, double accuracy)  {

        if (this.SwimUnPhys) return null;

        double[] value = new double[8];
        
        Vector norm = new Vector(nx,ny,nz);
        Point point = new Point(px/100,py/100,pz/100);
        
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
    
    public double[] AdaptiveSwimCylinder(double a1x, double a1y, double a1z, double a2x, double a2y, double a2z, double radius, double accuracy)  {
        
        if (this.SwimUnPhys) return null;

        double[] value = new double[8];
        
        radius = radius/100;
        Point a1 = new Point(a1x/100, a1y/100, a1z/100);
        Point a2 = new Point(a2x/100, a2y/100, a2z/100);
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

    public double[] AdaptiveSwimRho(double radius, double accuracy)  {
        System.out.println("Don't use yet");
        if(this.SwimUnPhys) return null;

        double[] value = new double[8];

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

    /**
     * 
     * @param Z
     * @param dir
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToZ(double Z, int dir) {
        double[] value = new double[8];
        ZSwimStopper stopper = new ZSwimStopper(Z, dir);
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, distanceBetweenSaves);
        if (st==null) return null;
        st.computeBDL(PC.CP);
        this.setSwimTraj(st);
        double[] lastY = st.lastElement();
        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;
        value[6] = lastY[6] * 100;
        value[7] = lastY[7] * 10; // Conversion from kG.m to T.cm
        return value;
    }

    private SwimTrajectory swimTraj; 
    
    /**
     * @return the swimTraj
     */
    public SwimTrajectory getSwimTraj() {
        return swimTraj;
    }

    /**
     * @param swimTraj the swimTraj to set
     */
    public void setSwimTraj(SwimTrajectory swimTraj) {
        this.swimTraj = swimTraj;
    }
    
    public double[] SwimToDCA(SwimTrajectory trk2) { //use for both traj to get doca for each track
        
        double[] value = new double[6];
        
        DCASwimStopper stopper = new DCASwimStopper(trk2);
        
        SwimTrajectory st = PC.CF.swim(_charge, _x0, _y0, _z0, _pTot, _theta, _phi,
            stopper, _maxPathLength, stepSize, 0.0005);
        if (st==null) return null;
       
        double[] lastY = st.lastElement();

        value[0] = lastY[0] * 100; // convert back to cm
        value[1] = lastY[1] * 100; // convert back to cm
        value[2] = lastY[2] * 100; // convert back to cm
        value[3] = lastY[3] * _pTot; // normalized values
        value[4] = lastY[4] * _pTot;
        value[5] = lastY[5] * _pTot;

        return value;
    }
}