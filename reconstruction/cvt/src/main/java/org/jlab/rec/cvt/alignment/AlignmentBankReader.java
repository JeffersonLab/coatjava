package org.jlab.rec.cvt.alignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

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
import org.jlab.rec.cvt.track.StraightTrack;
import org.jlab.rec.cvt.track.Track;
import org.jlab.rec.cvt.trajectory.Helix;

/**
 *
 * @author spaul
 *
 */
public class AlignmentBankReader {

    public List<StraightTrack> getCosmics(DataEvent event) {

        
        var SVThits = RecoBankReader.readBSTHitBank(event, "BST::Hits");
        var BMThits = RecoBankReader.readBMTHitBank(event, "BMT::Hits");
        if(SVThits!= null) {
            Collections.sort(SVThits);
        }
        if(BMThits!=null) {
            for(Hit hit : BMThits) {
                hit.getStrip().calcBMTStripParams(hit.getSector(), hit.getLayer(), swimmer);
            }
            Collections.sort(BMThits);
        }

        var _SVTclusters = RecoBankReader.readBSTClusterBank(event, SVThits, "BSTRec::Clusters");
        var _BMTclusters = RecoBankReader.readBMTClusterBank(event, BMThits, "BMTRec::Clusters");
        
        
        var _SVTcrosses = RecoBankReader.readBSTCrossBank(event, _SVTclusters, "BSTRec::Crosses");
        var _BMTcrosses = RecoBankReader.readBMTCrossBank(event, _BMTclusters, "BMTRec::Crosses");
        if(_SVTcrosses!=null) {
            for(Cross cross : _SVTcrosses) {
                cross.setCluster1(_SVTclusters.get(cross.getCluster1().getId()-1));
                cross.setCluster2(_SVTclusters.get(cross.getCluster2().getId()-1)); 
            }
        }
        if(_BMTcrosses!=null) {
            for(Cross cross : _BMTcrosses) {
                cross.setCluster1(_BMTclusters.get(cross.getCluster1().getId()-1));
            }
        }
                       
        List<StraightTrack> tracks = RecoBankReader.readCVTCosmicsBank(event, "CVTRec::Cosmics");
        if(tracks == null) 
            return null;
        
        for(StraightTrack track : tracks) {
            
            List<Cross> crosses = new ArrayList<>();
            for(Cross c : track) {
                if(_SVTcrosses!=null && c.getDetector()==DetectorType.BST) {
                    for(Cross cross : _SVTcrosses) {
                        if(c.getId() == cross.getId())
                            crosses.add(cross);
                    }
                }
                if(_BMTcrosses!=null && c.getDetector()==DetectorType.BMT) {
                    for(Cross cross : _BMTcrosses) {
                        if(c.getId() == cross.getId())
                            crosses.add(cross);
                    }
                }
            }
            track.clear();
            track.addAll(crosses);
        }
        
        return tracks;
    }

    public List<Track> getTracks(DataEvent event) {

        
        var SVThits = RecoBankReader.readBSTHitBank(event, "BST::Hits");
        var BMThits = RecoBankReader.readBMTHitBank(event, "BMT::Hits");
        if(SVThits!= null) {
            Collections.sort(SVThits);
        }
        if(BMThits!=null) {
            for(Hit hit : BMThits) {
                hit.getStrip().calcBMTStripParams(hit.getSector(), hit.getLayer(), swimmer);
            }
            Collections.sort(BMThits);
        }

        var _SVTclusters = RecoBankReader.readBSTClusterBank(event, SVThits, "BSTRec::Clusters");
        var _BMTclusters = RecoBankReader.readBMTClusterBank(event, BMThits, "BMT::Clusters");
        
        
        var _SVTcrosses = RecoBankReader.readBSTCrossBank(event, _SVTclusters, "BSTRec::Crosses");
        var _BMTcrosses = RecoBankReader.readBMTCrossBank(event, _BMTclusters, "BMTRec::Crosses");
        if(_SVTcrosses!=null) {
            for(Cross cross : _SVTcrosses) {
                cross.setCluster1(_SVTclusters.get(cross.getCluster1().getId()-1));
                cross.setCluster2(_SVTclusters.get(cross.getCluster2().getId()-1)); 
            }
        }
        if(_BMTcrosses!=null) {
            for(Cross cross : _BMTcrosses) {
                cross.setCluster1(_BMTclusters.get(cross.getCluster1().getId()-1));
            }
        }
        double xb = 0.0;  /////// FIXME
        double yb = 0.0;  /////// FIXME
        _CVTseeds = RecoBankReader.readCVTSeedsBank(event, xb, yb, _SVTcrosses, _BMTcrosses, "CVT::Seeds");
                       
        List<Track> tracks = RecoBankReader.readCVTTracksBank(event, xb, yb, _CVTseeds, _SVTcrosses, _BMTcrosses, "CVTRec::Tracks");
        if(tracks == null) 
            return null;
        
        for(Track track : tracks) {
            
            List<Cross> crosses = new ArrayList<>();
            for(Cross c : track) {
                if(_SVTcrosses!=null && c.getDetector()==DetectorType.BST) {
                    for(Cross cross : _SVTcrosses) {
                        if(c.getId() == cross.getId())
                            crosses.add(cross);
                    }
                }
                if(_BMTcrosses!=null && c.getDetector()==DetectorType.BMT) {
                    for(Cross cross : _BMTcrosses) {
                        if(c.getId() == cross.getId())
                            crosses.add(cross);
                    }
                }
            }
            track.clear();
            track.addAll(crosses);
           
        }
       
        return tracks;
    }

    
}
