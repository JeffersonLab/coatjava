/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.clas.swimtools;

import org.apache.commons.math3.util.FastMath;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
/**
 *
 * @author ziegler
 */

public class Swim {
    
    
    private boolean newSwim = true;

    private ProbeCollection PC;
    
    /**
     * Class for swimming to various surfaces.  
     */
    private void init() {
        setPC(Swimmer.getProbeCollection(Thread.currentThread()));
        if (getPC() == null) {
            setPC(new ProbeCollection());
            Swimmer.put(Thread.currentThread(), getPC());
        }
        oldSwimmer = new SwimD(PC);
        newSwimmer = new CLAS12Swim(PC);
    } 

    /**
     * @return the PC
     */
    public ProbeCollection getPC() {
        return PC;
    }

    /**
     * @param PC the PC to set
     */
    public void setPC(ProbeCollection PC) {
        this.PC = PC;
    }
    
    public Swim() {
       this.init();
    }
    public Swim(boolean newS) {
       this.init();
       newSwim = newS;
    }
    /**
     * 
     * @param FDpz PDet pz for swimming to a FD fixed plane
     * @param Ptot total momentum required to swim a track
     */
    public void setMinPCuts(double FDpz, double Ptot) {
        oldSwimmer.setSWIMZMINMOM(FDpz);
        oldSwimmer.setMINTRKMOM(Ptot);
        newSwimmer.setSWIMZMINMOM(FDpz);
        newSwimmer.setMINTRKMOM(Ptot);
    }
    /**
     * @return the newSwim
     */
    public boolean isNewSwim() {
        return newSwim;
    }

    SwimD oldSwimmer;
    CLAS12Swim newSwimmer;
    
   
    
    private void printV(String pfx, double v[]) {
        double x = v[0] / 100;
        double y = v[1] / 100;
        double z = v[2] / 100;
        double r = Math.sqrt(x * x + y * y + z * z);
        System.out.println(String.format("%s: (%-8.5f, %-8.5f, %-8.5f) R: %-8.5f", pfx, z, y, z, r));
    }
    /**
     *
     * @param direction
     *            +1 for out -1 for in
     * @param x0
     * @param y0
     * @param z0
     * @param thx
     * @param thy
     * @param p
     * @param charge
     */
    public void SetSwimParameters(int direction, double x0, double y0, double z0, double thx, double thy, double p,
                    int charge) {
        if(this.isNewSwim()) {
            newSwimmer.setX0(x0);
            newSwimmer.setY0(y0);
            newSwimmer.setZ0(z0);
            newSwimmer.checkR(newSwimmer.getX0(), newSwimmer.getY0(), newSwimmer.getZ0());
            double pz = direction * p / Math.sqrt(thx * thx + thy * thy + 1);
            double px = thx * pz;
            double py = thy * pz;
            newSwimmer.setPhi(Math.toDegrees(FastMath.atan2(py, px)));
            newSwimmer.setpTot(Math.sqrt(px * px + py * py + pz * pz));
            newSwimmer.setTheta(Math.toDegrees(Math.acos(pz / newSwimmer.getpTot())));
            newSwimmer.setCharge(direction * charge);
        } else {
            // x,y,z in m = swimmer units
            //_x0 = x0 / 100;
            //_y0 = y0 / 100;
            //_z0 = z0 / 100;
            oldSwimmer.setX0(x0/100);
            oldSwimmer.setY0(y0/100);
            oldSwimmer.setZ0(z0/100);
            oldSwimmer.checkR(oldSwimmer.getX0(), oldSwimmer.getY0(), oldSwimmer.getZ0());
            double pz = direction * p / Math.sqrt(thx * thx + thy * thy + 1);
            double px = thx * pz;
            double py = thy * pz;
            oldSwimmer.setPhi(Math.toDegrees(FastMath.atan2(py, px)));
            oldSwimmer.setpTot(Math.sqrt(px * px + py * py + pz * pz));
            oldSwimmer.setTheta(Math.toDegrees(Math.acos(pz / oldSwimmer.getpTot())));
            oldSwimmer.setCharge(direction * charge);
        }
    }

    /**
     * Sets the parameters used by swimmer based on the input track state vector
     * parameters swimming outwards
     *
     * @param superlayerIdx
     * @param layerIdx
     * @param x0
     * @param y0
     * @param z0
     * @param thx
     * @param thy
     * @param p
     * @param charge
     */
    public void SetSwimParameters(int superlayerIdx, int layerIdx, double x0, double y0, double z0, double thx,
                    double thy, double p, int charge) {
        // z at a given DC plane in the tilted coordinate system
        // x,y,z in m = swimmer units
        if(this.isNewSwim()) {
            newSwimmer.setX0(x0);
            newSwimmer.setY0(y0);
            newSwimmer.setZ0(z0);
            newSwimmer.checkR(newSwimmer.getX0(), newSwimmer.getY0(), newSwimmer.getZ0());
            double pz =  p / Math.sqrt(thx * thx + thy * thy + 1);
            double px = thx * pz;
            double py = thy * pz;
            newSwimmer.setPhi(Math.toDegrees(FastMath.atan2(py, px)));
            newSwimmer.setpTot(Math.sqrt(px * px + py * py + pz * pz));
            newSwimmer.setTheta(Math.toDegrees(Math.acos(pz / newSwimmer.getpTot())));
            newSwimmer.setCharge(charge);
        } else {
            // x,y,z in m = swimmer units
            //_x0 = x0 / 100;
            //_y0 = y0 / 100;
            //_z0 = z0 / 100;
            oldSwimmer.setX0(x0/100);
            oldSwimmer.setY0(y0/100);
            oldSwimmer.setZ0(z0/100);
            oldSwimmer.checkR(oldSwimmer.getX0(), oldSwimmer.getY0(), oldSwimmer.getZ0());
            double pz = p / Math.sqrt(thx * thx + thy * thy + 1);
            double px = thx * pz;
            double py = thy * pz;
            oldSwimmer.setPhi(Math.toDegrees(FastMath.atan2(py, px)));
            oldSwimmer.setpTot(Math.sqrt(px * px + py * py + pz * pz));
            oldSwimmer.setTheta(Math.toDegrees(Math.acos(pz / oldSwimmer.getpTot())));
            oldSwimmer.setCharge(charge);
        }
    }

    /**
     * Sets the parameters used by swimmer based on the input track parameters
     *
     * @param x0
     * @param y0
     * @param z0
     * @param px
     * @param py
     * @param pz
     * @param charge
     */
    public void SetSwimParameters(double x0, double y0, double z0, double px, double py, double pz, int charge) {
        if(this.isNewSwim()) {
            newSwimmer.setX0(x0);
            newSwimmer.setY0(y0);
            newSwimmer.setZ0(z0);
            newSwimmer.checkR(newSwimmer.getX0(), newSwimmer.getY0(), newSwimmer.getZ0());
            newSwimmer.setPhi(Math.toDegrees(FastMath.atan2(py, px)));
            newSwimmer.setpTot(Math.sqrt(px * px + py * py + pz * pz));
            newSwimmer.setTheta(Math.toDegrees(Math.acos(pz / newSwimmer.getpTot())));
            newSwimmer.setCharge(charge);
        } else {
            // x,y,z in m = swimmer units
            //_x0 = x0 / 100;
            //_y0 = y0 / 100;
            //_z0 = z0 / 100;
            oldSwimmer.setX0(x0/100);
            oldSwimmer.setY0(y0/100);
            oldSwimmer.setZ0(z0/100);
            oldSwimmer.checkR(oldSwimmer.getX0(), oldSwimmer.getY0(), oldSwimmer.getZ0());
            oldSwimmer.setPhi(Math.toDegrees(FastMath.atan2(py, px)));
            oldSwimmer.setpTot(Math.sqrt(px * px + py * py + pz * pz));
            oldSwimmer.setTheta(Math.toDegrees(Math.acos(pz / oldSwimmer.getpTot())));
            oldSwimmer.setCharge(charge);
        }
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
    public void SetSwimParameters(double xcm, double ycm, double zcm, double phiDeg, double thetaDeg, double p,
                    int charge, double maxPathLength) {
        
        if(this.isNewSwim()) {
            newSwimmer.setX0(xcm);
            newSwimmer.setY0(ycm);
            newSwimmer.setZ0(zcm);
            newSwimmer.checkR(newSwimmer.getX0(), newSwimmer.getY0(), newSwimmer.getZ0());
            newSwimmer.setPhi(phiDeg);
            newSwimmer.setpTot(p);
            newSwimmer.setTheta(thetaDeg);
            newSwimmer.setCharge(charge);
            newSwimmer.setMaxPathLength(maxPathLength);
        } else {
            // x,y,z in m = swimmer units
            //_x0 = x0 / 100;
            //_y0 = y0 / 100;
            //_z0 = z0 / 100;
            oldSwimmer.setX0(xcm/100);
            oldSwimmer.setY0(ycm/100);
            oldSwimmer.setZ0(zcm/100);
            oldSwimmer.checkR(oldSwimmer.getX0(), oldSwimmer.getY0(), oldSwimmer.getZ0());
            oldSwimmer.setPhi(phiDeg);
            oldSwimmer.setpTot(p);
            oldSwimmer.setTheta(thetaDeg);
            oldSwimmer.setCharge(charge);
            oldSwimmer.setMaxPathLength(maxPathLength);
        }
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
    public void SetSwimParameters(double xcm, double ycm, double zcm, double phiDeg, double thetaDeg, double p,
                    int charge, double maxPathLength, double Accuracy, double StepSize) {

        if(this.isNewSwim()) {
            newSwimmer.setX0(xcm);
            newSwimmer.setY0(ycm);
            newSwimmer.setZ0(zcm);  
            newSwimmer.checkR(newSwimmer.getX0(), newSwimmer.getY0(), newSwimmer.getZ0());
            newSwimmer.setPhi(phiDeg);
            newSwimmer.setpTot(p);
            newSwimmer.setTheta(thetaDeg);
            newSwimmer.setCharge(charge);
            newSwimmer.setMaxPathLength(maxPathLength);
            newSwimmer.setAccuracy(Accuracy);
            newSwimmer.setStepSize(StepSize);
            
        } else {
            // x,y,z in m = swimmer units
            //_x0 = x0 / 100;
            //_y0 = y0 / 100;
            //_z0 = z0 / 100;
            oldSwimmer.setX0(xcm/100);
            oldSwimmer.setY0(ycm/100);
            oldSwimmer.setZ0(zcm/100);
            oldSwimmer.checkR(oldSwimmer.getX0(), oldSwimmer.getY0(), oldSwimmer.getZ0());
            oldSwimmer.setPhi(phiDeg);
            oldSwimmer.setpTot(p);
            oldSwimmer.setTheta(thetaDeg);
            oldSwimmer.setCharge(charge);
            oldSwimmer.setMaxPathLength(maxPathLength);
            oldSwimmer.setAccuracy(Accuracy/100);
            oldSwimmer.setStepSize(StepSize/100);
        }
        
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

        getPC().RCP.field(sector, (float) x_cm, (float) y_cm, (float) z_cm, result);
        // rcompositeField.field((float) x_cm, (float) y_cm, (float) z_cm,
        // result);
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

        getPC().CP.field((float) x_cm, (float) y_cm, (float) z_cm, result);
        result[0] = result[0] / 10;
        result[1] = result[1] / 10;
        result[2] = result[2] / 10;

    }

    /**
     * 
     * @param Rad
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the surface 
     */
    public double[] SwimToSphere(double Rad) {
          if(!this.isNewSwim()) {
            return oldSwimmer.SwimToSphere( Rad);
        } else {
            return newSwimmer.SwimToSphere( Rad);
        }
    }
    public double[] SwimToCylinder(double Rad) {
         if(!this.isNewSwim()) {
            return oldSwimmer.SwimToCylinder( Rad);
        } else {
            return newSwimmer.SwimToCylinder( Rad);
        }
    }
    public double[] SwimRho(double radius, double accuracy) {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimRho(radius, accuracy);
        } else {
            return newSwimmer.SwimRho(radius, accuracy);
        }
    }
    public double[] SwimRho(double radius) {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimRho(radius);
        } else {
            return newSwimmer.SwimRho(radius);
        }
    }
    
    /**
     * 
     * @param axisPoint1 in cm
     * @param axisPoint2 in cm 
     * @param radius in cm 
     * @return swam trajectory to the cylinder
     */
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius)  {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimGenCylinder(axisPoint1, axisPoint2, radius);
        } else {
            return newSwimmer.SwimGenCylinder(axisPoint1, axisPoint2, radius);
        }
    }
    
    public double[] SwimGenCylinder(Point3D axisPoint1, Point3D axisPoint2, double radius, double accuracy) {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimGenCylinder(axisPoint1, axisPoint2, radius, accuracy);
        } else {
            return newSwimmer.SwimGenCylinder(axisPoint1, axisPoint2, radius, accuracy);
        }
    }
            
    public double[] SwimToPlaneTiltSecSys(int sector, double z_cm) {
        if(!this.isNewSwim()) { 
            return oldSwimmer.SwimToPlaneTiltSecSys(sector, z_cm);
        } else {
            return newSwimmer.SwimToPlaneTiltSecSys(sector, z_cm);
        }
    }
    
    /**
     * 
     * @param z_cm
     * @return state  x,y,z,px,py,pz, pathlength, iBdl at the plane surface
     */
    public double[] SwimToPlaneLab(double z_cm) {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimToPlaneLab(z_cm);
        } else {
            return newSwimmer.SwimToPlaneLab(z_cm);
        }
    }
    
    public double[] SwimPlane(Vector3D n, Point3D p, double accuracy)  {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimPlane(n,p,accuracy);
        } else {
            return newSwimmer.SwimPlane(n,p,accuracy);
        }
    }
    
    public double[] SwimToPlaneBoundary(double d_cm, Vector3D n, int dir) {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimToPlaneBoundary(d_cm, n, dir);
        } else {
            return newSwimmer.SwimToPlaneBoundary(d_cm, n, dir);
        }
    }
    
    public double[] SwimToBeamLine(double xB, double yB) {
        if(!this.isNewSwim()) {
            return oldSwimmer.SwimToBeamLine(xB, yB);
        } else {
            return newSwimmer.SwimToBeamLine(xB, yB);
        }
    }
    
}