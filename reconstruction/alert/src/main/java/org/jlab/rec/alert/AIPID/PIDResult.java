package org.jlab.rec.alert.AIPID;

public class PIDResult {
    public final int trackid;
    public final int clusterid;
    public final int pid;
    public final float p2212, p45, p46, p47, p49;

    public PIDResult(int trackid, int clusterid, float[] prediction) {
        this.trackid = trackid;
        this.clusterid = clusterid;
        this.pid = (int) prediction[0];
        this.p2212 = prediction[1];
        this.p45 = prediction[2];
        this.p46 = prediction[3];
        this.p49 = prediction[4];
        this.p47 = prediction[5];
    }
}
