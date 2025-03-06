package org.jlab.rec.atof.veff;

public class VeffCalibration {
    double ldiff, tdiff;
    int iCluster, iBarHit, module;

    public double getLdiff() {
        return ldiff;
    }

    public void setLdiff(double ldiff) {
        this.ldiff = ldiff;
    }

    public double getTdiff() {
        return tdiff;
    }

    public void setTdiff(double tdiff) {
        this.tdiff = tdiff;
    }

    // Getter and Setter for iCluster
    public int getICluster() {
        return iCluster;
    }

    public void setICluster(int iCluster) {
        this.iCluster = iCluster;
    }

    // Getter and Setter for iBarHit
    public int getIBarHit() {
        return iBarHit;
    }

    public void setIBarHit(int iBarHit) {
        this.iBarHit = iBarHit;
    }

    // Getter and Setter for module
    public int getModule() {
        return module;
    }

    public void setModule(int module) {
        this.module = module;
    }
    
    VeffCalibration(int module, double ldiff, double tdiff, int iCluster, int iBarHit){
        this.module=module;
        this.ldiff = ldiff;
        this.tdiff = tdiff;
        this.iCluster = iCluster;
        this.iBarHit = iBarHit;
    }

    public static void main(String[] args) {
    }
}
