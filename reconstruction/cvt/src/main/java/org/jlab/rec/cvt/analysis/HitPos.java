/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.rec.cvt.bmt.BMTType;

/**
 *
 * @author ziegler
 */
public class HitPos {
    private BMTType _detType;
    private int _ID;
    private int _layer;
    private int _sector;
    private int _tstatus;
    private int _rstatus;
    private int _rlevel;
    private Line3D _stripline;
    private Track _tTrack; //bf-free trk
    private Track _rTrack; //reco trk
    private List<HitPos> nearestNeighbors;
    public boolean isTruePositive;  //is on track, is reconstructed on-track
    public boolean isFalsePositive; //is off track, is reconstructed on-track
    public boolean isTrueNegative;  //is off track, is reconstructed off-track
    public boolean isFalseNegative; //is on track, is reconstructed off-track
    
    HitPos(DataBank bank, int row, Map<Integer, Track>  recTracks, Track  bgFreeTrack) {
        _ID         = bank.getShort("ID", row);
        _layer      = bank.getByte("layer", row);
        _sector     = bank.getByte("sector", row);
        _tstatus    = bank.getByte("tstatus", row);
        _rstatus    = bank.getByte("rstatus", row);
        _rlevel     = bank.getShort("rlevel", row);
        if(_tstatus ==1) { //hit is on-track
            if(_rstatus ==1) isTruePositive = true; //correctly recognized by tracking as on-track
            if(_rstatus ==0) isFalseNegative = true;//tracking falsely sets hit as off-track
        }
        if(_tstatus ==0) { //hit is off-track
            if(_rstatus ==1) isFalsePositive = true;//tracking falsely sets hit as on-track
            if(_rstatus ==0) isTrueNegative = true;//correctly recognized by tracking as off-track
        }
        double x1 = bank.getFloat("r1", row)*Math.sin(bank.getFloat("theta1", row))*Math.cos(bank.getFloat("phi1", row));
        double y1 = bank.getFloat("r1", row)*Math.sin(bank.getFloat("theta1", row))*Math.sin(bank.getFloat("phi1", row));
        double z1 = bank.getFloat("r1", row)*Math.cos(bank.getFloat("theta1", row));
        double x2 = bank.getFloat("r2", row)*Math.sin(bank.getFloat("theta2", row))*Math.cos(bank.getFloat("phi2", row));
        double y2 = bank.getFloat("r2", row)*Math.sin(bank.getFloat("theta2", row))*Math.sin(bank.getFloat("phi2", row));
        double z2 = bank.getFloat("r2", row)*Math.cos(bank.getFloat("theta2", row));
        Point3D p1 = new Point3D(x1,y1,z1);
        Point3D p2 = new Point3D(x2,y2,z2);
        _stripline = new Line3D(p1,p2);
        int tid = bank.getShort("tid", row); 
        if(recTracks!=null && recTracks.containsKey(tid)) {
            _rTrack = recTracks.get(tid);
        }
        if(bgFreeTrack!=null) {
            _tTrack = bgFreeTrack;
        }
    }

    public void findNearestNeighbors(List<HitPos> insameSecLyr, double docaMax) {
        Line3D hpLn = this.getStripline(); 
        this.nearestNeighbors = new ArrayList<>();
        for(HitPos hp : insameSecLyr) {
            if(hp.getTstatus()==1) continue; //only consider background hits
            Line3D hpnLn = hp.getStripline();
            if(hpLn.distance(hpnLn).length() < docaMax) {
                this.nearestNeighbors.add(hp);
            }
        }
    }
    /**
     * @return the _detType
     */
    public BMTType getDetType() {
        return _detType;
    }

    /**
     * @param _detType the _detType to set
     */
    public void setDetType(BMTType _detType) {
        this._detType = _detType;
    }

    /**
     * @return the _ID
     */
    public int getID() {
        return _ID;
    }

    /**
     * @param _ID the _ID to set
     */
    public void setID(int _ID) {
        this._ID = _ID;
    }

    /**
     * @return the _layer
     */
    public int getLayer() {
        return _layer;
    }

    /**
     * @param _layer the _layer to set
     */
    public void setLayer(int _layer) {
        this._layer = _layer;
    }

    /**
     * @return the _sector
     */
    public int getSector() {
        return _sector;
    }

    /**
     * @param _sector the _sector to set
     */
    public void setSector(int _sector) {
        this._sector = _sector;
    }

    /**
     * @return the _tstatus
     */
    public int getTstatus() {
        return _tstatus;
    }

    /**
     * @param _tstatus the _tstatus to set
     */
    public void setTstatus(int _tstatus) {
        this._tstatus = _tstatus;
    }

    /**
     * @return the _rstatus
     */
    public int getRstatus() {
        return _rstatus;
    }

    /**
     * @param _rstatus the _rstatus to set
     */
    public void setRstatus(int _rstatus) {
        this._rstatus = _rstatus;
    }

    /**
     * @return the _rlevel
     */
    public int getRlevel() {
        return _rlevel;
    }

    /**
     * @param _rlevel the _rlevel to set
     */
    public void setRlevel(int _rlevel) {
        this._rlevel = _rlevel;
    }

    /**
     * @return the _stripline
     */
    public Line3D getStripline() {
        return _stripline;
    }

    /**
     * @param _stripline the _stripline to set
     */
    public void setStripline(Line3D _stripline) {
        this._stripline = _stripline;
    }

    /**
     * @return the _tTrack
     */
    public Track gettTrack() {
        return _tTrack;
    }

    /**
     * @param _tTrack the _tTrack to set
     */
    public void settTrack(Track _tTrack) {
        this._tTrack = _tTrack;
    }

    /**
     * @return the _rTrack
     */
    public Track getrTrack() {
        return _rTrack;
    }

    /**
     * @param _rTrack the _rTrack to set
     */
    public void setrTrack(Track _rTrack) {
        this._rTrack = _rTrack;
    }

    /**
     * @return the nearestNeighbors
     */
    public List<HitPos> getNearestNeighbors() {
        return nearestNeighbors;
    }

    /**
     * @param nearestNeighbors the nearestNeighbors to set
     */
    public void setNearestNeighbors(List<HitPos> nearestNeighbors) {
        this.nearestNeighbors = nearestNeighbors;
    }
    
}
