package org.jlab.rec.fmt.track;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jlab.clas.swimtools.Swim;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.fmt.Constants;

import org.jlab.rec.urwell.reader.URWellCluster;

/**
 *
 * @author ziegler
 * @author devita
 */
public class Track {

    /**
     *  The status variable explains the number of tracks and the quality of the reconstruction.
     *
     *  Its last digit is the number of FMT layers used in FVT tracking, so it can be any number
     *  from 0 to 3. If it's 0, it means that no FMT layers were used and the FVT track should be
     *  the same as the DC track.
     *
     *  If there was an error in swimming due to an odd track shape or anything, a 100 is added to
     *  the variable to denote that.
     */
    private int status;
    private int _id;
    private int _index;
    private int _sector;
    private int _q;
    private double _chi2;
    private double _x;
    private double _y;
    private double _z;
    private double _px;
    private double _py;
    private double _pz;
    private int _NDF;

    private final List<URWellCluster>[] _clusters = new ArrayList[Constants.NLAYERS];
    private final Trajectory[]    _FMTtrajs = new Trajectory[Constants.NLAYERS];
            
    public Track() {
    }


    public Track(int _index, int _sector, int _q, double _x, double _y, double _z, 
                 double _px, double _py, double _pz, List<URWellCluster> clusters) {
        this._index = _index;
        this._sector = _sector;
        this._q = _q;
        this._x = _x;
        this._y = _y;
        this._z = _z;
        this._px = _px;
        this._py = _py;
        this._pz = _pz;
        for(URWellCluster cluster : clusters) this.addCluster(cluster);
    }
    

    public List<URWellCluster> getClusters() {
        List<URWellCluster> clusters = new ArrayList<>();
        for(int i=0; i<Constants.NLAYERS; i++) {
            if(_clusters[i]!=null) clusters.addAll(_clusters[i]);
        }
        return clusters;
    }

    public List<URWellCluster> getClusters(int layer) {
        if(layer<=0 || layer>Constants.NLAYERS) return null;
        else return _clusters[layer-1];
    }

    public URWellCluster getCluster(int layer) {
        if(layer<=0 || layer>Constants.NLAYERS) return null;
        else if(_clusters[layer-1]== null || _clusters[layer-1].size()==0) return null;
        else return _clusters[layer-1].get(0);
    }

    public int getClusterLayers() {
        int n = 0;
        for(int i=0; i<Constants.NLAYERS; i++) {
            if(_clusters[i]!=null) n ++;
        }
        return n;
    }

    public int getClusterLayer(int layer) {
        if(_clusters[layer-1]!=null) return _clusters[layer-1].size();
        else return 0;
    }
    
    public final void addCluster(URWellCluster cluster) {
        if(this._clusters[cluster.layer()-1]==null)
            this._clusters[cluster.layer()-1] = new ArrayList<>();
        this._clusters[cluster.layer()-1].add(cluster);
    }    

    public void clearClusters(int layer) {
        this._clusters[layer-1].clear();
    }
    
    public Trajectory getFMTTraj(int layer) {
        if(layer<=0 || layer>Constants.NLAYERS) return null;
        return _FMTtrajs[layer-1];
    }

    public void setFMTtraj(Trajectory trj) {
        this._FMTtrajs[trj.getLayer()-1] = trj;
    }

    public int getId() {
        return _id;
    }

    public void setId(int _id) {
        this._id = _id;
    }

    /**
     * @return the _id
     */
    public int getIndex() {
        return _index;
    }

    /**
     * @param _id the _id to set
     */
    public void setIndex(int _id) {
        this._index = _id;
    }

    /**
     * @return the sector
     */
    public int getSector() {
        return _sector;
    }

    /**
     * @param _sector the sector to set
     */
    public void setSector(int _sector) {
        this._sector = _sector;
    }

    /**
     * @return the _q
     */
    public int getQ() {
        return _q;
    }

    /**
     * @param _q the _q to set
     */
    public void setQ(int _q) {
        this._q = _q;
    }

    /**
     * @return the _chi^2.
     */
    public double getChi2() {
        return _chi2;
    }

    /**
     * @param _chi2 the _chi2 to set
     */
    public void setChi2(double _chi2) {
        this._chi2 = _chi2;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getNDF() {
        return _NDF;
    }

    public void setNDF(int _NDF) {
        this._NDF = _NDF;
    }

    /**
     * @return the _x
     */
    public double getX() {
        return _x;
    }

    /**
     * @param _x the _x to set
     */
    public void setX(double _x) {
        this._x = _x;
    }

    /**
     * @return the _y
     */
    public double getY() {
        return _y;
    }

    /**
     * @param _y the _y to set
     */
    public void setY(double _y) {
        this._y = _y;
    }

    /**
     * @return the _z
     */
    public double getZ() {
        return _z;
    }

    /**
     * @param _z the _z to set
     */
    public void setZ(double _z) {
        this._z = _z;
    }

    /**
     * @return the the tracke momentum
     */
    public double getP() {
        return Math.sqrt(_px*_px+_py*_py+_pz*_pz);
    }

    /**
     * @return the _px
     */
    public double getPx() {
        return _px;
    }

    /**
     * @param _px the _px to set
     */
    public void setPx(double _px) {
        this._px = _px;
    }

    /**
     * @return the _py
     */
    public double getPy() {
        return _py;
    }

    /**
     * @param _py the _py to set
     */
    public void setPy(double _py) {
        this._py = _py;
    }

    /**
     * @return the _pz
     */
    public double getPz() {
        return _pz;
    }

    /**
     * @param _pz the _pz to set
     */
    public void setPz(double _pz) {
        this._pz = _pz;
    }
    
    public List<Track> getDCTracks(DataEvent event, Swim swimmer) {
        
        Map<Integer, Track> trackmap = new LinkedHashMap<Integer, Track>();        
        
        DataBank trackBank = null;
        DataBank trajBank  = null;
        if(event.hasBank("TimeBasedTrkg::TBTracks"))   trackBank = event.getBank("TimeBasedTrkg::TBTracks");
        if(event.hasBank("TimeBasedTrkg::Trajectory")) trajBank  = event.getBank("TimeBasedTrkg::Trajectory");
        if (trackBank!=null) {
        
            for (int i = 0; i < trackBank.rows(); i++) {
                Track trk = new Track();
                int id = trackBank.getShort("id", i);
                trk.setId(id);
                trk.setIndex(i);
                trk.setSector(trackBank.getByte("sector", i));
                trk.setQ(trackBank.getByte("q", i));
                
                double vx = trackBank.getFloat("Vtx0_x", i);
                double vy = trackBank.getFloat("Vtx0_y", i);
                double vz = trackBank.getFloat("Vtx0_z", i);
                
                double px = trackBank.getFloat("p0_x", i);
                double py = trackBank.getFloat("p0_y", i);
                double pz = trackBank.getFloat("p0_z", i);
                
                
                Point3D vertexLocal = transGlobaltoLocal(trk.getSector(), vx, vy, vz);
                
                Point3D momLocal = transGlobaltoLocal(trk.getSector(), px, py, pz);
                
                trk.setX(vertexLocal.x());
                trk.setY(vertexLocal.y());
                trk.setZ(vertexLocal.z());
                trk.setPx(momLocal.x());
                trk.setPy(momLocal.y());
                trk.setPz(momLocal.z());
                trk.setStatus(1);
                trackmap.put(id,trk);                            
            }
        }
        List<Track> tracks = new ArrayList<>();
        for(Entry<Integer,Track> entry: trackmap.entrySet()) {
            tracks.add(entry.getValue());
        }
        return tracks;
    }
    
    public Point3D transGlobaltoLocal(int sector, double x, double y, double z){
        Point3D point = new Point3D(x, y, z);
        point.rotateZ(Math.toRadians(-60 * (sector - 1)));
        point.rotateY(Math.toRadians(-25)); 
        
        return point;
    }
    
    public Point3D transLocaltoGlobal(int sector, double x, double y, double z){
        Point3D point = new Point3D(x, y, z);
        point.rotateY(Math.toRadians(25));         
        point.rotateZ(Math.toRadians(60 * (sector - 1)));
        
        return point;
    }    

    @Override
    public String toString() {
        String str = "FMT track :" + " Index "  + this._index
                                   + " Q  "     + this._q
                                   + String.format(" P (%.4f,%.4f,%.4f)", this._px, this._py, this._pz)
                                   + String.format(" D (%.4f,%.4f,%.4f)", this._x, this._y, this._z);
        for(int i=0; i<Constants.NLAYERS; i++) {
            if(_clusters[i]!=null) {
                for(int j=0; j<_clusters[i].size(); j++) str = str + "\n\t" + _clusters[i].get(j).toString();
            }           
        }
        return str;                           
    }

}
