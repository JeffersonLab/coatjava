package org.jlab.rec.muvt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.geant4.v2.MPGD.MUVT.MUVTStripFactory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;


/**
 *
 * @author ziegler
 * @author devita
 */
public class MUVTTrack {

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

    private final MUVTTrajectory[]    _DCtrajs  = new MUVTTrajectory[MUVTConstants.NLAYER];
    private final List<MUVTCluster>[] _clusters = new ArrayList[MUVTConstants.NLAYER];
    private final MUVTTrajectory[]    _FMTtrajs = new MUVTTrajectory[MUVTConstants.NLAYER];
            
    public MUVTTrack() {
    }


    public MUVTTrack(int _index, int _sector, int _q, double _x, double _y, double _z, 
                 double _px, double _py, double _pz, List<MUVTCluster> clusters) {
        this._index = _index;
        this._sector = _sector;
        this._q = _q;
        this._x = _x;
        this._y = _y;
        this._z = _z;
        this._px = _px;
        this._py = _py;
        this._pz = _pz;
        for(MUVTCluster cluster : clusters) this.addCluster(cluster);
    }
    

    
    /**
     * @param layer
     * @return the _traj
     */
    public MUVTTrajectory getDCTraj(int layer) {
        if(layer<=0 || layer>MUVTConstants.NLAYER) return null;
        else return _DCtrajs[layer-1];
    }

    /**
     * @param trj
     */
    public void setDCTraj(MUVTTrajectory trj) {
        this._DCtrajs[trj.getLayer()-1] = trj;
    }

    public List<MUVTCluster> getClusters() {
        List<MUVTCluster> clusters = new ArrayList<>();
        for(int i=0; i<MUVTConstants.NLAYER; i++) {
            if(_clusters[i]!=null) clusters.addAll(_clusters[i]);
        }
        return clusters;
    }

    public List<MUVTCluster> getClusters(int layer) {
        if(layer<=0 || layer>MUVTConstants.NLAYER) return null;
        else return _clusters[layer-1];
    }

    public MUVTCluster getCluster(int layer) {
        if(layer<=0 || layer>MUVTConstants.NLAYER) return null;
        else if(_clusters[layer-1]== null || _clusters[layer-1].size()==0) return null;
        else return _clusters[layer-1].get(0);
    }

    public int getClusterLayers() {
        int n = 0;
        for(int i=0; i<MUVTConstants.NLAYER; i++) {
            if(_clusters[i]!=null) n ++;
        }
        return n;
    }

    public int getClusterLayer(int layer) {
        if(_clusters[layer-1]!=null) return _clusters[layer-1].size();
        else return 0;
    }
    
    public final void addCluster(MUVTCluster cluster) {
        if(this._clusters[cluster.getLayer()-1]==null)
            this._clusters[cluster.getLayer()-1] = new ArrayList<>();
        this._clusters[cluster.getLayer()-1].add(cluster);
    }    

    public void clearClusters(int layer) {
        this._clusters[layer-1].clear();
    }
    
    public MUVTTrajectory getFMTTraj(int layer) {
        if(layer<=0 || layer>MUVTConstants.NLAYER) return null;
        return _FMTtrajs[layer-1];
    }

    public void setFMTtraj(MUVTTrajectory trj) {
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
    
//    //  FIXME: THIS METHOD SHOULD BE GENERALIZED
//    public double getSeedQuality() {
//        double quality=99;
//        if(this.getClusterLayer(1)>0 && this.getClusterLayer(2)>0 && this.getClusterLayer(3)>0) {
//            Line3D seg1 = this.getClusters(1).get(0).getGlobalSegment();
//            Line3D seg2 = this.getClusters(2).get(0).getGlobalSegment();
//            Line3D seg3 = this.getClusters(3).get(0).getGlobalSegment();
//            quality = seg1.distanceSegments(seg3).distanceSegments(seg2).length(); 
//        }
//        return quality;
//    }


//    //  FIXME: THIS METHOD SHOULD BE GENERALIZED
//    public void filterClusters(int mode) {
//                
//        double dref = Double.POSITIVE_INFINITY;
//        
//        int[] ibest = new int[Constants.NLAYERS];
//        for(int i=0; i<Constants.NLAYERS; i++) ibest[i] = -1;
//        
//        if(mode==1 && this.getClusterLayer(1)>0 && this.getClusterLayer(2)>0 && this.getClusterLayer(3)>0) {
//            for(int i1=0; i1<this.getClusters(1).size(); i1++) {
//                Line3D seg1 = this.getClusters(1).get(i1).getGlobalSegment();
//                for(int i2=0; i2<this.getClusters(2).size(); i2++) {
//                    Line3D seg2 = this.getClusters(2).get(i2).getGlobalSegment();
//                    for(int i3=0; i3<this.getClusters(3).size(); i3++) {
//                        Line3D seg3 = this.getClusters(3).get(i3).getGlobalSegment();
//
//                        double d = seg1.distanceSegments(seg3).distanceSegments(seg2).length();
//                        if(d<dref) {
//                            ibest[0] = i1; ibest[1]=i2; ibest[2]=i3;
//                            dref = d;
//                        }
//                    }    
//                }    
//            }
//        }
//        else {
//            for(int layer=1; layer<=Constants.NLAYERS; layer++) {
//                
//                if(this.getClusters(layer)==null) continue;
//                
//                dref = Double.POSITIVE_INFINITY;
//                for(int j=0; j<this.getClusters(layer).size(); j++) {
//                    double d = this.getClusters(layer).get(j).distance(this.getDCTraj(layer).getPosition());
//                    if(d<dref) {
//                        dref = d;
//                        ibest[layer-1]=j;
//                    }
//                }
//            }
//        }
//        
//        for(int i=0; i<Constants.NLAYERS; i++) {
//            if(ibest[i]>=0) {
//                List<Cluster> cls = new ArrayList<>();
//                cls.add(_clusters[i].get(ibest[i]));
//                _clusters[i] = cls; 
//            }
//        }
//    }
    
    public List<MUVTTrack> getDCTracks(DataEvent event, Swim swimmer, MUVTStripFactory factory) {
        
        Map<Integer, MUVTTrack> trackmap = new LinkedHashMap<>();        
        
        DataBank trackBank = null;
        DataBank trajBank  = null;
        if(event.hasBank("TimeBasedTrkg::TBTracks"))   trackBank = event.getBank("TimeBasedTrkg::TBTracks");
        if(event.hasBank("TimeBasedTrkg::Trajectory")) trajBank  = event.getBank("TimeBasedTrkg::Trajectory");
        if (trackBank!=null) {
        
            for (int i = 0; i < trackBank.rows(); i++) {
                MUVTTrack trk = new MUVTTrack();
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
                                
                trk.setX(vx);
                trk.setY(vy);
                trk.setZ(vz);
                trk.setPx(px);
                trk.setPy(py);
                trk.setPz(pz);
                trk.setStatus(1);
                trackmap.put(id,trk);                         
                
                Line3D vertexToPlane = new Line3D();
                factory.getPlane(trk.getSector(), 1).distance(new Point3D(vx, vy, vz), vertexToPlane);
                if(vertexToPlane.direction().dot(new Vector3D(px,py,pz))>0) {
                    swimmer.SetSwimParameters(vx, vy, vz, px, py, pz, trk.getQ());
                    for(int il=0; il<MUVTConstants.NLAYER/2; il++) {
                        int layer = il*2+1;
                        Plane3D plane = factory.getPlane(trk.getSector(), layer);
                        double[] traj =swimmer.SwimToPlaneBoundary(plane.point().toVector3D().dot(plane.normal()), plane.normal(), 1);
                        if(traj!=null) {
                            trk.setDCTraj(new MUVTTrajectory(layer    , traj[0], traj[1], traj[2], traj[3], traj[4], traj[5], traj[6]));
                            trk.setDCTraj(new MUVTTrajectory(layer + 1, traj[0], traj[1], traj[2], traj[3], traj[4], traj[5], traj[6]));
                        }
                    }
                }
            }
//            for (int i = 0; i < trajBank.rows(); i++) {
//                if (trajBank.getByte("detector", i) == DetectorType.FMT.getDetectorId()) { 
//                    int id    = trajBank.getShort("id", i);
//                    int layer = trajBank.getByte("layer", i);
//                    MUVTTrajectory trj = new MUVTTrajectory(layer,
//                                                    trajBank.getFloat("x", i),
//                                                    trajBank.getFloat("y", i),
//                                                    trajBank.getFloat("z", i),
//                                                    trajBank.getFloat("tx", i),
//                                                    trajBank.getFloat("ty", i),
//                                                    trajBank.getFloat("tz", i),
//                                                    trajBank.getFloat("path", i));
//                    trackmap.get(id).setDCTraj(trj);                
//                }
//            }
        }
        List<MUVTTrack> tracks = new ArrayList<>();
        for(Entry<Integer,MUVTTrack> entry: trackmap.entrySet()) {
            tracks.add(entry.getValue());
        }
        return tracks;
    }
    
    public void toLocal(int sector) {
        Point3D v = MUVTConstants.toTiltedSectorFrame(sector, _x, _y, _z);
        Point3D p = MUVTConstants.toTiltedSectorFrame(sector, _px, _py, _pz);
        this.setSector(sector);
        this.setX(v.x());
        this.setY(v.y());
        this.setZ(v.z());
        this.setPx(p.x());
        this.setPy(p.y());
        this.setPz(p.z());
    }

    public void toGlobal() {
        Point3D v = MUVTConstants.toLab(_sector, _x, _y, _z);
        Point3D p = MUVTConstants.toLab(_sector, _px, _py, _pz);
        this.setX(v.x());
        this.setY(v.y());
        this.setZ(v.z());
        this.setPx(p.x());
        this.setPy(p.y());
        this.setPz(p.z());
    }

    @Override
    public String toString() {
        String str = "FMT track :" + " Index "  + this._index
                                   + " Q  "     + this._q
                                   + String.format(" P (%.4f,%.4f,%.4f)", this._px, this._py, this._pz)
                                   + String.format(" D (%.4f,%.4f,%.4f)", this._x, this._y, this._z);
        for(int i=0; i<MUVTConstants.NLAYER; i++) {
            if(_clusters[i]!=null) {
                for(int j=0; j<_clusters[i].size(); j++) str = str + "\n\t" + _clusters[i].get(j).toString();
            }           
        }
        return str;                           
    }

}
