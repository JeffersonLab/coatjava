package org.jlab.rec.alert.AIPID;

public class PrePIDResult {
    public final int trackid;
    public final int clusterid;
    public final int prepid;
    public final float p2212, p45, p46, p47, p49;

    public PrePIDResult(int trackid, int clusterid, int prepid,
            float p2212, float p45, float p46, float p47, float p49) {
        this.trackid = trackid;
        this.clusterid = clusterid;
        this.prepid = prepid;
        this.p2212 = p2212;
        this.p45 = p45;
        this.p46 = p46;
        this.p47 = p47;
        this.p49 = p49;
    }

    public PrePIDResult(int trackid, int clusterid, float[] prediction) {
        this(trackid, clusterid, (int) prediction[0],
                prediction[1], prediction[2], prediction[3],
                prediction[5], prediction[4]);
    }
}
