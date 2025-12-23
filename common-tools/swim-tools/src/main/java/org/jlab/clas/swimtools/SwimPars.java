package org.jlab.clas.swimtools;

import org.apache.commons.math3.util.FastMath;

public class SwimPars {
    
    final double SWIMZMINMOM = 0.75; // GeV/c
    final double MINTRKMOM = 0.05; // GeV/c

    double _x0;
    double _y0;
    double _z0;
    double _phi;
    double _theta;
    double _pTot;
    final double _rMax = 5 + 3;
    double _maxPathLength = 9;
    boolean SwimUnPhys = false; //Flag to indicate if track is swimmable
    int _charge;
    double accuracy = 20e-6; // 20 microns
    public double stepSize = 5.00 * 1.e-4; // 500 microns
    public double distanceBetweenSaves= 100*stepSize;
    
    ProbeCollection PC;
    
    public SwimPars() {
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
     * @param thx 
     * @param thy 
     * @param p (GeV)
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
     * @param p (GeV)
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
     * @param px (GeV)
     * @param py (GeV)
     * @param pz (GeV)
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
     * @param p (GeV)
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
     * @param p (GeV)
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

}
