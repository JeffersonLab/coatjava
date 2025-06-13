package org.jlab.rec.dc.trajectory;

import java.util.ArrayList;
import java.util.List;

import org.jlab.clas.clas.math.FastMath;
import org.jlab.detector.geant4.v2.DCGeant4Factory;
import org.jlab.rec.dc.cluster.Cluster;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.Constants;


/**
 * 
 * @author Tongtong Cao
 */

public class RoadFinderWithURWell extends RoadFinder  {
    
    public RoadFinderWithURWell() {}
    
    /**
     * 
     * @param segs list of segments
     * @param DcDetector DC detector utility
     * @return list of segments corresponding to pseudo-segments
     */
    public List<Road> findRoadsWithURWell(List<Segment> segs, DCGeant4Factory DcDetector) {
        //QuadraticFit qf = new QuadraticFit();
        //initialize the lists

        List<Road> Roads = new ArrayList<>();
        
        List<ArrayList<ArrayList<Segment>>> superLayerLists = new ArrayList<>();
        for(int sec=0; sec<6; sec++)  {
            ArrayList<ArrayList<Segment>> sLyrs = new ArrayList<>();
            ArrayList<ArrayList<ArrayList<Segment>>> rLyrs = new ArrayList<>();
            
            for(int sly=0; sly<6; sly++) {
                sLyrs.add(new ArrayList<>());
            }
            superLayerLists.add(sLyrs);
        }
        //make an array sorted by sector, superlayers
        for (Segment seg : segs) { 
            if (seg.isOnTrack==false) {
                superLayerLists.get(seg.get_Sector()-1).get(seg.get_Superlayer()-1).add((Segment) seg.clone());
            }
        }
        for(int sec=0; sec<6; sec++)  {
            for(int sly=0; sly<6; sly++) {
                // add a blank to each superlayer
                Segment blank = new Segment(new FittedCluster(new Cluster(sec+1, sly+1, -1)));
                blank.set_Id(-10);
                superLayerLists.get(sec).get(sly).add(blank);
            }
        }
        int roadId =1;
        for (int sec = 0; sec<6; sec++) { 
            for (int j = 0; j<2; j++) {
                for (int i1 = 0; i1<superLayerLists.get(sec).get(0+j).size(); i1++) {
                    Segment s1 = superLayerLists.get(sec).get(0+j).get(i1); 
                    for (int i2 = 0; i2<superLayerLists.get(sec).get(2+j).size(); i2++) {
                        Segment s2 = superLayerLists.get(sec).get(2+j).get(i2); 
                        for (int i3 = 0; i3<superLayerLists.get(sec).get(4+j).size(); i3++) {
                            Segment s3 = superLayerLists.get(sec).get(4+j).get(i3); 
                            Road sLyr = new Road(); 
                            if(s1.get_Id()!=-10) {
                                sLyr.add(s1);
                            }
                            if(s2.get_Id()!=-10) {
                                sLyr.add(s2);
                            }
                            if(s3.get_Id()!=-10) {
                                sLyr.add(s3);
                            } 
                            if(sLyr.size()<3) continue;
                            
                            if(s1.getMatchedURWellCross() != null) sLyr.setURWellCross(s1.getMatchedURWellCross());
                            
                            if (this.fitRoadWithURWell(sLyr, DcDetector)==true) { 
                                if(qf.chi2<fitPassingCut && qf.chi2!=0 ) { // road is good --> pass w.out looking for missing segment
                                    sLyr.id=roadId;
                                    sLyr.a=qf.a;
                                    Roads.add(sLyr);
                                    roadId++; 
                                }
                                
                            }
                        }
                    }
                }
            }
        }
        return Roads;
    }
    
    private boolean fitRoadWithURWell(Road road, DCGeant4Factory DcDetector) {
        qf.init();
        int NbHits =0;		
        if(road.size()<2) {
            return false;
        }

        for(Segment s : road) {
            NbHits+=s.size();
        }
        
        if(road.getURWellCross() != null) NbHits++;
        
        double[] X = new double[NbHits];
        double[] Z = new double[NbHits];
        double[] errX = new double[NbHits];

        int hitno =0; 
        for(Segment s : road) {           
            for(int j =0; j<s.size(); j++) { 
                X[hitno] = s.get(j).get_X();               
                Z[hitno] = s.get(j).get_Z();
                errX[hitno] = s.get(j).get_CellSize()/Math.sqrt(12.)/Constants.COS6; 
                hitno++;
            }
        }

        if(road.getURWellCross() != null){
                Z[hitno] = road.getURWellCross().local().z();
                if(road.get(0).get_Superlayer() == 1) X[hitno] = road.getURWellCross().getXRelativeDCSL1AtPlaneY0TSC();
                else X[hitno] = road.getURWellCross().getXRelativeDCSL2AtPlaneY0TSC();
                errX[hitno] =  road.getURWellCross().getXErrRelativeDCAtPlaneY0TSCHB();
                hitno++;                
        }
        
        qf.evaluate(Z, X, errX);

        double WChi2 =0;
        for(Segment s : road) {
            for(FittedHit h : s) {
                double trkX = qf.a[0]*h.get_Z()*h.get_Z()+qf.a[1]*h.get_Z()+qf.a[2]; 
                int calcWire = segTrj.getWireOnTrajectory(h.get_Sector(), h.get_Superlayer(), h.get_Layer(), trkX, DcDetector) ;
                WChi2+=(h.get_Wire()-calcWire)*(h.get_Wire()-calcWire);
            } 
        }
        // pass if normalized chi2 is less than 1
        if(road.getURWellCross() == null) return WChi2/qf.NDF <= 1;
        else return WChi2/(qf.NDF-1) <= 1;
    }               
    
}