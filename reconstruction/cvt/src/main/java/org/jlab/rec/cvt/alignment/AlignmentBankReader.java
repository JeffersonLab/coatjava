package org.jlab.rec.cvt.alignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.banks.RecoBankReader;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.hit.Strip;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.StraightTrack;
import org.jlab.rec.cvt.track.Track;
import org.jlab.rec.cvt.trajectory.Helix;
import org.jlab.rec.cvt.trajectory.Ray;

/**
 *
 * @author spaul
 *
 */
public class AlignmentBankReader {

    private Map<Integer,Cross>      _SVTcrosses;
    private Map<Integer,Cluster>   _SVTclusters;
    private Map<Integer,Cross>      _BMTcrosses;
    private Map<Integer,Cluster>   _BMTclusters;

    public List<StraightTrack> getCosmics(DataEvent event) {

        
        if(!event.hasBank("CVTRec::Cosmics"))
            return null;
        
        _SVTclusters = RecoBankReader.readBSTClusterBank(event, "BSTRec::Clusters", RecoBankReader.readBSTHitBank(event, "BSTRec::Hits"));
        _BMTclusters = RecoBankReader.readBMTClusterBank(event, "BMTRec::Clusters", RecoBankReader.readBMTHitBank(event, "BMTRec::Hits"));
        
        
        _SVTcrosses = RecoBankReader.readBSTCrossBank(event, "BSTRec::Crosses", _SVTclusters);
        _BMTcrosses = RecoBankReader.readBMTCrossBank(event, "BMTRec::Crosses", _BMTclusters);
//        if(_SVTcrosses!=null) {
//            for(Cross cross : _SVTcrosses) {
//                cross.setCluster1(_SVTclusters.get(cross.getCluster1().getId()-1));
//                cross.setCluster2(_SVTclusters.get(cross.getCluster2().getId()-1)); 
//            }
//        }
//        if(_BMTcrosses!=null) {
//            for(Cross cross : _BMTcrosses) {
//                cross.setCluster1(_BMTclusters.get(cross.getCluster1().getId()-1));
//            }
//        }
                       
        List<StraightTrack> tracks = new ArrayList<>();        

        DataBank bank = event.getBank("CVTRec::Cosmics");
        for(int i = 0; i < bank.rows(); i++) {
            int    tid      = bank.getShort("ID", i);
            double chi2     = bank.getFloat("chi2", i);
            int    ndf      = bank.getShort("ndf", i);
            double yxSlope  = bank.getFloat("trkline_yx_slope", i);
            double yxInterc = bank.getFloat("trkline_yx_interc", i)*10;
            double yzSlope  = bank.getFloat("trkline_yz_slope", i);
            double yzInterc = bank.getFloat("trkline_yz_interc", i)*10;

            Ray ray = new Ray(yxSlope, yxInterc, yzSlope, yzInterc);
            StraightTrack track = new StraightTrack(ray);
            track.setId(tid);
            track.setChi2(chi2);
            track.setNDF(ndf);

            for (int j = 0; j < 18; j++) {
                int crossId = bank.getShort("Cross"+(j+1)+"_ID", i);
                if(crossId>0) {
                    Cross cross = crossId>1000 ? _BMTcrosses.get(crossId) : _SVTcrosses.get(crossId);
                    track.add(cross);
                }
            }
            tracks.add(track);
        }
        return tracks;
    }

    public List<Track> getTracks(DataEvent event) {

        
        if(!event.hasBank("CVTRec::Tracks"))
            return null;
        
        _SVTclusters = RecoBankReader.readBSTClusterBank(event, "BSTRec::Clusters", RecoBankReader.readBSTHitBank(event, "BSTRec::Hits"));
        _BMTclusters = RecoBankReader.readBMTClusterBank(event, "BMT::Clusters", RecoBankReader.readBMTHitBank(event, "BMTRec:::Hits"));
        
        
        _SVTcrosses = RecoBankReader.readBSTCrossBank(event, "BSTRec::Crosses", _SVTclusters);
        _BMTcrosses = RecoBankReader.readBMTCrossBank(event, "BMTRec::Crosses", _BMTclusters);


        List<Track> tracks = new ArrayList<>();        

        DataBank bank = event.getBank("CVTRec::Tracks");
        for(int i = 0; i < bank.rows(); i++) {
            int    tid    = bank.getShort("ID", i);
            double pt     = bank.getFloat("pt", i);
            double phi0   = bank.getFloat("phi0", i);
            double tandip = bank.getFloat("tandip", i);
            double z0     = bank.getFloat("z0", i)*10;
            double d0     = bank.getFloat("d0", i)*10;
            int    q      = bank.getByte("q", i);
            double xb     = bank.getFloat("xb", i)*10;
            double yb     = bank.getFloat("yb", i)*10;
            Helix helix = new Helix( pt, d0, phi0, z0, tandip, q, xb, yb);
            double[][] covmatrix = new double[5][5];
            covmatrix[0][0] = bank.getFloat("cov_d02", i)*10*10;
            covmatrix[0][1] = bank.getFloat("cov_d0phi0", i)*10 ;
            covmatrix[0][2] = bank.getFloat("cov_d0rho", i);
            covmatrix[1][0] = bank.getFloat("cov_d0phi0", i)*10 ;
            covmatrix[1][1] = bank.getFloat("cov_phi02", i);
            covmatrix[1][2] = bank.getFloat("cov_phi0rho", i)/10 ;
            covmatrix[2][0] = bank.getFloat("cov_d0rho", i);
            covmatrix[2][1] = bank.getFloat("cov_phi0rho", i)/10 ;
            covmatrix[2][2] = bank.getFloat("cov_rho2", i)/10/10;
            covmatrix[3][3] = bank.getFloat("cov_z02", i)*10*10;
            covmatrix[3][4] = bank.getFloat("cov_z0tandip", i)*10;
            covmatrix[4][3] = bank.getFloat("cov_z0tandip", i)*10;
            covmatrix[4][4] = bank.getFloat("cov_tandip2", i);
            int    status   = bank.getShort("status", i);
            double chi2     = bank.getFloat("chi2", i);
            int    ndf      = bank.getShort("ndf", i);
            int    pid      = bank.getInt("pid", i);
            int    seedId   = bank.getShort("seedID", i);
            int    type   = bank.getByte("fittingMethod", i);
            Seed seed = new Seed();
            seed.setId(seedId);
            seed.setStatus(type);
            Track track = new Track(helix);
            track.setId(tid);
            track.getHelix().setCovMatrix(covmatrix);
            track.setChi2(chi2);
            track.setNDF(ndf);
            track.setPID(pid);
            track.setKFIterations((int) status/1000);
            track.setSeed(seed);
            for (int j = 0; j < 9; j++) {
                int crossId = bank.getShort("Cross"+(j+1)+"_ID", i);
                if(crossId>0) {
                    Cross cross = crossId>1000 ? _BMTcrosses.get(crossId) : _SVTcrosses.get(crossId);
                    track.add(cross);
                }
            }
            tracks.add(track);

           
        }
       
        return tracks;
    }

    
    public Map<Integer,Cluster> get_ClustersSVT() {
        return _SVTclusters;
    }

    public Map<Integer,Cluster> get_ClustersBMT() {
        return _BMTclusters;
    }

    public Map<Integer,Cross> get_CrossesSVT() {
        return _SVTcrosses;
    }

    public Map<Integer,Cross> get_CrossesBMT() {
        return _BMTcrosses;
    }

}
