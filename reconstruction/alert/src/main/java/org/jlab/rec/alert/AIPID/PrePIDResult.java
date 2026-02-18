package org.jlab.rec.alert.AIPID;

public class PrePIDResult {
    public final int trackid;
    public final int clusterid;
    public final int prepid;

    public PrePIDResult(int trackid, int clusterid, int prepid) {
        this.trackid = trackid;
        this.clusterid = clusterid;
        this.prepid = prepid;
    }
}
