package org.jlab.rec.cvt.track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.clas.swimtools.Swim;

import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.fit.CircleFitter;
import org.jlab.rec.cvt.fit.CircleFitPars;
import org.jlab.rec.cvt.svt.SVTGeometry;
import org.jlab.rec.cvt.svt.SVTParameters;

public class TrackSeeder {
    SVTGeometry sgeo = null;
    BMTGeometry bgeo = null;
    double bfield;
    
    int NBINS = 36;
    double[] phiShift = new double[]{0, 65, 90}; // move the bin edge to handle bin boundaries
    List<ArrayList<Cross>> scan ;
    Map<Double, ArrayList<Cross>> seedMap ; // init seeds;
    List<ArrayList<ArrayList<Cross>>> sortedCrosses;
    List<Seed> seedScan ;
    List<Double> Xs ;
    List<Double> Ys ;
    List<Double> Ws ;
    public boolean unUsedHitsOnly = false;
    
    public TrackSeeder(SVTGeometry sgeo, BMTGeometry bgeo, Swim swimmer) {
        this.sgeo = sgeo;
        this.bgeo = bgeo;
        float[] b = new float[3];
        swimmer.BfieldLab(0, 0, 0, b);
        this.bfield = Math.abs(b[2]);

        //init lists for scan
        sortedCrosses = new ArrayList<ArrayList<ArrayList<Cross>>>();
        for(int i =0; i<NBINS; i++) {
            sortedCrosses.add(i, new ArrayList<ArrayList<Cross>>() );
            for(int l =0; l<3; l++) {
                sortedCrosses.get(i).add(l,new ArrayList<Cross>() );
            }
        }
        scan = new ArrayList<ArrayList<Cross>>();
        seedMap = new HashMap<Double, ArrayList<Cross>>(); // init seeds;
        seedScan = new ArrayList<Seed>();
        //for fitting
        Xs = new ArrayList<Double>();
        Ys = new ArrayList<Double>();
        Ws = new ArrayList<Double>();
    }
    
    private void MatchSeed(List<Cross> othercrs) {
        if(othercrs==null || othercrs.size()==0)
            return;
        
        for (Seed seed : seedScan) {
            double d = seed.get_Doca();
            double r = seed.get_Rho();
            double f = seed.get_Phi();

            for (Cross c : othercrs ) { 
                c.set_AssociatedTrackID(22220);
                if(this.InSamePhiRange(seed, c)== true) {
                    c.set_AssociatedTrackID(22221);
                    double xi = c.get_Point().x(); 
                    double yi = c.get_Point().y();
                    double ri = Math.sqrt(xi*xi+yi*yi);
                    double fi = Math.atan2(yi,xi) ;

                    double res = this.calcResi(r, ri, d, f, fi);
                    if(Math.abs(res)<SVTParameters.RESIMAX) { 
                        c.set_AssociatedTrackID(22222);
                        // add to seed    
                        seed.get_Crosses().add((Cross) c.clone());
                    }
                }
            }
        }
    }
    public void FitSeed(List<Cross> seedcrs) {
        Xs.clear();
        Ys.clear();
        Ws.clear();
        ((ArrayList<Double>) Xs).ensureCapacity(seedcrs.size()+1);
        ((ArrayList<Double>) Ys).ensureCapacity(seedcrs.size()+1);
        ((ArrayList<Double>) Ws).ensureCapacity(seedcrs.size()+1);
        Xs.add(0, Constants.getXb()); 
        Ys.add(0, Constants.getYb());
        Ws.add(0,0.1);
        for (Cross c : seedcrs ) { 
            if(c.get_Type()==BMTType.C ) System.err.println("WRONG CROSS TYPE");
            Xs.add(c.get_Point().x()); 
            Ys.add(c.get_Point().y());
            Ws.add(1. / (c.get_PointErr().x()*c.get_PointErr().x()+c.get_PointErr().y()*c.get_PointErr().y()));
            
        }
        CircleFitter circlefit = new CircleFitter();
        boolean circlefitstatusOK = circlefit.fitStatus(Xs, Ys, Ws, Xs.size());
        CircleFitPars pars = circlefit.getFit(); 
        if(circlefitstatusOK==false )
            return;
        double d = pars.doca();
        double r = pars.rho();
        double f = pars.phi();
        
        boolean failed = false;
        for (Cross c : seedcrs ) { 
            double xi = c.get_Point().x(); 
            double yi = c.get_Point().y();
            double ri = Math.sqrt(xi*xi+yi*yi);
            double fi = Math.atan2(yi,xi) ;
            
            double res = this.calcResi(r, ri, d, f, fi);
            if(Math.abs(res)>SVTParameters.RESIMAX) { 
                failed = true;
                return;
            }
        }
        Seed seed = new Seed(seedcrs, d, r, f);
        seedScan.add(seed);
    }
    
    /*
    Finds BMT seeds
    */
    public void FindSeedCrossList(List<Cross> crosses) {
        
        seedMap.clear();
        
        for(int si1 = 0; si1<scan.size(); si1++)
            scan.get(si1).clear();
        
        for(int i = 0; i< phiShift.length; i++) {
            FindSeedCrossesFixedBin(crosses, phiShift[i]); 
        }
        
        seedMap.forEach((key,value) -> this.FitSeed(value));
    }
   
    
    /*
    Scans overphase space to find groups of BMT crosses 
    */
    private void FindSeedCrossesFixedBin(List<Cross> crosses, double phiShift) {
        for(int b =0; b<NBINS; b++) {
            for(int l =0; l<3; l++) {
                sortedCrosses.get(b).get(l).clear();
            }
        }
        int[][] LPhi = new int[NBINS][3];
        for (int i = 0; i < crosses.size(); i++) {
            double phi = Math.toDegrees(crosses.get(i).get_Point().toVector3D().phi());

            phi += phiShift;
            if (phi < 0) {
                phi += 360;
            }

            int binIdx = (int) (phi / (360./NBINS) );
            if(binIdx>35)
                binIdx = 35;
            sortedCrosses.get(binIdx).get(crosses.get(i).get_Region() - 1).add(crosses.get(i));
            LPhi[binIdx][crosses.get(i).get_Region() - 1]++; 
        }
        
        for (int b = 0; b < NBINS; b++) {
            int max_layers =0;
            for (int la = 0; la < 3; la++) { 
                if(LPhi[b][la]>0)
                    max_layers++;
            }
            if (sortedCrosses.get(b) != null && max_layers >= 2) { 
                double SumLyr=0;
                while(LPhi[b][0]+LPhi[b][1]+ LPhi[b][2]>=max_layers) {
                    if(SumLyr!=LPhi[b][0]+LPhi[b][1]+ LPhi[b][2]) {
                        SumLyr = LPhi[b][0]+LPhi[b][1]+ LPhi[b][2];
                    } 
                    ArrayList<Cross> hits = new ArrayList<Cross>(); 
                    for (int la = 0; la < 3; la++) {
                        if (sortedCrosses.get(b).get(la) != null && LPhi[b][la]>0) { 
                            if (sortedCrosses.get(b).get(la).get(LPhi[b][la]-1) != null 
                                    && sortedCrosses.get(b).get(la).size()>0) {
                                hits.add(sortedCrosses.get(b).get(la).get(LPhi[b][la]-1)); 
                                
                                if(LPhi[b][la]>1)
                                   LPhi[b][la]--; 
                                if(SumLyr==max_layers)
                                    LPhi[b][la]=0; 
                            }
                        }
                    }
                   
                    if (hits.size() >= 2) {
                        double seedIdx=0;
                        int s = hits.size();
                        int index = (int) Math.pow(2,s);
                        for(Cross c : hits) {
                            seedIdx +=c.get_Id()*Math.pow(10, index);
                            index-=4;
                        }
                        seedMap.put(seedIdx, hits);
                    }
                }
            }
        }
    }

    

    List<Seed> BMTmatches = new ArrayList<Seed>();
    public List<Seed> findSeed(List<Cross> bst_crosses, List<Cross> bmt_crosses) {
        
        List<Seed> seedlist = new ArrayList<Seed>();

        List<Cross> crosses = new ArrayList<Cross>();
        List<Cross> svt_crosses = new ArrayList<Cross>();
        List<Cross> bmtC_crosses = new ArrayList<Cross>();
        
        if(bmt_crosses!=null && bmt_crosses.size()>0) {
            for(Cross c : bmt_crosses) { 
                if(c.get_Type()==BMTType.Z) { 
                    if(this.unUsedHitsOnly == false) {
                        crosses.add(c);
                    } else {
                        if(this.unUsedHitsOnly == true && c.isInSeed == false) {
                            crosses.add(c);
                        }
                    }
                }
                if(c.get_Type()==BMTType.C) {
                    if(this.unUsedHitsOnly == false) {
                        bmtC_crosses.add(c);
                    } else {
                        if(this.unUsedHitsOnly == true && c.isInSeed == false) {
                            bmtC_crosses.add(c);
                        }
                    }
                }
            }
        }
        if(bst_crosses!=null && bst_crosses.size()>0) {
            for(Cross c : bst_crosses) { 
                if(this.unUsedHitsOnly == false) {
                        svt_crosses.add(c);
                } else {
                    if(this.unUsedHitsOnly == true && c.isInSeed == false) {
                        svt_crosses.add(c);
                    }
                }
            }
        }
        this.FindSeedCrossList(svt_crosses);
        this.MatchSeed(crosses);
        
        for(Seed mseed : seedScan) { 
            List<Cross> seedcrs = mseed.get_Crosses();
            for (Cross c : seedcrs ) { 
                if(c.get_Type()==BMTType.C ) continue;
                c.set_AssociatedTrackID(122220);
            }
          // loop until a good circular fit. removing far crosses each time
          boolean circlefitstatusOK = false;
          while( ! circlefitstatusOK && seedcrs.size()>=3 ){
            
            Xs.clear();
            Ys.clear();
            Ws.clear();
            ((ArrayList<Double>) Xs).ensureCapacity(seedcrs.size()+1);
            ((ArrayList<Double>) Ys).ensureCapacity(seedcrs.size()+1);
            ((ArrayList<Double>) Ws).ensureCapacity(seedcrs.size()+1);
            Xs.add(0, Constants.getXb()); 
            Ys.add(0, Constants.getYb());
            Ws.add(0, 0.1);
            for (Cross c : seedcrs ) { 
                if(c.get_Type()==BMTType.C ) continue;
                c.set_AssociatedTrackID(122221);
                Xs.add(c.get_Point().x()); 
                Ys.add(c.get_Point().y());
                Ws.add(1. / (c.get_PointErr().x()*c.get_PointErr().x()
                        +c.get_PointErr().y()*c.get_PointErr().y()));
            }

            CircleFitter circlefit = new CircleFitter();
            circlefitstatusOK = circlefit.fitStatus(Xs, Ys, Ws, Xs.size());
            CircleFitPars pars = circlefit.getFit();

            // if not a good fit, check for outliers 
            if (!circlefitstatusOK ||  pars.chisq()/(double)(Xs.size()-3)>10) {
              //System.out.println(" check circular fit" );
              double d = pars.doca();
              double r = pars.rho();
              double f = pars.phi();
              for (Cross c : seedcrs ) { 
                if(c.get_Type()==BMTType.C) continue;
                c.set_AssociatedTrackID(122222);
                    double xi = c.get_Point().x(); 
                    double yi = c.get_Point().y();
                    double ri = Math.sqrt(xi*xi+yi*yi);
                    double fi = Math.atan2(yi,xi) ;
                    double res = this.calcResi(r, ri, d, f, fi);
                    if(Math.abs(res)>SVTParameters.RESIMAX) {
                        //System.out.println(" remove detector " + c .get_Detector() + " region " + c.get_Region() + " sector " + c.get_Sector()  );
                        seedcrs.remove(c);
                        break;
                    }
                }
            }
          }
        }


        for(Seed mseed : seedScan) { 
            List<Cross> seedcrs = mseed.get_Crosses();
            Track cand = null;
            if(seedcrs.size()>=3)
                cand = mseed.fit(this.sgeo, this.bgeo, 5, false, this.bfield);
            if (cand != null) {
                Seed seed = new Seed(seedcrs, cand.get_helix());

                //match to BMT
                if (seed != null ) {
                    List<Cross> sameSectorCrosses = this.FindCrossesInSameSectorAsSVTTrk(seed, bmtC_crosses);
                    BMTmatches.clear();
                    if (sameSectorCrosses.size() >= 0) {
                        BMTmatches = this.findCandUsingMicroMegas(seed, sameSectorCrosses);
                    } 
                    Seed bestSeed = null;
                    double chi2_Circ = Double.POSITIVE_INFINITY;
                    double chi2_Line = Double.POSITIVE_INFINITY;
                    
                    for (Seed bseed : BMTmatches) {
                        //refit using the BMT
                        Track bcand = bseed.fit(this.sgeo, this.bgeo, 5, false, this.bfield);
                        
                        if (bcand != null && bcand.get_circleFitChi2PerNDF()<chi2_Circ
                                          && bcand.get_lineFitChi2PerNDF()<chi2_Line
                                          && bcand.isGood()) {
                            bestSeed = new Seed(bseed.get_Crosses(), bcand.get_helix());
                            chi2_Circ = bcand.get_circleFitChi2PerNDF();
                            chi2_Line = bcand.get_lineFitChi2PerNDF();
                        }
                    }
                    if(bestSeed!= null) seedlist.add(bestSeed);
                } 
            }
        }
       
        for (Seed bseed : seedlist) { 
            for(Cross c : bseed.get_Crosses()) {
                c.isInSeed = true;
            }
        }
        return seedlist;
    }
    

    private List<Cross> FindCrossesInSameSectorAsSVTTrk(Seed seed, List<Cross> bmt_crosses) {
        List<Cross> bmt_crossesInSec = new ArrayList<Cross>();
        //double angle_i = 0; // first angular boundary init
        //double angle_f = 0; // second angular boundary for detector A, B, or C init
        
        double jitter = Math.toRadians(10); // 10 degrees jitter
        for (int i = 0; i < bmt_crosses.size(); i++) { 
            Point3D pAtBMTSurf =seed.get_Helix().getPointAtRadius(bgeo.getRadius(bmt_crosses.get(i).get_Cluster1().get_Layer()));
            // the hit parameters
            double angle = Math.atan2(pAtBMTSurf.y(), pAtBMTSurf.x());
            if (angle < 0) {
                angle += 2 * Math.PI;
            }
            //if (bmt_geo.isInDetector(bmt_crosses.get(i).get_Region()*2-1, angle, jitter) 
            //        == bmt_crosses.get(i).get_Sector() - 1) 
            //inDetector(int layer, int sector, Point3D traj) 
            if (bgeo.inDetector(bmt_crosses.get(i).get_Region()*2-1, bmt_crosses.get(i).get_Sector(), pAtBMTSurf)==true){
                bmt_crossesInSec.add(bmt_crosses.get(i)); 
            }
            
        }

        return bmt_crossesInSec;
    }

//    private List<Double> X = new ArrayList<Double>();
//    private List<Double> Y = new ArrayList<Double>();
//    private List<Double> Z = new ArrayList<Double>();
//    private List<Double> Rho = new ArrayList<Double>();
//    private List<Double> ErrZ = new ArrayList<Double>();
//    private List<Double> ErrRho = new ArrayList<Double>();
//    private List<Double> ErrRt = new ArrayList<Double>();
//    List<Cross> BMTCrossesC = new ArrayList<Cross>();
//    List<Cross> BMTCrossesZ = new ArrayList<Cross>();
//    List<Cross> SVTCrosses = new ArrayList<Cross>();
//    float b[] = new float[3];
//    
//    public Track fit(List<Cross> VTCrosses, SVTGeometry svt_geo, BMTGeometry bmt_geo, int fitIter, 
//            boolean originConstraint, Swim swimmer) {
//        double chisqMax = Double.POSITIVE_INFINITY;
//        
//        Track cand = null;
//        HelicalTrackFitter fitTrk = new HelicalTrackFitter();
//        for (int i = 0; i < fitIter; i++) {
//            //	if(originConstraint==true) {
//            //		X.add(0, (double) 0);
//            //		Y.add(0, (double) 0);
//            //		Z.add(0, (double) 0);
//            //		Rho.add(0, (double) 0);
//            //		ErrRt.add(0, (double) org.jlab.rec.cvt.svt.Constants.RHOVTXCONSTRAINT);
//            //		ErrZ.add(0, (double) org.jlab.rec.cvt.svt.Constants.ZVTXCONSTRAINT);		
//            //		ErrRho.add(0, (double) org.jlab.rec.cvt.svt.Constants.RHOVTXCONSTRAINT);										
//            //	}
//            X.clear();
//            Y.clear();
//            Z.clear();
//            Rho.clear();
//            ErrZ.clear();
//            ErrRho.clear();
//            ErrRt.clear();
//
//            int svtSz = 0;
//            int bmtZSz = 0;
//            int bmtCSz = 0;
//
//            BMTCrossesC.clear();
//            BMTCrossesZ.clear();
//            SVTCrosses.clear();
//
//            for (Cross c : VTCrosses) {
//                if (c.get_Detector()==DetectorType.BST) {
//                    SVTCrosses.add(c);
//                }
//                else if (c.get_Detector()==DetectorType.BMT && c.get_Type()==BMTType.C ) {
//                    BMTCrossesC.add(c);
//                }
//                else if (c.get_Detector()==DetectorType.BMT && c.get_Type()==BMTType.Z ) {
//                    BMTCrossesZ.add(c);
//                }
//            }
//            svtSz = SVTCrosses.size();
//            if (BMTCrossesZ != null) {
//                bmtZSz = BMTCrossesZ.size();
//            }
//            if (BMTCrossesC != null) {
//                bmtCSz = BMTCrossesC.size();
//            }
//
//            int useSVTdipAngEst = 1;
//            if (bmtCSz >= 2) {
//                useSVTdipAngEst = 0;
//            }
//
//            ((ArrayList<Double>) X).ensureCapacity(svtSz + bmtZSz);
//            ((ArrayList<Double>) Y).ensureCapacity(svtSz + bmtZSz);
//            ((ArrayList<Double>) Z).ensureCapacity(svtSz * useSVTdipAngEst + bmtCSz);
//            ((ArrayList<Double>) Rho).ensureCapacity(svtSz * useSVTdipAngEst + bmtCSz);
//            ((ArrayList<Double>) ErrZ).ensureCapacity(svtSz * useSVTdipAngEst + bmtCSz);
//            ((ArrayList<Double>) ErrRho).ensureCapacity(svtSz * useSVTdipAngEst + bmtCSz); // Try: don't use svt in dipdangle fit determination
//            ((ArrayList<Double>) ErrRt).ensureCapacity(svtSz + bmtZSz);
//
//            cand = new Track(null);
//            cand.addAll(SVTCrosses);
//            for (int j = 0; j < SVTCrosses.size(); j++) {
//                X.add(j, SVTCrosses.get(j).get_Point().x());
//                Y.add(j, SVTCrosses.get(j).get_Point().y());
//                if (useSVTdipAngEst == 1) {
//                    Z.add(j, SVTCrosses.get(j).get_Point().z());
//                    Rho.add(j, Math.sqrt(SVTCrosses.get(j).get_Point().x() * SVTCrosses.get(j).get_Point().x()
//                            + SVTCrosses.get(j).get_Point().y() * SVTCrosses.get(j).get_Point().y()));
//                    ErrRho.add(j, Math.sqrt(SVTCrosses.get(j).get_PointErr().x() * SVTCrosses.get(j).get_PointErr().x()
//                            + SVTCrosses.get(j).get_PointErr().y() * SVTCrosses.get(j).get_PointErr().y()));
//                    ErrZ.add(j, SVTCrosses.get(j).get_PointErr().z());
//                }
//                ErrRt.add(j, Math.sqrt(SVTCrosses.get(j).get_PointErr().x() * SVTCrosses.get(j).get_PointErr().x()
//                        + SVTCrosses.get(j).get_PointErr().y() * SVTCrosses.get(j).get_PointErr().y()));
//            }
//
//            if (bmtZSz > 0) {
//                for (int j = svtSz; j < svtSz + bmtZSz; j++) {
//                    X.add(j, BMTCrossesZ.get(j - svtSz).get_Point().x());
//                    Y.add(j, BMTCrossesZ.get(j - svtSz).get_Point().y());
//                    ErrRt.add(j, Math.sqrt(BMTCrossesZ.get(j - svtSz).get_PointErr().x() * BMTCrossesZ.get(j - svtSz).get_PointErr().x()
//                            + BMTCrossesZ.get(j - svtSz).get_PointErr().y() * BMTCrossesZ.get(j - svtSz).get_PointErr().y()));
//                }
//            }
//            if (bmtCSz > 0) {
//                for (int j = svtSz * useSVTdipAngEst; j < svtSz * useSVTdipAngEst + bmtCSz; j++) {
//                    Z.add(j, BMTCrossesC.get(j - svtSz * useSVTdipAngEst).get_Point().z());
//                    Rho.add(j, bmt_geo.getRadiusMidDrift(BMTCrossesC.get(j - svtSz * useSVTdipAngEst).get_Cluster1().get_Layer()));
//                    
//                    ErrRho.add(j, bmt_geo.getThickness()/2 / Math.sqrt(12.));
//                    ErrZ.add(j, BMTCrossesC.get(j - svtSz * useSVTdipAngEst).get_PointErr().z());
//                }
//            }
//            X.add((double) Constants.getXb());
//            Y.add((double) Constants.getYb());
//            ErrRt.add((double) 0.1);
//            
//            fitTrk.fit(X, Y, Z, Rho, ErrRt, ErrRho, ErrZ);
//            
//            if (fitTrk.get_helix() == null) { 
//                return null;
//            }
//
//            cand = new Track(fitTrk.get_helix());
//            //cand.addAll(SVTCrosses);
//            cand.addAll(SVTCrosses);
//            cand.addAll(BMTCrossesC);
//            cand.addAll(BMTCrossesZ);
//            
//            swimmer.BfieldLab(0, 0, 0, b);
//            double Bz = Math.abs(b[2]);
//            fitTrk.get_helix().B = Bz;
//            cand.set_HelicalTrack(fitTrk.get_helix());
//            //if(shift==0)
//            if (fitTrk.get_chisq()[0] < chisqMax) {
//                chisqMax = fitTrk.get_chisq()[0];
//                if(chisqMax<Constants.CIRCLEFIT_MAXCHI2)
//                    cand.update_Crosses(svt_geo, bmt_geo);
//                //i=fitIter;
//            }
//        }
//        //System.out.println(" Seed fitter "+fitTrk.get_chisq()[0]+" "+fitTrk.get_chisq()[1]); 
//        if(chisqMax>Constants.CIRCLEFIT_MAXCHI2)
//            return null;
//        if(X.size() > 3)
//            cand.set_circleFitChi2PerNDF(fitTrk.get_chisq()[0] / (int) (X.size() - 3)); // 3 fit params	
//        if(Z.size() > 2)
//            cand.set_lineFitChi2PerNDF(fitTrk.get_chisq()[1] / (int) (Z.size() - 2)); // 2 fit params
//        return cand;
//    }



    public List<Seed> findCandUsingMicroMegas(Seed trkCand, List<Cross> bmt_crosses) {
        List<ArrayList<Cross>> BMTCcrosses = new ArrayList<ArrayList<Cross>>();
        
        ArrayList<Cross> matches = new ArrayList<Cross>();
        Map<String, Seed> AllSeeds = new HashMap<String, Seed>();
        int[] S = new int[3];
       
        for (int r = 0; r < 3; r++) {
            BMTCcrosses.add(new ArrayList<Cross>());
        }
        //for (int r = 0; r < 3; r++) {
        //    BMTCcrosses.get(r).clear();
        //    BMTZcrosses.get(r).clear();
        //}

        for (Cross bmt_cross : bmt_crosses) { 
            if (bmt_cross.get_Type()==BMTType.C) // C-detector
                BMTCcrosses.get(bmt_cross.get_Region() - 1).add(bmt_cross); 
        }

        AllSeeds.clear();

        for (int r = 0; r < 3; r++) {
            S[r] = BMTCcrosses.get(r).size();
            if (S[r] == 0) {
                S[r] = 1;
            }
            
        }

        for (int i1 = 0; i1 < S[0]; i1++) {
            for (int i2 = 0; i2 < S[1]; i2++) {
                for (int i3 = 0; i3 < S[2]; i3++) {

                    matches.clear();

                    if (BMTCcrosses.get(0).size() > 0 && i1 < BMTCcrosses.get(0).size()) {
                        if (this.passCcross(trkCand, BMTCcrosses.get(0).get(i1), bgeo)) {
                            matches.add(BMTCcrosses.get(0).get(i1));
                        }
                    }
                    if (BMTCcrosses.get(1).size() > 0 && i2 < BMTCcrosses.get(1).size()) {
                        if (this.passCcross(trkCand, BMTCcrosses.get(1).get(i2), bgeo)) {
                            matches.add(BMTCcrosses.get(1).get(i2));
                        }
                    }
                    if (BMTCcrosses.get(2).size() > 0 && i3 < BMTCcrosses.get(2).size()) {
                        if (this.passCcross(trkCand, BMTCcrosses.get(2).get(i3), bgeo)) {
                            matches.add(BMTCcrosses.get(2).get(i3));
                        }
                    }
                    
                    matches.addAll(trkCand.get_Crosses());
                    if (matches.size() > 0) {
                        Seed BMTTrkSeed = new Seed();
                        String st = "";
                        for(Cross c : matches)
                            st+=c.get_Id();
                        BMTTrkSeed.set_Helix(trkCand.get_Helix());
                        BMTTrkSeed.set_Crosses(matches);
                        AllSeeds.put(st,BMTTrkSeed);
                        
                        //if (AllSeeds.size() > 200) {
                        //    AllSeeds.clear();
                        //    return AllSeeds;
                        //}
                        BMTTrkSeed = null;
                                
                    }
                }
            }
        }
        
        List<Seed> outputSeeds = new ArrayList<Seed>();
        for(Seed s : AllSeeds.values())
            outputSeeds.add(s);
        
        return outputSeeds;
    }

    private boolean passCcross(Seed trkCand, Cross bmt_Ccross, BMTGeometry bmt_geo) {
        boolean pass = false;

        double dzdrsum = trkCand.get_Helix().get_tandip();

        double z_bmt = bmt_Ccross.get_Point().z();
        double r_bmt = bmt_geo.getRadius(bmt_Ccross.get_Cluster1().get_Layer());
        Point3D phiHelixAtSurf = trkCand.get_Helix().getPointAtRadius(r_bmt); 
        //if (bmt_geo.isInSector(bmt_Ccross.get_Cluster1().get_Layer(), Math.atan2(phiHelixAtSurf.y(), phiHelixAtSurf.x()), Math.toRadians(10)) 
        //        != bmt_Ccross.get_Sector()) 
        int sector = bmt_geo.getSector(bmt_Ccross.get_Cluster1().get_Layer(), Math.atan2(phiHelixAtSurf.y(), phiHelixAtSurf.x()));
        if(sector!= bmt_Ccross.get_Sector() || sector ==0){
            return false;
        }
        double dzdr_bmt = z_bmt / r_bmt;
        if (Math.abs(1 - (dzdrsum / (double) (trkCand.get_Crosses().size())) / ((dzdrsum + dzdr_bmt) / (double) (trkCand.get_Crosses().size() + 1))) <= SVTParameters.dzdrcut) // add this to the track
        {
            pass = true;
        } 
        
        return pass;
    }

    
    /**
     *
     * @param x1 cross1 x-coordinate
     * @param x2 cross2 x-coordinate
     * @param x3 cross3 x-coordinate
     * @param y1 cross1 y-coordinate
     * @param y2 cross2 y-coordinate
     * @param y3 cross3 y-coordinate
     * @return radius of circle containing 3 crosses in the (x,y) plane
     */
    private double calc_radOfCurv(double x1, double x2, double x3, double y1, double y2, double y3) {
        double radiusOfCurv = 0;

        if (Math.abs(x2 - x1) > 1.0e-9 && Math.abs(x3 - x2) > 1.0e-9) {
            // Find the intersection of the lines joining the innermost to middle and middle to outermost point
            double ma = (y2 - y1) / (x2 - x1);
            double mb = (y3 - y2) / (x3 - x2);

            if (Math.abs(mb - ma) > 1.0e-9) {
                double xcen = 0.5 * (ma * mb * (y1 - y3) + mb * (x1 + x2) - ma * (x2 + x3)) / (mb - ma);
                double ycen = (-1. / mb) * (xcen - 0.5 * (x2 + x3)) + 0.5 * (y2 + y3);

                radiusOfCurv = Math.sqrt((x1 - xcen) * (x1 - xcen) + (y1 - ycen) * (y1 - ycen));
            }
        }
        return radiusOfCurv;

    }

    private void removeDuplicates(List<Seed> AllSeeds) {
        
        Collections.sort(AllSeeds);
        List<Seed> Dupl = new ArrayList<Seed>();
        for(int i = 1; i < AllSeeds.size(); i++) { 
            if(AllSeeds.get(i-1).get_IntIdentifier().equalsIgnoreCase(AllSeeds.get(i).get_IntIdentifier())) { 
                Dupl.add(AllSeeds.get(i));
            }
        }
        AllSeeds.removeAll(Dupl);
        
    }

    private double calcResi(double r, double ri, double d, double f, double fi) {
        double res = 0.5*r*ri*ri - (1+r*d)*ri*Math.sin(f-fi)+0.5*r*d*d+d;
        return res;
    }

    private boolean InSamePhiRange(Seed seed, Cross c) {
        return true;
    }

}
