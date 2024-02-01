package org.jlab.clas.swimtools;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

/**
 *
 * @author ziegler
 */
public class CLAS12Swim  {
    private ProbeCollection PC;
    public CLAS12Swim(ProbeCollection pC) {
        PC = pC;
    }
    private double _x0;
    private double _y0;
    private double _z0;
    private double _phi;
    private double _theta;
    private double _pTot;
    private double _rMax = 500 + 300; // increase to allow swimming to outer in cm 
    // detectors
    private double _maxPathLength = 900; //in cm
    private boolean SwimUnPhys = false; //Flag to indicate if track is swimmable
    private int _charge;

    private double SWIMZMINMOM = 0.75; // GeV/c
    private double MINTRKMOM = 0.05; // GeV/c
    private double accuracy = 20e-4; // in cm -->20 microns
    private double tolerance = 1.0e-5;
    private double stepSize = 5.00 * 1.e-2; // in cm -->500 microns
    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToSphere(double Rad) {
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;
        if (getpTot() > getSWIMZMINMOM()) {
             szr = PC.CF_cs.swimSphere(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), new double[]{0,0,0}, Rad, getAccuracy(), getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
        return value;
    }
    
    public double[] SwimToCylinder(double Rad) {
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;
        cnuphys.CLAS12Swim.geometry.Cylinder targetCylinder = new cnuphys.CLAS12Swim.geometry.Cylinder(new double[]{0,0,-1000}, new double[] {0,0,1000}, Rad);
        if (getpTot() > getSWIMZMINMOM()) {
             szr = PC.CF_cs.swimCylinder(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), targetCylinder, getAccuracy(), getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
        return value;
    }
    
    /**
     * 
     * @param radius in cm
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimRho(double radius)  {
        return SwimRho(radius, getAccuracy());
    }
    public double[] SwimRho(double radius, double accuracy)  {
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;
        if (getpTot() > getSWIMZMINMOM()) {
             szr = PC.CF_cs.swimRho(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), radius, accuracy, getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
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
        return SwimGenCylinder(axisPoint1, axisPoint2, radius, getAccuracy());
    }
    /**
     * 
     * @param axisPoint1 in cm
     * @param axisPoint2 in cm 
     * @param radius in cm 
     * @param accuracy
     * @return swam trajectory to the cylinder
     */
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy)  {
       double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }
        
        CLAS12SwimResult szr = null;
        if (getpTot() > getSWIMZMINMOM()) {
             szr = PC.CF_cs.swimCylinder(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), 
                     new double[]{axisPoint1.x(),axisPoint1.y(),axisPoint1.z()}, 
                     new double[]{axisPoint2.x(),axisPoint2.y(),axisPoint2.z()}, 
                     radius,
                     accuracy, getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
        return value;
    }
    
    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;

        if (getpTot() > getSWIMZMINMOM()) {
            szr = PC.RCF_cs.sectorSwimZ(sector, getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), z_cm, getAccuracy(), getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
        return value;
    }
    /**
     * 
     * @param z_cm
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the plane surface
     */
    public double[] SwimToPlaneLab(double z_cm) {
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;

        if (getpTot() > getSWIMZMINMOM()) {
            szr = PC.CF_cs.swimZ(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), z_cm, getAccuracy(), getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
        return value;
    
    }
    
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy)  {
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;

        if (getpTot() > getSWIMZMINMOM()) {
            szr = PC.CF_cs.swimPlane(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), 
                    new double[]{n.x(),n.y(),n.z()}, new double[]{p.x(),p.y(),p.z()}, 
                    accuracy, getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
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
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;
         // the new swim to plane in swimmer
        cnuphys.CLAS12Swim.geometry.Plane plane = new cnuphys.CLAS12Swim.geometry.Plane(n.x(), n.y(), n.z(), d_cm);
        if (getpTot() > getSWIMZMINMOM()) {
            szr = PC.CF_cs.swimPlane(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), plane, getAccuracy(), getrMax(), getStepSize(), getTolerance());
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
        return value;
    
    }
    
    public double[] SwimToBeamLine(double xB, double yB) {
        double[] value = new double[8];

        if (getpTot() < getMINTRKMOM() || this.isSwimUnPhys()==true) // fiducial cut
        {
            return null;
        }

        CLAS12SwimResult szr = null;
        if (getpTot() > getSWIMZMINMOM()) {
            if(xB==0 && yB==0) {
                szr = PC.CF_cs.swimBeamline(getCharge(), getX0(), getY0(), getZ0(), getpTot(), getTheta(), getPhi(), getAccuracy(), getrMax(), getStepSize(), getTolerance());
            } else {
                //fixme
            }
        }
        if(szr!=null) {
            double bdl = szr.getTrajectory().getComputedBDL();
            double pathLength = szr.getPathLength();
            double[] U = szr.getFinalU();
            value[0] = U[0]; // xf in cm
            value[1] = U[1]; // yz in cm
            value[2] = U[2]; // zf in cm
            value[3] = U[3]*getpTot();
            value[4] = U[4]*getpTot();
            value[5] = U[5]*getpTot();
            value[6] = pathLength;
            value[7] = bdl / 10; // convert from kg*cm to T*cm
        }
        
        return value;
    
    }

    /**
     * @return the _x0
     */
    public double getX0() {
        return _x0;
    }

    /**
     * @param _x0 the _x0 to set
     */
    public void setX0(double _x0) {
        this._x0 = _x0;
    }

    /**
     * @return the _y0
     */
    public double getY0() {
        return _y0;
    }

    /**
     * @param _y0 the _y0 to set
     */
    public void setY0(double _y0) {
        this._y0 = _y0;
    }

    /**
     * @return the _z0
     */
    public double getZ0() {
        return _z0;
    }

    /**
     * @param _z0 the _z0 to set
     */
    public void setZ0(double _z0) {
        this._z0 = _z0;
    }

    /**
     * @return the _phi
     */
    public double getPhi() {
        return _phi;
    }

    /**
     * @param _phi the _phi to set
     */
    public void setPhi(double _phi) {
        this._phi = _phi;
    }

    /**
     * @return the _theta
     */
    public double getTheta() {
        return _theta;
    }

    /**
     * @param _theta the _theta to set
     */
    public void setTheta(double _theta) {
        this._theta = _theta;
    }

    /**
     * @return the _pTot
     */
    public double getpTot() {
        return _pTot;
    }

    /**
     * @param _pTot the _pTot to set
     */
    public void setpTot(double _pTot) {
        this._pTot = _pTot;
    }

    /**
     * @return the _rMax
     */
    public double getrMax() {
        return _rMax;
    }

    /**
     * @param _rMax the _rMax to set
     */
    public void setrMax(double _rMax) {
        this._rMax = _rMax;
    }

    /**
     * @return the _maxPathLength
     */
    public double getMaxPathLength() {
        return _maxPathLength;
    }

    /**
     * @param _maxPathLength the _maxPathLength to set
     */
    public void setMaxPathLength(double _maxPathLength) {
        this._maxPathLength = _maxPathLength;
    }

    /**
     * @return the SwimUnPhys
     */
    public boolean isSwimUnPhys() {
        return SwimUnPhys;
    }

    /**
     * @param SwimUnPhys the SwimUnPhys to set
     */
    public void setSwimUnPhys(boolean SwimUnPhys) {
        this.SwimUnPhys = SwimUnPhys;
    }

    /**
     * @return the _charge
     */
    public int getCharge() {
        return _charge;
    }

    /**
     * @param _charge the _charge to set
     */
    public void setCharge(int _charge) {
        this._charge = _charge;
    }

    /**
     * @return the SWIMZMINMOM
     */
    public double getSWIMZMINMOM() {
        return SWIMZMINMOM;
    }

    /**
     * @param SWIMZMINMOM the SWIMZMINMOM to set
     */
    public void setSWIMZMINMOM(double SWIMZMINMOM) {
        this.SWIMZMINMOM = SWIMZMINMOM;
    }

    /**
     * @return the MINTRKMOM
     */
    public double getMINTRKMOM() {
        return MINTRKMOM;
    }

    /**
     * @param MINTRKMOM the MINTRKMOM to set
     */
    public void setMINTRKMOM(double MINTRKMOM) {
        this.MINTRKMOM = MINTRKMOM;
    }

    /**
     * @return the accuracy
     */
    public double getAccuracy() {
        return accuracy;
    }

    /**
     * @param accuracy the accuracy to set
     */
    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    /**
     * @return the tolerance
     */
    public double getTolerance() {
        return tolerance;
    }

    /**
     * @param tolerance the tolerance to set
     */
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }

    /**
     * @return the stepSize
     */
    public double getStepSize() {
        return stepSize;
    }

    /**
     * @param stepSize the stepSize to set
     */
    public void setStepSize(double stepSize) {
        this.stepSize = stepSize;
    }
    
    public void checkR(double _x0, double _y0, double _z0) {
        this.SwimUnPhys=false;
        if(Math.sqrt(_x0*_x0 + _y0*_y0)>this._rMax || 
                Math.sqrt(_x0*_x0 + _y0*_y0 + _z0*_z0)>this._maxPathLength)
            this.SwimUnPhys=true;
    }
}

