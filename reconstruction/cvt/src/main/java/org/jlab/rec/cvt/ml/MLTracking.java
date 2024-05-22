/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.banks.RecoBankWriter;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.services.TracksFromTargetRec;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.Track;

/**
 *
 * @author ziegler
 */
public class MLTracking extends CVTInitializer {
    private double[] aistatus = new double[]{1};
    
    @Override
    public boolean processDataEvent(DataEvent event) {
        this.initialize(event);
        this.processData(event);
        return true;
    }

    private void processData(DataEvent event) {
        MLClusterBankIO aicr = new MLClusterBankIO();
        
        List<DataBank> banks = new ArrayList<>();
        List<Cluster> aicls = aicr.getClusters(event, swimmer, getAistatus());
        List<ArrayList<Cluster>>    clusters = new ArrayList<>();
        clusters.add(aicr.getSVTClusters());
        clusters.add(aicr.getBMTClusters());
        
        List<ArrayList<Cross>>    crosses = reco.findCrosses(aicls);
         Map<Integer, Track> helicaltracks = new HashMap<>();
        if(crosses != null) {
            TracksFromTargetRec  trackFinder = new TracksFromTargetRec(swimmer, xyBeam);
            trackFinder.totTruthHits = reco.getTotalNbTruHits();
            List<Seed>   seeds = trackFinder.getSeeds(clusters, crosses);

            List<Track> tracks = trackFinder.getTracks(event, this.isInitFromMc(), 
                                                              this.isKfFilterOn(), 
                                                              this.getKfIterations(), 
                                                              true, this.getPid());

            if(tracks!=null) {
                for(Track t : tracks)
                    helicaltracks.put(t.getId(), t);
            }
            if(!crosses.get(0).isEmpty()) banks.add(RecoBankWriter.fillSVTCrossBank(event, crosses.get(0), this.getSvtCrossBank()));
            if(!crosses.get(1).isEmpty()) banks.add(RecoBankWriter.fillBMTCrossBank(event, crosses.get(1), this.getBmtCrossBank()));
            
            if(seeds!=null) {
                banks.add(RecoBankWriter.fillSeedBank(event, seeds, this.getSeedBank()));
                banks.add(RecoBankWriter.fillSeedClusBank(event, seeds, this.getSeedClusBank()));
            }
            if(tracks!=null) {
                banks.add(RecoBankWriter.fillTrackBank(event, tracks, this.getTrackBank()));
                //banks.add(RecoBankWriter.fillUTrackBank(event, tracks, this.getUTrackBank()));
                //banks.add(RecoBankWriter.fillTrackCovMatBank(event, tracks, this.getCovMat()));
                banks.add(RecoBankWriter.fillTrajectoryBank(event, tracks, this.getTrajectoryBank()));
                banks.add(RecoBankWriter.fillKFTrajectoryBank(event, tracks, this.getKFTrajectoryBank()));
            }
        }
        //override the hits and clusters banks
        if(event.hasBank(this.getSvtHitBank())) {
            event.removeBank(this.getSvtHitBank());
        }
        banks.add(RecoBankWriter.fillSVTHitBank(event, aicr.getSVTHits(), this.getSvtHitBank()));
        
        // banks.add(RecoBankWriter.fillSVTHitPosBank(event, aicr.getSVTHits(), helicaltracks, this.getSvtHitPosBank()));
        if(event.hasBank(this.getBmtHitBank())) {
            event.removeBank(this.getBmtHitBank());
        }
        banks.add(RecoBankWriter.fillBMTHitBank(event, aicr.getSVTHits(), this.getBmtHitBank()));
          
        // banks.add(RecoBankWriter.fillBMTHitPosBank(event, aicr.getBMTHits(), helicaltracks, this.getBmtHitPosBank()));
         
        if(event.hasBank(this.getSvtClusterBank())) {
            event.removeBank(this.getSvtClusterBank());
        }
        banks.add(RecoBankWriter.fillSVTClusterBank(event, aicr.getSVTClusters(), this.getSvtClusterBank()));
        
        if(event.hasBank(this.getBmtClusterBank())) {
            event.removeBank(this.getBmtClusterBank());
        }
        banks.add(RecoBankWriter.fillBMTClusterBank(event, aicr.getBMTClusters(), this.getBmtClusterBank()));
          
        
        event.appendBanks(banks.toArray(new DataBank[0]));
    }
    
    /**
     * @return the aistatus
     */
    public double[] getAistatus() {
        return aistatus;
    }

    /**
     * @param aistatus the aistatus to set
     */
    public void setAistatus(double[] aistatus) {
        this.aistatus = aistatus;
    }
}
