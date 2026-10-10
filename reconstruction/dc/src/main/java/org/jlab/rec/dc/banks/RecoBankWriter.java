package org.jlab.rec.dc.banks;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.banks.HitBasedTrkg_HBClusters;
import org.jlab.io.banks.HitBasedTrkg_HBCrosses;
import org.jlab.io.banks.HitBasedTrkg_HBHitTrkId;
import org.jlab.io.banks.HitBasedTrkg_HBHits;
import org.jlab.io.banks.HitBasedTrkg_HBSegments;
import org.jlab.io.banks.HitBasedTrkg_HBTracks;
import org.jlab.io.banks.HitBasedTrkg_Hits;
import org.jlab.io.banks.TimeBasedTrkg_TBClusters;
import org.jlab.io.banks.TimeBasedTrkg_TBCovMat;
import org.jlab.io.banks.TimeBasedTrkg_TBCrosses;
import org.jlab.io.banks.TimeBasedTrkg_TBSegments;
import org.jlab.io.banks.TimeBasedTrkg_TBTracks;
import org.jlab.io.banks.TimeBasedTrkg_Trajectory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.track.Track;

import trackfitter.fitter.utilities.*;

/**
 * A class to fill the reconstructed DC banks
 *
 * @author ziegler
 *
 */
public class RecoBankWriter {
    
    Banks bankNames = null;
//    /**
//     *
//     * Writes output banks
//     *
//     */
    
    public RecoBankWriter(Banks names) {
        this.bankNames= names;
    }

    public void updateListsWithClusterInfo(List<FittedHit> fhits,
            List<FittedCluster> clusters) {
        ArrayList rmHits = new ArrayList<FittedHit>();
        ArrayList addHits = new ArrayList<FittedHit>();
        for (int i = 0; i < clusters.size(); i++) {
            clusters.get(i).set_Id(i + 1);
            for (int j = 0; j < clusters.get(i).size(); j++) {

                clusters.get(i).get(j).set_AssociatedClusterID(clusters.get(i).get_Id());
                addHits.add(clusters.get(i).get(j));
                for (int k = 0; k < fhits.size(); k++) {
                    if (fhits.get(k).get_Id() == clusters.get(i).get(j).get_Id()) {
                        rmHits.add(fhits.get(k));
                    }
                }
            }
        }
        
        fhits.removeAll(rmHits);
        fhits.addAll(addHits);
    }
    
    public DataBank fillHitsBank(DataEvent event, List<FittedHit> hitlist) {
        String name = bankNames.getHitsBank();
        
        int rejCnt = 0;
        for (int i = 0; i < hitlist.size(); i++) {
//            if (hitlist.get(i).get_Id() == -1 /*|| hitlist.get(i).get_Id()==0*/) { //PASS1
            if (hitlist.get(i).get_Id() == -1 || hitlist.get(i).get_Id()==0) {
                rejCnt++;
            }
        }
        DataBank bank = event.createBank(name, hitlist.size()-rejCnt);
        rejCnt=0;
        for (int i = 0; i < hitlist.size(); i++) {
//            if (hitlist.get(i).get_Id() == -1 /*|| hitlist.get(i).get_Id()==0*/) { //PASS1
            if (hitlist.get(i).get_Id() == -1 || hitlist.get(i).get_Id()==0) {
                rejCnt++;
                continue;
        }
        bank.setShort(HitBasedTrkg_Hits.id, i-rejCnt, (short) hitlist.get(i).get_Id());
        bank.setShort(HitBasedTrkg_Hits.indexTDC, i-rejCnt, (short) hitlist.get(i).get_IndexTDC());
        bank.setShort(HitBasedTrkg_Hits.status, i-rejCnt, (short) hitlist.get(i).get_QualityFac());
        bank.setByte(HitBasedTrkg_Hits.superlayer, i-rejCnt, (byte) hitlist.get(i).get_Superlayer());
        bank.setByte(HitBasedTrkg_Hits.layer, i-rejCnt, (byte) hitlist.get(i).get_Layer());
        bank.setByte(HitBasedTrkg_Hits.sector, i-rejCnt, (byte) hitlist.get(i).get_Sector());
        bank.setShort(HitBasedTrkg_Hits.wire, i-rejCnt, (short) hitlist.get(i).get_Wire());
        bank.setFloat(HitBasedTrkg_Hits.docaError, i-rejCnt, (float) hitlist.get(i).get_DocaErr());
        bank.setFloat(HitBasedTrkg_Hits.trkDoca, i-rejCnt, (float) hitlist.get(i).get_ClusFitDoca());
        bank.setFloat(HitBasedTrkg_Hits.LocX, i-rejCnt, (float) hitlist.get(i).get_lX());
        bank.setFloat(HitBasedTrkg_Hits.LocY, i-rejCnt, (float) hitlist.get(i).get_lY());
        bank.setFloat(HitBasedTrkg_Hits.X, i-rejCnt, (float) hitlist.get(i).get_X());
        bank.setFloat(HitBasedTrkg_Hits.Z, i-rejCnt, (float) hitlist.get(i).get_Z());
        bank.setByte(HitBasedTrkg_Hits.LR, i-rejCnt, (byte) hitlist.get(i).get_LeftRightAmb());
        bank.setShort(HitBasedTrkg_Hits.clusterID, i-rejCnt, (short) hitlist.get(i).get_AssociatedClusterID());
        bank.setInt(HitBasedTrkg_Hits.TDC,i-rejCnt,hitlist.get(i).get_TDC());
        bank.setByte(HitBasedTrkg_Hits.jitter,i, (byte) hitlist.get(i).getJitter());
        
        }

        return bank;

    }

    public DataBank fillHBHitsBank(DataEvent event, List<FittedHit> hitlist) {
        String name = bankNames.getHitsBank();
        
        int rejCnt = 0;
        for (int i = 0; i < hitlist.size(); i++) {
//            if (hitlist.get(i).get_Id() == -1 /*|| hitlist.get(i).get_Id()==0*/) { //PASS1
            if (hitlist.get(i).get_Id() == -1 || hitlist.get(i).get_Id()==0) {
                rejCnt++;
            }
        }
        DataBank bank = event.createBank(name, hitlist.size()-rejCnt);
        rejCnt=0;
        for (int i = 0; i < hitlist.size(); i++) {
//            if (hitlist.get(i).get_Id() == -1 /*|| hitlist.get(i).get_Id()==0*/) { //PASS1
            if (hitlist.get(i).get_Id() == -1 || hitlist.get(i).get_Id()==0) {
                rejCnt++;
                continue;
        }

        bank.setShort(HitBasedTrkg_HBHits.id, i-rejCnt, (short) hitlist.get(i).get_Id());
        bank.setShort(HitBasedTrkg_HBHits.status, i-rejCnt, (short) hitlist.get(i).get_QualityFac());
        bank.setByte(HitBasedTrkg_HBHits.superlayer, i-rejCnt, (byte) hitlist.get(i).get_Superlayer());
        bank.setByte(HitBasedTrkg_HBHits.layer, i-rejCnt, (byte) hitlist.get(i).get_Layer());
        bank.setByte(HitBasedTrkg_HBHits.sector, i-rejCnt, (byte) hitlist.get(i).get_Sector());
        bank.setShort(HitBasedTrkg_HBHits.wire, i-rejCnt, (short) hitlist.get(i).get_Wire());
        bank.setFloat(HitBasedTrkg_HBHits.docaError, i-rejCnt, (float) hitlist.get(i).get_DocaErr());
        bank.setFloat(HitBasedTrkg_HBHits.trkDoca, i-rejCnt, (float) hitlist.get(i).get_ClusFitDoca());
        bank.setFloat(HitBasedTrkg_HBHits.LocX, i-rejCnt, (float) hitlist.get(i).get_lX());
        bank.setFloat(HitBasedTrkg_HBHits.LocY, i-rejCnt, (float) hitlist.get(i).get_lY());
        bank.setFloat(HitBasedTrkg_HBHits.X, i-rejCnt, (float) hitlist.get(i).get_X());
        bank.setFloat(HitBasedTrkg_HBHits.Z, i-rejCnt, (float) hitlist.get(i).get_Z());
        bank.setByte(HitBasedTrkg_HBHits.LR, i-rejCnt, (byte) hitlist.get(i).get_LeftRightAmb());
        bank.setShort(HitBasedTrkg_HBHits.clusterID, i-rejCnt, (short) hitlist.get(i).get_AssociatedClusterID());
        bank.setInt(HitBasedTrkg_HBHits.TDC,i-rejCnt,hitlist.get(i).get_TDC());
        bank.setByte(HitBasedTrkg_HBHits.jitter,i, (byte) hitlist.get(i).getJitter());
        
    }

    return bank;

    }   
    
    public DataBank fillHBHitsTrkIdBank(DataEvent event, List<FittedHit> hitlist) {
        String name = bankNames.getIdsBank(); 
        int rejCnt = 0;
        for (int i = 0; i < hitlist.size(); i++) {
//            if (hitlist.get(i).get_AssociatedHBTrackID() == -1 /*|| hitlist.get(i).get_Id()==0*/ //PASS1
//                    /*|| hitlist.get(i).getTFlight()==0*/) {
            if (hitlist.get(i).get_AssociatedHBTrackID() == -1 || hitlist.get(i).get_Id()==0 ){
//                    || hitlist.get(i).getTFlight()==0) {
                rejCnt++;
            }
        }
        DataBank bank = event.createBank(name, hitlist.size()-rejCnt);
        rejCnt=0;
        for (int i = 0; i < hitlist.size(); i++) {
            //output only for HOTs
//            if (hitlist.get(i).get_AssociatedHBTrackID() == -1 || hitlist.get(i).get_Id()==0 //PASS1
//                    /*|| hitlist.get(i).getTFlight()==0*/) {
            if (hitlist.get(i).get_AssociatedHBTrackID() == -1 || hitlist.get(i).get_Id()==0 ) {
//                    || hitlist.get(i).getTFlight()==0) {
                rejCnt++;
                continue;
            } 
            bank.setShort(HitBasedTrkg_HBHitTrkId.id, i-rejCnt, (short) hitlist.get(i).get_Id());
            bank.setShort(HitBasedTrkg_HBHitTrkId.tid, i-rejCnt, (short) hitlist.get(i).get_AssociatedHBTrackID());
            bank.setFloat(HitBasedTrkg_HBHitTrkId.B, i-rejCnt, (float) hitlist.get(i).getB());
            bank.setFloat(HitBasedTrkg_HBHitTrkId.TProp, i-rejCnt, (float) hitlist.get(i).getTProp());
            bank.setFloat(HitBasedTrkg_HBHitTrkId.TFlight, i-rejCnt, (float) hitlist.get(i).getTFlight());
//            if(i>0 && hitlist.get(i).getSector()==hitlist.get(i-1).getSector() && 
//                     hitlist.get(i).get_Superlayer()==hitlist.get(i-1).get_Superlayer() && 
//                     hitlist.get(i).get_Layer()==hitlist.get(i-1).get_Layer() && 
//                     Math.abs(hitlist.get(i).get_Wire()-hitlist.get(i-1).get_Wire())==1 &&
//                     hitlist.get(i-1).getTFlight()>0) {// fix for double hits
//                bank.setFloat("B", i-rejCnt, (float) hitlist.get(i-1).getB());
//                bank.setFloat("TProp", i-rejCnt, (float) hitlist.get(i-1).getTProp());
//                bank.setFloat("TFlight", i-rejCnt, (float) hitlist.get(i-1).getTFlight());
//            }
//            if(hitlist.get(i).get_AssociatedHBTrackID()>-1 && !event.hasBank("MC::Particle")) {
//                bank.setFloat("TProp", i-rejCnt, (float) hitlist.get(i).getSignalPropagTimeAlongWire());
//                bank.setFloat("TFlight", i-rejCnt, (float) hitlist.get(i).getSignalTimeOfFlight());
//                if(i>0 && hitlist.get(i).getSector()==hitlist.get(i-1).getSector() && 
//                     hitlist.get(i).get_Superlayer()==hitlist.get(i-1).get_Superlayer() && 
//                     hitlist.get(i).get_Layer()==hitlist.get(i-1).get_Layer() && 
//                     Math.abs(hitlist.get(i).get_Wire()-hitlist.get(i-1).get_Wire())==1 &&
//                     hitlist.get(i-1).getTFlight()>0) {// fix for double hits
//                    bank.setFloat("TProp", i-rejCnt, (float) hitlist.get(i-1).getSignalPropagTimeAlongWire());
//                    bank.setFloat("TFlight", i-rejCnt, (float) hitlist.get(i-1).getSignalTimeOfFlight());
//                }
//            }
        }
    //bank.show();
    return bank;

}
/**
 *
 * @param event the EvioEvent
     * @param cluslist
 * @return clusters bank
 */
public DataBank fillHBClustersBank(DataEvent event, List<FittedCluster> cluslist) {
    String name = bankNames.getClustersBank();
    DataBank bank = event.createBank(name, cluslist.size());

    int[] hitIdxArray = new int[12];
    if(cluslist==null)
        return bank;
    for (int i = 0; i < cluslist.size(); i++) {
        if (cluslist.get(i) == null || cluslist.get(i).get_Id() == -1) {
            continue;
        }
        for (int j = 0; j < hitIdxArray.length; j++) {
            hitIdxArray[j] = -1;
        }
        double chi2 = 0;

        bank.setShort(HitBasedTrkg_HBClusters.id, i, (short) cluslist.get(i).get_Id());
        int status = 0;
        if(cluslist.get(i).size()<6)
            status = 1;
        bank.setShort(HitBasedTrkg_HBClusters.status, i, (short) status);
        bank.setByte(HitBasedTrkg_HBClusters.superlayer, i, (byte) cluslist.get(i).get_Superlayer());
        bank.setByte(HitBasedTrkg_HBClusters.sector, i, (byte) cluslist.get(i).get_Sector());

        bank.setFloat(HitBasedTrkg_HBClusters.avgWire, i, (float) cluslist.get(i).getAvgwire());
        bank.setByte(HitBasedTrkg_HBClusters.size, i, (byte) cluslist.get(i).size());

        double fitSlope = cluslist.get(i).get_clusterLineFitSlope();
        double fitInterc = cluslist.get(i).get_clusterLineFitIntercept();

        bank.setFloat(HitBasedTrkg_HBClusters.fitSlope, i, (float) fitSlope);
        bank.setFloat(HitBasedTrkg_HBClusters.fitSlopeErr, i, (float) cluslist.get(i).get_clusterLineFitSlopeErr());
        bank.setFloat(HitBasedTrkg_HBClusters.fitInterc, i, (float) fitInterc);
        bank.setFloat(HitBasedTrkg_HBClusters.fitIntercErr, i, (float) cluslist.get(i).get_clusterLineFitInterceptErr());

        for (int j = 0; j < cluslist.get(i).size(); j++) {
            if (j < hitIdxArray.length) {
                hitIdxArray[j] = cluslist.get(i).get(j).get_Id();
            }

            double residual = cluslist.get(i).get(j).get_ClusFitDoca() / (cluslist.get(i).get(j).get_CellSize() / Math.sqrt(12.));
            chi2 += residual * residual;
        }
        bank.setFloat(HitBasedTrkg_HBClusters.fitChisqProb, i, (float) ProbChi2perNDF.prob(chi2, cluslist.get(i).size() - 2));

        for (int j = 0; j < hitIdxArray.length; j++) {
            String hitStrg = "Hit";
            hitStrg += (j + 1);
            hitStrg += "_ID";
            bank.setShort(hitStrg, i, (short) hitIdxArray[j]);
    }
    }

    return bank;

    }

    /**
     *
     * @param event the EvioEvent
     * @param seglist
     * @return segments bank
     */
    public DataBank fillHBSegmentsBank(DataEvent event, List<Segment> seglist) {
        String name = bankNames.getSegmentsBank();
        DataBank bank = event.createBank(name, seglist.size());
        int[] hitIdxArray = new int[12]; // only saving 12 hits for now

        for (int i = 0; i < seglist.size(); i++) {

            if (seglist.get(i).get_Id() == -1) {
                continue;
            }

            for (int j = 0; j < hitIdxArray.length; j++) {
                hitIdxArray[j] = -1;
            }

            double chi2 = 0;

            bank.setShort(HitBasedTrkg_HBSegments.id, i, (short) seglist.get(i).get_Id());
            bank.setByte(HitBasedTrkg_HBSegments.superlayer, i, (byte) seglist.get(i).get_Superlayer());
            bank.setByte(HitBasedTrkg_HBSegments.sector, i, (byte) seglist.get(i).get_Sector());

            FittedCluster cls = seglist.get(i).get_fittedCluster();
            bank.setShort(HitBasedTrkg_HBSegments.Cluster_ID, i, (short) cls.get_Id());

            bank.setFloat(HitBasedTrkg_HBSegments.avgWire, i, (float) cls.getAvgwire());
            bank.setByte(HitBasedTrkg_HBSegments.size, i, (byte) seglist.get(i).size());

            bank.setFloat(HitBasedTrkg_HBSegments.fitSlope, i, (float) cls.get_clusterLineFitSlope());
            bank.setFloat(HitBasedTrkg_HBSegments.fitSlopeErr, i, (float) cls.get_clusterLineFitSlopeErr());
            bank.setFloat(HitBasedTrkg_HBSegments.fitInterc, i, (float) cls.get_clusterLineFitIntercept());
            bank.setFloat(HitBasedTrkg_HBSegments.fitIntercErr, i, (float) cls.get_clusterLineFitInterceptErr());

            bank.setFloat(HitBasedTrkg_HBSegments.SegEndPoint1X, i, (float) seglist.get(i).get_SegmentEndPoints()[0]);
            bank.setFloat(HitBasedTrkg_HBSegments.SegEndPoint1Z, i, (float) seglist.get(i).get_SegmentEndPoints()[1]);
            bank.setFloat(HitBasedTrkg_HBSegments.SegEndPoint2X, i, (float) seglist.get(i).get_SegmentEndPoints()[2]);
            bank.setFloat(HitBasedTrkg_HBSegments.SegEndPoint2Z, i, (float) seglist.get(i).get_SegmentEndPoints()[3]);

            for (int j = 0; j < seglist.get(i).size(); j++) {
                if (seglist.get(i).get_Id() == -1) {
                    continue;
                }
                if (j < hitIdxArray.length) {
                    hitIdxArray[j] = seglist.get(i).get(j).get_Id();
                }

                double residual = seglist.get(i).get(j).get_ClusFitDoca() / (seglist.get(i).get(j).get_CellSize() / Math.sqrt(12.));
                chi2 += residual * residual;
            }
            bank.setFloat(HitBasedTrkg_HBSegments.fitChisqProb, i, (float) ProbChi2perNDF.prob(chi2, seglist.get(i).size() - 2));

            for (int j = 0; j < hitIdxArray.length; j++) {
                String hitStrg = "Hit";
                hitStrg += (j + 1);
                hitStrg += "_ID";
                bank.setShort(hitStrg, i, (short) hitIdxArray[j]);
            }
        }

        return bank;

    }

    /**
     *
     * @param event the EvioEvent
     * @param crosslist
     * @return crosses bank
     */
    public DataBank fillHBCrossesBank(DataEvent event, List<Cross> crosslist) {

        int banksize=0;
        for (Cross aCrosslist1 : crosslist) {
            if (aCrosslist1.get_Id() != -1)
                banksize++;
        }
        String name = bankNames.getCrossesBank();
        DataBank bank = event.createBank(name, banksize); 

        int index=0;
        for (Cross aCrosslist : crosslist) {
            if (aCrosslist.get_Id() != -1) {
                bank.setShort(HitBasedTrkg_HBCrosses.id, index, (short) aCrosslist.get_Id());
                bank.setShort(HitBasedTrkg_HBCrosses.status, index, (short) 0);
                bank.setByte(HitBasedTrkg_HBCrosses.sector, index, (byte) aCrosslist.get_Sector());
                bank.setByte(HitBasedTrkg_HBCrosses.region, index, (byte) aCrosslist.get_Region());
                bank.setFloat(HitBasedTrkg_HBCrosses.x, index, (float) aCrosslist.get_Point().x());
                bank.setFloat(HitBasedTrkg_HBCrosses.y, index, (float) aCrosslist.get_Point().y());
                bank.setFloat(HitBasedTrkg_HBCrosses.z, index, (float) aCrosslist.get_Point().z());
                bank.setFloat(HitBasedTrkg_HBCrosses.err_x, index, (float) aCrosslist.get_PointErr().x());
                bank.setFloat(HitBasedTrkg_HBCrosses.err_y, index, (float) aCrosslist.get_PointErr().y());
                bank.setFloat(HitBasedTrkg_HBCrosses.err_z, index, (float) aCrosslist.get_PointErr().z());
                bank.setFloat(HitBasedTrkg_HBCrosses.ux, index, (float) aCrosslist.get_Dir().x());
                bank.setFloat(HitBasedTrkg_HBCrosses.uy, index, (float) aCrosslist.get_Dir().y());
                bank.setFloat(HitBasedTrkg_HBCrosses.uz, index, (float) aCrosslist.get_Dir().z());
                bank.setFloat(HitBasedTrkg_HBCrosses.err_ux, index, (float) aCrosslist.get_DirErr().x());
                bank.setFloat(HitBasedTrkg_HBCrosses.err_uy, index, (float) aCrosslist.get_DirErr().y());
                bank.setFloat(HitBasedTrkg_HBCrosses.err_uz, index, (float) aCrosslist.get_DirErr().z());
                bank.setShort(HitBasedTrkg_HBCrosses.Segment1_ID, index, (short) aCrosslist.get_Segment1().get_Id());
                bank.setShort(HitBasedTrkg_HBCrosses.Segment2_ID, index, (short) aCrosslist.get_Segment2().get_Id());
                index++;
            }
        }
        return bank;
    }
    
    public DataBank fillHBTracksBank(DataEvent event, List<Track> candlist) {
        String name = bankNames.getTracksBank();
        DataBank bank = event.createBank(name, candlist.size()); 

        for (int i = 0; i < candlist.size(); i++) {
            bank.setShort(HitBasedTrkg_HBTracks.id, i, (short) candlist.get(i).get_Id());
            bank.setByte(HitBasedTrkg_HBTracks.sector, i, (byte) candlist.get(i).getSector());
            bank.setByte(HitBasedTrkg_HBTracks.q, i, (byte) candlist.get(i).get_Q());
            bank.setShort(HitBasedTrkg_HBTracks.status, i, (short) candlist.get(i).getBitStatus());
            if(candlist.get(i).get_PreRegion1CrossPoint()!=null) {
                bank.setFloat(HitBasedTrkg_HBTracks.c1_x, i, (float) candlist.get(i).get_PreRegion1CrossPoint().x());
                bank.setFloat(HitBasedTrkg_HBTracks.c1_y, i, (float) candlist.get(i).get_PreRegion1CrossPoint().y());
                bank.setFloat(HitBasedTrkg_HBTracks.c1_z, i, (float) candlist.get(i).get_PreRegion1CrossPoint().z());
                bank.setFloat(HitBasedTrkg_HBTracks.c1_ux, i, (float) candlist.get(i).get_PreRegion1CrossDir().x());
                bank.setFloat(HitBasedTrkg_HBTracks.c1_uy, i, (float) candlist.get(i).get_PreRegion1CrossDir().y());
                bank.setFloat(HitBasedTrkg_HBTracks.c1_uz, i, (float) candlist.get(i).get_PreRegion1CrossDir().z());
            }
            if(candlist.get(i).get_PostRegion3CrossPoint()!=null) {
                bank.setFloat(HitBasedTrkg_HBTracks.c3_x, i, (float) candlist.get(i).get_PostRegion3CrossPoint().x());
                bank.setFloat(HitBasedTrkg_HBTracks.c3_y, i, (float) candlist.get(i).get_PostRegion3CrossPoint().y());
                bank.setFloat(HitBasedTrkg_HBTracks.c3_z, i, (float) candlist.get(i).get_PostRegion3CrossPoint().z());
                bank.setFloat(HitBasedTrkg_HBTracks.c3_ux, i, (float) candlist.get(i).get_PostRegion3CrossDir().x());
                bank.setFloat(HitBasedTrkg_HBTracks.c3_uy, i, (float) candlist.get(i).get_PostRegion3CrossDir().y());
                bank.setFloat(HitBasedTrkg_HBTracks.c3_uz, i, (float) candlist.get(i).get_PostRegion3CrossDir().z());
            }
            if(candlist.get(i).get_Region1TrackX()!=null) {
                bank.setFloat(HitBasedTrkg_HBTracks.t1_x, i, (float) candlist.get(i).get_Region1TrackX().x());
                bank.setFloat(HitBasedTrkg_HBTracks.t1_y, i, (float) candlist.get(i).get_Region1TrackX().y());
                bank.setFloat(HitBasedTrkg_HBTracks.t1_z, i, (float) candlist.get(i).get_Region1TrackX().z());
                bank.setFloat(HitBasedTrkg_HBTracks.t1_px, i, (float) candlist.get(i).get_Region1TrackP().x());
                bank.setFloat(HitBasedTrkg_HBTracks.t1_py, i, (float) candlist.get(i).get_Region1TrackP().y());
                bank.setFloat(HitBasedTrkg_HBTracks.t1_pz, i, (float) candlist.get(i).get_Region1TrackP().z());
            }
            bank.setFloat(HitBasedTrkg_HBTracks.pathlength, i, (float) candlist.get(i).get_TotPathLen());
            bank.setFloat(HitBasedTrkg_HBTracks.Vtx0_x, i, (float) candlist.get(i).get_Vtx0().x());
            bank.setFloat(HitBasedTrkg_HBTracks.Vtx0_y, i, (float) candlist.get(i).get_Vtx0().y());
            bank.setFloat(HitBasedTrkg_HBTracks.Vtx0_z, i, (float) candlist.get(i).get_Vtx0().z());
            bank.setFloat(HitBasedTrkg_HBTracks.p0_x, i, (float) candlist.get(i).get_pAtOrig().x());
            bank.setFloat(HitBasedTrkg_HBTracks.p0_y, i, (float) candlist.get(i).get_pAtOrig().y());
            bank.setFloat(HitBasedTrkg_HBTracks.p0_z, i, (float) candlist.get(i).get_pAtOrig().z());
            //fill associated IDs
            for(int r = 0; r < 3; r++) {
                bank.setShort("Cross"+String.valueOf(r+1)+"_ID", 
                    i, (short) -1);
            }
            for(int r = 0; r < 6; r++) {
                bank.setShort("Cluster"+String.valueOf(r+1)+"_ID", 
                    i, (short) -1);
            }
            for(int k = 0; k < candlist.get(i).size(); k++) {
                bank.setShort("Cross"+String.valueOf(candlist.get(i).get(k).get_Region())+"_ID", 
                    i, (short) candlist.get(i).get(k).get_Id());
                bank.setShort("Cluster"+String.valueOf(candlist.get(i).get(k).get_Region()*2-1)+"_ID", 
                        i, (short) candlist.get(i).get(k).get_Segment1().get_Id());
                bank.setShort("Cluster"+String.valueOf(candlist.get(i).get(k).get_Region()*2)+"_ID", 
                        i, (short) candlist.get(i).get(k).get_Segment2().get_Id());
            }
            if(candlist.get(i).getSingleSuperlayer()!=null) {
                bank.setShort("Cluster"+String.valueOf(candlist.get(i).getSingleSuperlayer().get_Superlayer())+"_ID", 
                        i, (short) candlist.get(i).getSingleSuperlayer().get_fittedCluster().get_Id());
            }
                
            bank.setFloat(HitBasedTrkg_HBTracks.chi2, i, (float) candlist.get(i).get_FitChi2());
            bank.setShort(HitBasedTrkg_HBTracks.ndf, i, (short) candlist.get(i).get_FitNDF());
            bank.setFloat(HitBasedTrkg_HBTracks.x, i, (float) candlist.get(i).getFinalStateVec().x());
            bank.setFloat(HitBasedTrkg_HBTracks.y, i, (float) candlist.get(i).getFinalStateVec().y());
            bank.setFloat(HitBasedTrkg_HBTracks.z, i, (float) candlist.get(i).getFinalStateVec().getZ());
            bank.setFloat(HitBasedTrkg_HBTracks.tx, i, (float) candlist.get(i).getFinalStateVec().tanThetaX());
            bank.setFloat(HitBasedTrkg_HBTracks.ty, i, (float) candlist.get(i).getFinalStateVec().tanThetaY());
            
        }
        //bank.show();
        return bank;
    }
        
    public DataBank fillHBTrajectoryBank(DataEvent event, List<Track> candlist) {
        return this.fillTrajectoryBank(event, candlist);
    }
        
    /**
     *
     * @param event hipo event
     * @param candlist tracks
     * @return covariance matrix for momentum and vertex in lab frame
     */
    private DataBank fillTrackCovMatLabBank(DataEvent event, List<Track> candlist) {

        DataBank bank = event.createBank(bankNames.getCovmatBank(), candlist.size());

        for (int i = 0; i < candlist.size(); i++) {
            bank.setShort(TimeBasedTrkg_TBCovMat.id, i, (short) candlist.get(i).get_Id());
            if(candlist.get(i).get_CMInLab()!=null) {
                double[][] CM = candlist.get(i).get_CMInLab();
                bank.setFloat(TimeBasedTrkg_TBCovMat.C11, i, (float) CM[0][0]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C12, i, (float) CM[0][1]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C13, i, (float) CM[0][2]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C14, i, (float) CM[0][3]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C15, i, (float) CM[0][4]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C16, i, (float) CM[0][5]);
                
                bank.setFloat(TimeBasedTrkg_TBCovMat.C21, i, (float) CM[1][0]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C22, i, (float) CM[1][1]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C23, i, (float) CM[1][2]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C24, i, (float) CM[1][3]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C25, i, (float) CM[1][4]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C26, i, (float) CM[1][5]);
                
                bank.setFloat(TimeBasedTrkg_TBCovMat.C31, i, (float) CM[2][0]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C32, i, (float) CM[2][1]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C33, i, (float) CM[2][2]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C34, i, (float) CM[2][3]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C35, i, (float) CM[2][4]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C36, i, (float) CM[2][5]);
                
                bank.setFloat(TimeBasedTrkg_TBCovMat.C41, i, (float) CM[3][0]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C42, i, (float) CM[3][1]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C43, i, (float) CM[3][2]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C44, i, (float) CM[3][3]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C45, i, (float) CM[3][4]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C46, i, (float) CM[3][5]);
                
                bank.setFloat(TimeBasedTrkg_TBCovMat.C51, i, (float) CM[4][0]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C52, i, (float) CM[4][1]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C53, i, (float) CM[4][2]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C54, i, (float) CM[4][3]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C55, i, (float) CM[4][4]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C56, i, (float) CM[4][5]);
                
                bank.setFloat(TimeBasedTrkg_TBCovMat.C61, i, (float) CM[5][0]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C62, i, (float) CM[5][1]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C63, i, (float) CM[5][2]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C64, i, (float) CM[5][3]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C65, i, (float) CM[5][4]);
                bank.setFloat(TimeBasedTrkg_TBCovMat.C66, i, (float) CM[5][5]);
               
            }
        }
        //bank.show();
        return bank;
    }
    
     /**
     *
     * @param event the EvioEvent
     * @return hits bank
     *
     */
     private DataBank fillTBHitsBank(DataEvent event, List<FittedHit> hitlist) {
        String name = bankNames.getHitsBank();
        DataBank bank = event.createBank(name, hitlist.size());
        
        for (int i = 0; i < hitlist.size(); i++) {
            if (hitlist.get(i).get_Id() == -1) {
                continue;
            }
            if(hitlist.get(i).get_TrkResid()==999)
                hitlist.get(i).set_AssociatedTBTrackID(-1);
            bank.setShort("id", i, (short) hitlist.get(i).get_Id());
            bank.setShort("status", i, (short) hitlist.get(i).get_QualityFac());
            bank.setByte("superlayer", i, (byte) hitlist.get(i).get_Superlayer());
            bank.setByte("layer", i, (byte) hitlist.get(i).get_Layer());
            bank.setByte("sector", i, (byte) hitlist.get(i).get_Sector());
            bank.setShort("wire", i, (short) hitlist.get(i).get_Wire());

            bank.setFloat("X", i, (float) hitlist.get(i).get_X());
            bank.setFloat("Z", i, (float) hitlist.get(i).get_Z());
            bank.setByte("LR", i, (byte) hitlist.get(i).get_LeftRightAmb());

            if(bank.getDescriptor().hasEntry("time")){
               bank.setFloat("time", i, (float) (hitlist.get(i).get_Time() - hitlist.get(i).get_DeltaTimeBeta()));
            }
            if(bank.getDescriptor().hasEntry("tBeta")){
               bank.setFloat("tBeta", i, (float) hitlist.get(i).get_DeltaTimeBeta());
            }
            if(bank.getDescriptor().hasEntry("dDoca")){
               bank.setFloat("dDoca", i, (float) hitlist.get(i).get_DeltaDocaBeta());
            }
            if(bank.getDescriptor().hasEntry("fitResidual")){
               bank.setFloat("fitResidual", i, (float) hitlist.get(i).get_TrkResid());
            }
            if(bank.getDescriptor().hasEntry("Alpha")){
               bank.setFloat("Alpha", i, (float) hitlist.get(i).getAlpha());
            }
            bank.setFloat("doca", i, (float) hitlist.get(i).get_Doca());
            bank.setFloat("docaError", i, (float) hitlist.get(i).get_DocaErr());
            bank.setFloat("trkDoca", i, (float) hitlist.get(i).get_ClusFitDoca());

            bank.setShort("clusterID", i, (short) hitlist.get(i).get_AssociatedClusterID());
            bank.setByte("trkID", i, (byte) hitlist.get(i).get_AssociatedTBTrackID());
            bank.setFloat("timeResidual", i, (float) hitlist.get(i).get_TimeResidual());
            bank.setFloat("DAFWeight", i, (float) hitlist.get(i).getDAFWeight());
            
            bank.setInt("TDC",i,hitlist.get(i).get_TDC());
            bank.setByte("jitter",i, (byte) hitlist.get(i).getJitter());
            bank.setFloat("B", i, (float) hitlist.get(i).getB());
            bank.setFloat("TProp", i, (float) hitlist.get(i).getTProp());
            bank.setFloat("TFlight", i, (float) hitlist.get(i).getTFlight());
            bank.setFloat("T0", i, (float) hitlist.get(i).getT0());
            bank.setFloat("TStart", i, (float) hitlist.get(i).getTStart());
            if(bank.getDescriptor().hasEntry("beta")){
               bank.setFloat("beta", i, (float) hitlist.get(i).get_Beta());
            }
            if(hitlist.get(i).get_AssociatedTBTrackID()>-1) {
                if(hitlist.get(i).getSignalPropagTimeAlongWire()==0 || hitlist.get(i).get_AssociatedTBTrackID()<1) {
                    bank.setFloat("TProp", i, (float) hitlist.get(i).getTProp()); //old value if track fit failed
                } else {
                    bank.setFloat("TProp", i, (float) hitlist.get(i).getSignalPropagTimeAlongWire()); //new calculated value
                }
                if(hitlist.get(i).getSignalTimeOfFlight()==0 || hitlist.get(i).get_AssociatedTBTrackID()<1) {
                    bank.setFloat("TFlight", i, (float) hitlist.get(i).getTFlight());
                } else {
                    bank.setFloat("TFlight", i, (float) hitlist.get(i).getSignalTimeOfFlight());
                }
            }

        }
        return bank;

    }

    /**
     *
     * @param event the EvioEvent
     * @return clusters bank
     */
    private DataBank fillTBClustersBank(DataEvent event, List<FittedCluster> cluslist) {
        String name = bankNames.getClustersBank();
        DataBank bank = event.createBank(name, cluslist.size());
        
        int[] hitIdxArray = new int[12];

        for (int i = 0; i < cluslist.size(); i++) {
            if (cluslist.get(i).get_Id() == -1) {
                continue;
            }
            for (int j = 0; j < hitIdxArray.length; j++) {
                hitIdxArray[j] = -1;
            }
            double chi2 = 0;

            bank.setShort(TimeBasedTrkg_TBClusters.id, i, (short) cluslist.get(i).get_Id());
//            int status =0;
//            if(cluslist.get(i).size()<6)
//                status = 1;
            bank.setShort(TimeBasedTrkg_TBClusters.status, i, (short) 0);
            bank.setByte(TimeBasedTrkg_TBClusters.superlayer, i, (byte) cluslist.get(i).get_Superlayer());
            bank.setByte(TimeBasedTrkg_TBClusters.sector, i, (byte) cluslist.get(i).get_Sector());

            bank.setFloat(TimeBasedTrkg_TBClusters.avgWire, i, (float) cluslist.get(i).getAvgwire());
            bank.setByte(TimeBasedTrkg_TBClusters.size, i, (byte) cluslist.get(i).size());

            double fitSlope = cluslist.get(i).get_clusterLineFitSlope();
            double fitInterc = cluslist.get(i).get_clusterLineFitIntercept();

            bank.setFloat(TimeBasedTrkg_TBClusters.fitSlope, i, (float) fitSlope);
            bank.setFloat(TimeBasedTrkg_TBClusters.fitSlopeErr, i, (float) cluslist.get(i).get_clusterLineFitSlopeErr());
            bank.setFloat(TimeBasedTrkg_TBClusters.fitInterc, i, (float) fitInterc);
            bank.setFloat(TimeBasedTrkg_TBClusters.fitIntercErr, i, (float) cluslist.get(i).get_clusterLineFitInterceptErr());

            for (int j = 0; j < cluslist.get(i).size(); j++) {
                if (j < hitIdxArray.length) {
                    hitIdxArray[j] = cluslist.get(i).get(j).get_Id();
                }

                double residual = cluslist.get(i).get(j).get_ClusFitDoca() / (cluslist.get(i).get(j).get_CellSize() / Math.sqrt(12.));
                chi2 += residual * residual;
            }
            bank.setFloat(TimeBasedTrkg_TBClusters.fitChisqProb, i, (float) ProbChi2perNDF.prob(chi2, cluslist.get(i).size() - 2));

            for (int j = 0; j < hitIdxArray.length; j++) {
                String hitStrg = "Hit";
                hitStrg += (j + 1);
                hitStrg += "_ID";
                bank.setShort(hitStrg, i, (short) hitIdxArray[j]);
            }
        }

        return bank;

    }

    /**
     *
     * @param event the EvioEvent
     * @return segments bank
     */
    private DataBank fillTBSegmentsBank(DataEvent event, List<Segment> seglist) {
        String name = bankNames.getSegmentsBank();
        DataBank bank = event.createBank(name, seglist.size());
        
        int[] hitIdxArray = new int[12];

        for (int i = 0; i < seglist.size(); i++) {
            if (seglist.get(i).get_Id() == -1) {
                continue;
            }

            for (int j = 0; j < hitIdxArray.length; j++) {
                hitIdxArray[j] = -1;
            }

            double chi2 = 0;

            bank.setShort(TimeBasedTrkg_TBSegments.id, i, (short) seglist.get(i).get_Id());
            bank.setShort(TimeBasedTrkg_TBSegments.status, i, (short) seglist.get(i).get_Status());
            bank.setByte(TimeBasedTrkg_TBSegments.superlayer, i, (byte) seglist.get(i).get_Superlayer());
            bank.setByte(TimeBasedTrkg_TBSegments.sector, i, (byte) seglist.get(i).get_Sector());
            FittedCluster cls = seglist.get(i).get_fittedCluster();
            bank.setShort(TimeBasedTrkg_TBSegments.Cluster_ID, i, (short) cls.get_Id());

            bank.setFloat(TimeBasedTrkg_TBSegments.avgWire, i, (float) cls.getAvgwire());
            bank.setByte(TimeBasedTrkg_TBSegments.size, i, (byte) seglist.get(i).size());
            bank.setFloat(TimeBasedTrkg_TBSegments.fitSlope, i, (float) cls.get_clusterLineFitSlope());
            bank.setFloat(TimeBasedTrkg_TBSegments.fitSlopeErr, i, (float) cls.get_clusterLineFitSlopeErr());
            bank.setFloat(TimeBasedTrkg_TBSegments.fitInterc, i, (float) cls.get_clusterLineFitIntercept());
            bank.setFloat(TimeBasedTrkg_TBSegments.fitIntercErr, i, (float) cls.get_clusterLineFitInterceptErr());
            bank.setFloat(TimeBasedTrkg_TBSegments.resiSum, i, (float) seglist.get(i).get_ResiSum());
            bank.setFloat(TimeBasedTrkg_TBSegments.timeSum, i, (float) seglist.get(i).get_TimeSum());
            bank.setFloat(TimeBasedTrkg_TBSegments.SegEndPoint1X, i, (float) seglist.get(i).get_SegmentEndPoints()[0]);
            bank.setFloat(TimeBasedTrkg_TBSegments.SegEndPoint1Z, i, (float) seglist.get(i).get_SegmentEndPoints()[1]);
            bank.setFloat(TimeBasedTrkg_TBSegments.SegEndPoint2X, i, (float) seglist.get(i).get_SegmentEndPoints()[2]);
            bank.setFloat(TimeBasedTrkg_TBSegments.SegEndPoint2Z, i, (float) seglist.get(i).get_SegmentEndPoints()[3]);

            for (int j = 0; j < seglist.get(i).size(); j++) {
                if (j < hitIdxArray.length) {
                    hitIdxArray[j] = seglist.get(i).get(j).get_Id();
                }

                double residual = seglist.get(i).get(j).get_ClusFitDoca() / (seglist.get(i).get(j).get_CellSize() / Math.sqrt(12.));
                chi2 += residual * residual;
            }
            bank.setFloat(TimeBasedTrkg_TBSegments.fitChisqProb, i, (float) ProbChi2perNDF.prob(chi2, seglist.get(i).size() - 2));

            for (int j = 0; j < hitIdxArray.length; j++) {
                String hitStrg = "Hit";
                hitStrg += (j + 1);
                hitStrg += "_ID";
                bank.setShort(hitStrg, i, (short) hitIdxArray[j]);
            }
        }

        return bank;

    }


    /**
     *
     * @param event the EvioEvent
     * @return crosses bank
     */
    private DataBank fillTBCrossesBank(DataEvent event, List<Cross> crosslist) {
        int banksize=0;
        for (Cross aCrosslist1 : crosslist) {
            if (aCrosslist1.get_Id() != -1)
                banksize++;
        }
        String name = bankNames.getCrossesBank();
        DataBank bank = event.createBank(name, banksize);
        
        int index=0;
        for (Cross aCrosslist : crosslist) {
            if (aCrosslist.get_Id() != -1) {
                bank.setShort(TimeBasedTrkg_TBCrosses.id, index, (short) aCrosslist.get_Id());
                bank.setShort(TimeBasedTrkg_TBCrosses.status, index, (short) (aCrosslist.get_Segment1().get_Status() + aCrosslist.get_Segment2().get_Status()));
                bank.setByte(TimeBasedTrkg_TBCrosses.sector, index, (byte) aCrosslist.get_Sector());
                bank.setByte(TimeBasedTrkg_TBCrosses.region, index, (byte) aCrosslist.get_Region());
                bank.setFloat(TimeBasedTrkg_TBCrosses.x, index, (float) aCrosslist.get_Point().x());
                bank.setFloat(TimeBasedTrkg_TBCrosses.y, index, (float) aCrosslist.get_Point().y());
                bank.setFloat(TimeBasedTrkg_TBCrosses.z, index, (float) aCrosslist.get_Point().z());
                bank.setFloat(TimeBasedTrkg_TBCrosses.err_x, index, (float) aCrosslist.get_PointErr().x());
                bank.setFloat(TimeBasedTrkg_TBCrosses.err_y, index, (float) aCrosslist.get_PointErr().y());
                bank.setFloat(TimeBasedTrkg_TBCrosses.err_z, index, (float) aCrosslist.get_PointErr().z());
                bank.setFloat(TimeBasedTrkg_TBCrosses.ux, index, (float) aCrosslist.get_Dir().x());
                bank.setFloat(TimeBasedTrkg_TBCrosses.uy, index, (float) aCrosslist.get_Dir().y());
                bank.setFloat(TimeBasedTrkg_TBCrosses.uz, index, (float) aCrosslist.get_Dir().z());
                bank.setFloat(TimeBasedTrkg_TBCrosses.err_ux, index, (float) aCrosslist.get_DirErr().x());
                bank.setFloat(TimeBasedTrkg_TBCrosses.err_uy, index, (float) aCrosslist.get_DirErr().y());
                bank.setFloat(TimeBasedTrkg_TBCrosses.err_uz, index, (float) aCrosslist.get_DirErr().z());
                bank.setShort(TimeBasedTrkg_TBCrosses.Segment1_ID, index, (short) aCrosslist.get_Segment1().get_Id());
                bank.setShort(TimeBasedTrkg_TBCrosses.Segment2_ID, index, (short) aCrosslist.get_Segment2().get_Id());
                index++;
            }
        }
        return bank;
    }

    /**
     *
     * @param event the EvioEvent
     * @return segments bank
     */
    private DataBank fillTBTracksBank(DataEvent event, List<Track> candlist) {
       
        String name = bankNames.getTracksBank(); 
        DataBank bank = event.createBank(name, candlist.size());
        for (int i = 0; i < candlist.size(); i++) {
            bank.setShort(TimeBasedTrkg_TBTracks.id, i, (short) candlist.get(i).get_Id());
            bank.setShort(TimeBasedTrkg_TBTracks.status, i, (short) candlist.get(i).getBitStatus());
            bank.setByte(TimeBasedTrkg_TBTracks.sector, i, (byte) candlist.get(i).getSector());
            bank.setByte(TimeBasedTrkg_TBTracks.q, i, (byte) candlist.get(i).get_Q());
            if(candlist.get(i).get_PreRegion1CrossPoint()!=null) {
                bank.setFloat(TimeBasedTrkg_TBTracks.c1_x, i, (float) candlist.get(i).get_PreRegion1CrossPoint().x());
                bank.setFloat(TimeBasedTrkg_TBTracks.c1_y, i, (float) candlist.get(i).get_PreRegion1CrossPoint().y());
                bank.setFloat(TimeBasedTrkg_TBTracks.c1_z, i, (float) candlist.get(i).get_PreRegion1CrossPoint().z());
                bank.setFloat(TimeBasedTrkg_TBTracks.c1_ux, i, (float) candlist.get(i).get_PreRegion1CrossDir().x());
                bank.setFloat(TimeBasedTrkg_TBTracks.c1_uy, i, (float) candlist.get(i).get_PreRegion1CrossDir().y());
                bank.setFloat(TimeBasedTrkg_TBTracks.c1_uz, i, (float) candlist.get(i).get_PreRegion1CrossDir().z());
            }
            if(candlist.get(i).get_PostRegion3CrossPoint()!=null) {
                bank.setFloat(TimeBasedTrkg_TBTracks.c3_x, i, (float) candlist.get(i).get_PostRegion3CrossPoint().x());
                bank.setFloat(TimeBasedTrkg_TBTracks.c3_y, i, (float) candlist.get(i).get_PostRegion3CrossPoint().y());
                bank.setFloat(TimeBasedTrkg_TBTracks.c3_z, i, (float) candlist.get(i).get_PostRegion3CrossPoint().z());
                bank.setFloat(TimeBasedTrkg_TBTracks.c3_ux, i, (float) candlist.get(i).get_PostRegion3CrossDir().x());
                bank.setFloat(TimeBasedTrkg_TBTracks.c3_uy, i, (float) candlist.get(i).get_PostRegion3CrossDir().y());
                bank.setFloat(TimeBasedTrkg_TBTracks.c3_uz, i, (float) candlist.get(i).get_PostRegion3CrossDir().z());
            }
            if(candlist.get(i).get_Region1TrackX()!=null) {
                bank.setFloat(TimeBasedTrkg_TBTracks.t1_x, i, (float) candlist.get(i).get_Region1TrackX().x());
                bank.setFloat(TimeBasedTrkg_TBTracks.t1_y, i, (float) candlist.get(i).get_Region1TrackX().y());
                bank.setFloat(TimeBasedTrkg_TBTracks.t1_z, i, (float) candlist.get(i).get_Region1TrackX().z());
                bank.setFloat(TimeBasedTrkg_TBTracks.t1_px, i, (float) candlist.get(i).get_Region1TrackP().x());
                bank.setFloat(TimeBasedTrkg_TBTracks.t1_py, i, (float) candlist.get(i).get_Region1TrackP().y());
                bank.setFloat(TimeBasedTrkg_TBTracks.t1_pz, i, (float) candlist.get(i).get_Region1TrackP().z());
            }
            
            bank.setFloat(TimeBasedTrkg_TBTracks.pathlength, i, (float) candlist.get(i).get_TotPathLen());
            bank.setFloat(TimeBasedTrkg_TBTracks.Vtx0_x, i, (float) candlist.get(i).get_Vtx0().x());
            bank.setFloat(TimeBasedTrkg_TBTracks.Vtx0_y, i, (float) candlist.get(i).get_Vtx0().y());
            bank.setFloat(TimeBasedTrkg_TBTracks.Vtx0_z, i, (float) candlist.get(i).get_Vtx0().z());
            bank.setFloat(TimeBasedTrkg_TBTracks.p0_x, i, (float) candlist.get(i).get_pAtOrig().x());
            bank.setFloat(TimeBasedTrkg_TBTracks.p0_y, i, (float) candlist.get(i).get_pAtOrig().y());
            bank.setFloat(TimeBasedTrkg_TBTracks.p0_z, i, (float) candlist.get(i).get_pAtOrig().z());
           //fill associated IDs
            for(int r = 0; r < 3; r++) {
                bank.setShort("Cross"+String.valueOf(r+1)+"_ID", 
                    i, (short) -1);
            }
            for(int r = 0; r < 6; r++) {
                bank.setShort("Cluster"+String.valueOf(r+1)+"_ID", 
                    i, (short) -1);
            }
            for(int k = 0; k < candlist.get(i).size(); k++) {
                bank.setShort("Cross"+String.valueOf(candlist.get(i).get(k).get_Region())+"_ID", 
                    i, (short) candlist.get(i).get(k).get_Id());
                bank.setShort("Cluster"+String.valueOf(candlist.get(i).get(k).get_Region()*2-1)+"_ID", 
                        i, (short) candlist.get(i).get(k).get_Segment1().get_Id());
                bank.setShort("Cluster"+String.valueOf(candlist.get(i).get(k).get_Region()*2)+"_ID", 
                        i, (short) candlist.get(i).get(k).get_Segment2().get_Id());
            }
            if(candlist.get(i).getSingleSuperlayer()!=null) {
                bank.setShort("Cluster"+String.valueOf(candlist.get(i).getSingleSuperlayer().get_Superlayer())+"_ID", 
                        i, (short) candlist.get(i).getSingleSuperlayer().get_fittedCluster().get_Id());
            }
            bank.setFloat(TimeBasedTrkg_TBTracks.chi2, i, (float) candlist.get(i).get_FitChi2());
            // To not interrupt current type of ndf, ndf weighted by DAF is converted from float to interger
            int ndfDAF = 999;
            if(candlist.get(i).get_NDFDAF() > 0){
                ndfDAF = (int) Math.ceil(candlist.get(i).get_NDFDAF());
            }
            else if (candlist.get(i).get_NDFDAF() < 0){
                ndfDAF = (int) Math.floor(candlist.get(i).get_NDFDAF());
            }
            bank.setShort(TimeBasedTrkg_TBTracks.ndf, i, (short) ndfDAF);
            // ndf0 is for traditional ndf for the track; # of hits can be obtained through it
            bank.setShort(TimeBasedTrkg_TBTracks.ndf0, i, (short) candlist.get(i).get_FitNDF());
        }
        return bank;

    }

    private DataBank fillTrajectoryBank(DataEvent event, List<Track> tracks) {
        int size=0;
        for (Track track : tracks) {
            if (track == null)
                continue;
            if (track.getTrajectory() == null)
                continue;
            size+=track.getTrajectory().size();
        }       
        DataBank bank = event.createBank(bankNames.getTrajBank(), size);
        int i1=0;
        for (Track track : tracks) {
            if (track == null)
                continue;
            if (track.getTrajectory() == null)
                continue;

            for (int j = 0; j < track.getTrajectory().size(); j++) {
                if (track.getTrajectory().get(j).getDetector() == DetectorType.DC.getDetectorId() && (track.getTrajectory().get(j).getLayer() - 6) % 6 != 0)
                    continue;  // save the last layer in a superlayer

                bank.setShort(TimeBasedTrkg_Trajectory.id,       i1, (short) track.get_Id());
                bank.setByte(TimeBasedTrkg_Trajectory.detector,  i1, (byte) track.getTrajectory().get(j).getDetector());
                bank.setByte(TimeBasedTrkg_Trajectory.sector,    i1, (byte) track.getSector());
                bank.setByte(TimeBasedTrkg_Trajectory.layer,     i1, (byte) track.getTrajectory().get(j).getLayer());
                bank.setFloat(TimeBasedTrkg_Trajectory.x,        i1, (float) track.getTrajectory().get(j).getPoint().x());
                bank.setFloat(TimeBasedTrkg_Trajectory.y,        i1, (float) track.getTrajectory().get(j).getPoint().y());
                bank.setFloat(TimeBasedTrkg_Trajectory.z,        i1, (float) track.getTrajectory().get(j).getPoint().z());
                bank.setFloat(TimeBasedTrkg_Trajectory.tx,       i1, (float) track.getTrajectory().get(j).getDirection().x());
                bank.setFloat(TimeBasedTrkg_Trajectory.ty,       i1, (float) track.getTrajectory().get(j).getDirection().y());
                bank.setFloat(TimeBasedTrkg_Trajectory.tz,       i1, (float) track.getTrajectory().get(j).getDirection().z());
                bank.setFloat(TimeBasedTrkg_Trajectory.B,        i1, (float) track.getTrajectory().get(j).getiBdl());
                bank.setFloat(TimeBasedTrkg_Trajectory.path,     i1, (float) track.getTrajectory().get(j).getPath());
                bank.setFloat(TimeBasedTrkg_Trajectory.dx,       i1, (float) track.getTrajectory().get(j).getDx());
                bank.setFloat(TimeBasedTrkg_Trajectory.edge,     i1, (float) track.getTrajectory().get(j).getEdge());
                i1++;
            }
        }
        return bank;
    }

    public List<FittedHit> createRawHitList(List<Hit> hits) {

        List<FittedHit> fhits = new ArrayList<>();

        for (Hit hit : hits) {
            FittedHit fhit = new FittedHit(hit.get_Sector(), hit.get_Superlayer(),
                    hit.get_Layer(), hit.get_Wire(), hit.get_TDC(), hit.getJitter(),
                    hit.get_Id());
            fhit.set_Id(hit.get_Id());
            fhit.set_IndexTDC(hit.get_IndexTDC());
            fhit.set_DocaErr(hit.get_DocaErr());
            fhits.add(fhit);
        }
        return fhits;
    }

    public void fillAllHBBanks(DataEvent event, List<FittedHit> fhits, List<FittedCluster> clusters,
            List<Segment> segments, List<Cross> crosses,
            List<Track> trkcands) {

        if (event == null) {
            return;
        }

        if (trkcands != null) {
            event.appendBanks(this.fillHBHitsBank(event, fhits),
                    this.fillHBClustersBank(event, clusters),
                    this.fillHBSegmentsBank(event, segments),
                    this.fillHBCrossesBank(event, crosses),
                    this.fillHBTracksBank(event, trkcands),
                    this.fillHBHitsTrkIdBank(event, fhits)
                    //this.fillTrackCovMatBank(event, trkcands)
            );

        }
        else if (crosses != null && trkcands == null) {
            event.appendBanks(this.fillHBHitsBank(event, fhits),
                    this.fillHBClustersBank(event, clusters),
                    this.fillHBSegmentsBank(event, segments),
                    this.fillHBCrossesBank(event, crosses)
            );
        }
        else if (segments != null && crosses == null) {
            event.appendBanks(this.fillHBHitsBank(event, fhits),
                    this.fillHBClustersBank(event, clusters),
                    this.fillHBSegmentsBank(event, segments)
            );
        }
        else if (clusters != null && segments == null) {

            event.appendBanks(this.fillHBHitsBank(event, fhits),
                    this.fillHBClustersBank(event, clusters)
            );
        }
        else if (fhits != null && clusters == null) {
            event.appendBanks(this.fillHBHitsBank(event, fhits)
            );
        }
    }
    
    public void fillAllTBBanks(DataEvent event, List<FittedHit> fhits, List<FittedCluster> clusters,
            List<Segment> segments, List<Cross> crosses, 
            List<Track> trkcands) {

        if (event == null) {
            return;
        }

        if (trkcands != null) {
            event.appendBanks(this.fillTBHitsBank(event, fhits),
                    this.fillTBClustersBank(event, clusters),
                    this.fillTBSegmentsBank(event, segments),
                    this.fillTBCrossesBank(event, crosses),
                    this.fillTBTracksBank(event, trkcands),
                    this.fillTrajectoryBank(event, trkcands),
                    this.fillTrackCovMatLabBank(event, trkcands)
                    );
        }
        if (crosses != null && trkcands == null) {
            event.appendBanks(this.fillTBHitsBank(event, fhits),
                    this.fillTBClustersBank(event, clusters),
                    this.fillTBSegmentsBank(event, segments),
                    this.fillTBCrossesBank(event, crosses));
        }
        if (segments != null && crosses == null) {
            event.appendBanks(this.fillTBHitsBank(event, fhits),
                    this.fillTBClustersBank(event, clusters),
                    this.fillTBSegmentsBank(event, segments));
        }

        if (clusters != null && segments == null) {
            event.appendBanks(this.fillTBHitsBank(event, fhits),
                    this.fillTBClustersBank(event, clusters));
        }

        if (fhits != null && clusters == null) {
            event.appendBanks(this.fillTBHitsBank(event, fhits));
        }
    }    
}
