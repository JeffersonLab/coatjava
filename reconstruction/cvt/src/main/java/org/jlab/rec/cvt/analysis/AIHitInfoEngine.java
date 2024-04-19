package org.jlab.rec.cvt.analysis;

import cnuphys.magfield.MagneticFields;
import java.io.FileNotFoundException;
import org.jlab.rec.cvt.services.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.MagFieldsEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.banks.RecoBankWriter;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cluster.ClusterFinder;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.svt.SVTGeometry;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.Track;
import org.jlab.utils.CLASResources;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.system.ClasUtilsFile;

/**
 * Service to return reconstructed TRACKS
 * format
 *
 * @author ziegler
 *
 */
public class AIHitInfoEngine extends ReconstructionEngine {

    public PrintWriter pw = null;
    public PrintWriter pw2 = null;
    public int nevts =0;
    public String fileName = "file.txt";
    public String fileName2 = "file2.txt";
    private double perTruHitOnCls=0.75;
    /**
     * @param docacutsum the docacutsum to set
     */
    public void setDocacutsum(double docacutsum) {
        this.docacutsum = docacutsum;
    }

    private int Run = -1;

    // run-time options
    private int     pid = 0;
    private int     kfIterations = 5;
    private boolean kfFilterOn = true;
    private boolean initFromMc = false;    
    
    // yaml setting passed to Constants class
    private boolean isCosmics           = false;
    private boolean svtOnly             = true;
    private String  excludeLayers       = null;
    private String  excludeBMTLayers    = null;
    private int     removeRegion        = 0;
    private int     beamSpotConstraint  = 2;
    private double  beamSpotRadius      = 0.3;
    private String  targetMaterial      = "LH2";
    private boolean elossPrecorrection  = true;
    private boolean svtSeeding          = true;
    private boolean timeCuts            = true;
    private boolean hvCuts              = false;
    public boolean useSVTTimingCuts     =  false;
    public boolean removeOverlappingSeeds = true;
    public boolean flagSeeds = false;
    public boolean gemcIgnBMT0ADC = false;
    public boolean KFfailRecovery = true;
    public boolean KFfailRecovMisCls = true;
    private String  matrixLibrary       = "EJML";
    private boolean useOnlyTruth        = false;
    private boolean useSVTLinkerSeeder  = true;
    private double docacut = 0.75;
    private double docacutsum = 1.15;
    private int svtmaxclussize = 100;
    private int bmtcmaxclussize = 100;
    private int bmtzmaxclussize = 100;
    private double rcut = 120.0;
    private double z0cut = 10;
    
    public AIHitInfoEngine(String name) {
        super(name, "ziegler", "6.0");
    }

    public AIHitInfoEngine() {
        super("CVTEngine", "ziegler", "6.0");
    }

    
    @Override
    public boolean init() { 
        try {
            pw = new PrintWriter("/Users/ziegler/BASE/Files/CVTDEBUG/AI/"+fileName);
            String s = "event   layer  component    xo  yo  zo  xe  ye  ze  p theta phi type (BG=0 or Signal=1) On-Trk(rec) (no=0,yes=1) On-Trk(MC) (no=0,yes=1)\n";
            pw.print(s);
            pw2 = new PrintWriter("/Users/ziegler/BASE/Files/CVTDEBUG/AI/"+fileName2);
            String s2 = "event   layer  component    xo  yo  zo  xe  ye  ze  type (BG=0 or Signal=1) On-Trk(rec) (no=0,yes=1) On-Trk(MC) (no=0,yes=1)\n";
            pw2.print(s2);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(AIHitInfoEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        this.loadConfiguration();
        Constants.getInstance().initialize(this.getName(),
                                           isCosmics,
                                           svtOnly,
                                           excludeLayers,
                                           excludeBMTLayers,
                                           removeRegion,
                                           beamSpotConstraint,
                                           beamSpotRadius,
                                           targetMaterial,
                                           elossPrecorrection,
                                           svtSeeding,
                                           timeCuts,
                                           hvCuts,
                                           useSVTTimingCuts,
                                           removeOverlappingSeeds,
                                           flagSeeds,
                                           gemcIgnBMT0ADC,
                                           KFfailRecovery,
                                           KFfailRecovMisCls, 
                                           matrixLibrary,
                                           useOnlyTruth,
                                           useSVTLinkerSeeder, 
                                           docacut, 
                                           docacutsum, 
                                           svtmaxclussize, 
                                           bmtcmaxclussize, 
                                           bmtzmaxclussize,
                                           rcut,
                                           z0cut);

        this.initConstantsTables();
        this.printConfiguration();
        return true;    
    }
    
    
   
    
    public int getRun(DataEvent event) {
    
        if (event.hasBank("RUN::config") == false) {
            System.err.println("RUN CONDITIONS NOT READ!");
            return 0;
        }

        DataBank bank = event.getBank("RUN::config");
        int run = bank.getInt("run", 0);  
        if(Constants.getInstance().seedingDebugMode) {
            System.out.println("EVENT "+bank.getInt("event", 0));
        }
        return run;
    }

    public int getPid() {
        return pid;
    }

    public int getKfIterations() {
        return kfIterations;
    }

    public boolean isKfFilterOn() {
        return kfFilterOn;
    }

    public boolean isInitFromMc() {
        return initFromMc;
    }

    public boolean seedBeamSpot() {
        return this.beamSpotConstraint>0;
    }
    
    public boolean kfBeamSpot() {
        return this.beamSpotConstraint==2;
    }
    
    /**
     * @return the docacut
     */
    public double getDocacut() {
        return docacut;
    }

    /**
     * @param docacut the docacut to set
     */
    public void setDocacut(double docacut) {
        this.docacut = docacut;
    }

    /**
     * @return the docacutsum
     */
    public double getDocacutsum() {
        return docacutsum;
    }

    /**
     * @return the svtmaxclussize
     */
    public int getSvtmaxclussize() {
        return svtmaxclussize;
    }

    /**
     * @param svtmaxclussize the svtmaxclussize to set
     */
    public void setSvtmaxclussize(int svtmaxclussize) {
        this.svtmaxclussize = svtmaxclussize;
    }

    /**
     * @return the bmtcmaxclussize
     */
    public int getBmtcmaxclussize() {
        return bmtcmaxclussize;
    }

    /**
     * @param bmtcmaxclussize the bmtcmaxclussize to set
     */
    public void setBmtcmaxclussize(int bmtcmaxclussize) {
        this.bmtcmaxclussize = bmtcmaxclussize;
    }

    /**
     * @return the bmtzmaxclussize
     */
    public int getBmtzmaxclussize() {
        return bmtzmaxclussize;
    }

    /**
     * @param bmtzmaxclussize the bmtzmaxclussize to set
     */
    public void setBmtzmaxclussize(int bmtzmaxclussize) {
        this.bmtzmaxclussize = bmtzmaxclussize;
    }
    
    private String PrintToFile(int evNb, org.jlab.rec.cvt.track.Track t, Cluster c) {
        //eventnumber  tracknumber x y covxx covxy covyy z angle time 
        double phi = t.getHelix().getPhiAtDCA();
        
        String s = "";
        s+=evNb;
        s+="\t";
        s+=t.getId();
        s+="\t";
        s+=(float) c.center().x()/10;
        s+="\t";
        s+=(float) c.center().y()/10;
        s+="\t";
        s+=(float) phi;
        s+="\t";
        s+=(float) 0.0;
        s+="\t";
        s+=(float) 0.0;
        s+="\t";
        s+=(float) 0.0;
        s+="\t";
        s+=(float) c.center().z()/10;
        s+="\t";
        s+=(float) c.getTime();
        s+="\n";
        //System.out.println(s);
        return s;
    }
    
    private void PrintHitsToFile(int evNb, org.jlab.rec.cvt.track.Track t, PrintWriter pw) {
        //event layer	component	xo	yo	zo	xe	ye	ze	type (BG=0 or Signal=1)	Track number
        for(Cluster c: t.getSeed().getClusters()) {
        
            for(Hit h : c) {
                int layr = h.getLayer();
                if(h.getDetector()==DetectorType.BMT)
                    layr+=6;
                Point3D ep1=null;
                Point3D ep2=null;
                if(h.getType()==BMTType.C) {
                    ep1 = h.getStrip().getArc().origin();
                    ep2 = h.getStrip().getArc().end();
                } else {
                    ep1 = h.getStrip().getLine().origin();
                    ep2 = h.getStrip().getLine().end();
                }
                int isBG = 0;
                if(h.getTrueAssociatedTrackID()!=-1) {
                    isBG =1; 
                } 
                String str = String.format("%d %d %d %.3f %.3f %.3f %.3f %.3f %.3f %d %d", 
                                    evNb, layr, h.getStrip().getStrip(), ep1.x(), ep1.y(), ep1.z(), ep2.x(), ep2.y(), ep2.z(), isBG, t.getId());
                str+="\n";
                
                pw.print(str);
            }
        }
    }
    
    private void PrintClustersToFile(int evNb, org.jlab.rec.cvt.track.Track t, 
            double mcPtk, double mcThetatk, double mcPhitk,
            PrintWriter pw, PrintWriter pw2) {
        //event layer	component	xo	yo	zo	xe	ye	ze	type (BG=0 or Signal=1)	Track number
        double mcP=999;
        double mcTheta=999;
        double mcPhi=999;
        boolean tGood=true;
        for(Cluster c: t.getSeed().getClusters()) {
            double isnotBG = 0;
            double W =0;
            for(Hit h : c) {
                W+=h.getStrip().getEdep();
                if(h.getTrueAssociatedTrackID()!=-1) {
                    isnotBG +=h.getStrip().getEdep();
                }
            }
            double perTru = isnotBG/W;
            if(perTru>perTruHitOnCls) {
                c.BG=1;
                for(Hit h : c) { 
                    if(h.getTrueAssociatedTrackID()!=-1) {
                        c.associatedTrueTrkId = h.getTrueAssociatedTrackID();
                    }
                }
                
            } else {
                tGood = false;
            }
        }
        for(Cluster c: t.getSeed().getClusters()) {
            int layr = c.getLayer();
            if(c.getDetector()==DetectorType.BMT)
                    layr+=6;
            Point3D ep1=null;
            Point3D ep2=null;
            if(c.getType()==BMTType.C) {
                ep1 = c.getArc().origin();
                ep2 = c.getArc().end();
            } else {
                ep1 = c.getLine().origin();
                ep2 = c.getLine().end();
            }
            if(c.getDetector()==DetectorType.BST) {
                int onTrk = c.associatedTrueTrkId;
                if(onTrk==-1) onTrk=0;
                if(onTrk==1) {
                     mcP=mcPtk;
                     mcTheta=mcThetatk;
                     mcPhi=mcPhitk;
                }
                String str = String.format("%d %d %d %.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f %d %d %d", 
                                        evNb, layr, (int)c.getCentroid(), ep1.x(), ep1.y(), ep1.z(), ep2.x(), ep2.y(), ep2.z(), 
                                        mcP, mcTheta, mcPhi,
                                        c.BG, t.getId(), onTrk);
                str+="\n";
                pw.print(str); 
//                if(tGood) { 
//                    pw.print(str); 
//                } else {
//                    pw2.print(str);
//                }
            }
        }
    }
    
    private void PrintUnmatchedClustersToFile(int evNb, Cluster c, 
            double mcPtk, double mcThetatk, double mcPhitk,
            PrintWriter pw, PrintWriter pw2) {
        //event layer	component	xo	yo	zo	xe	ye	ze	type (BG=0 or Signal=1)	Track number
        double mcP=999;
        double mcTheta=999;
        double mcPhi=999;
        int layr = c.getLayer();
        if(c.getDetector()==DetectorType.BMT)
                layr+=6;
        Point3D ep1=null;
        Point3D ep2=null;
        if(c.getType()==BMTType.C) {
            ep1 = c.getArc().origin();
            ep2 = c.getArc().end();
        } else {
            ep1 = c.getLine().origin();
            ep2 = c.getLine().end();
        }
        int onTrk = c.associatedTrueTrkId;
        if(onTrk==-1) onTrk=0;
        if(onTrk==1) {
            mcP=mcPtk;
            mcTheta=mcThetatk;
            mcPhi=mcPhitk;
       }
        String str = String.format("%d %d %d %.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f %.3f %d %d %d", 
                                evNb, layr, (int)c.getCentroid(), ep1.x(), ep1.y(), ep1.z(), ep2.x(), ep2.y(), ep2.z(), 
                                 mcP, mcTheta, mcPhi,
                                c.BG, 0, onTrk);
        str+="\n";
        //if(onTrk==1)
            pw.print(str);
        //if(onTrk==1)
            //pw2.print(str);
            
    }
    
    private double PidToCharge(int pid) {
        
        if(Math.abs(pid)>44) {
            return (int) Math.signum(pid);
        } else {
            return (int) -Math.signum(pid);
        }
    }
    private Cluster getClosestCluster(double x, double y, double z, List<Cluster> closestClusters) {
        Cluster select = null;
        double doca = Double.POSITIVE_INFINITY;
        for(Cluster cl : closestClusters) {
            if(cl.getType() == BMTType.C) {
                double d = Math.abs(cl.getZ()-z);
                if(d<doca) {
                    doca = d;
                    select = cl;
                }
            } else {
                double d = cl.getLine().distance(new Point3D(x,y,z)).length();
                if(d<doca) {
                    doca = d;
                    select = cl;
                }
            }
        }
        return select;
    }
    
    private List<Cluster> findListofClosestBSTClusters(int layer, double x, double y, double z, List<Cluster> clusters,
            SVTGeometry geo) {
        List<Cluster> matched = new ArrayList<>();
        int trkSecAtLayer = geo.getSector(layer, new Point3D(x, y, z));
        for(Cluster cl : clusters) {
            if(cl.getSector()==trkSecAtLayer) {
                matched.add(cl);
            }
        }
        return matched;
    }
    
    private List<Cluster> findListofClosestBMTClusters(int layer, double x, double y, double z, List<Cluster> clusters,
            BMTGeometry bgeo) {
        List<Cluster> matched = new ArrayList<>();
        int trkSecAtLayer = bgeo.getSector(layer, new Point3D(x,y,z));
        for(Cluster cl : clusters) {
            if(cl.getSector()==trkSecAtLayer) {
                matched.add(cl);
            }
        }
        return matched;
    }
    
    private Map<Integer, List<Cluster>> clusterMap(List<ArrayList<Cluster>> clusters, 
            List<double[]> mcTrks, Swim swimmer, SVTGeometry geo, BMTGeometry bgeo) {
        Map<Integer, List<Cluster>> BSTClustersByLayer = new HashMap<>();
        Map<Integer, List<Cluster>> BMTClustersByLayer = new HashMap<>();
        for(Cluster cl : clusters.get(0)) {
            if(BSTClustersByLayer.containsKey(cl.getLayer())) {
                BSTClustersByLayer.get(cl.getLayer()).add(cl);
            } else {
                BSTClustersByLayer.put(cl.getLayer(), new ArrayList<>());
                BSTClustersByLayer.get(cl.getLayer()).add(cl);
            }
        }
        for(Cluster cl : clusters.get(1)) {
            if(BMTClustersByLayer.containsKey(cl.getLayer())) {
                BMTClustersByLayer.get(cl.getLayer()).add(cl);
            } else {
                BMTClustersByLayer.put(cl.getLayer(), new ArrayList<>());
                BMTClustersByLayer.get(cl.getLayer()).add(cl);
            }
        }
        Map<Integer, List<Cluster>> map = new HashMap<>();
        Map<Integer, List<Cluster>> goodTracks = new HashMap<>();
        int tidx=0;
        for(double[] t : mcTrks) {
            tidx++;
            swimmer.SetSwimParameters(t[0], t[1], t[2], t[3], t[4], t[5], (int) t[6]);
            for(int l =0; l<6; l++) {
                double r = SVTGeometry.getLayerRadius(l+1);
                double[] st = swimmer.SwimRho(r);
                if(BSTClustersByLayer.containsKey(l+1)) {
                    List<Cluster> closestClusters = this.findListofClosestBSTClusters(l+1, 
                            st[0]*10, st[1]*10, st[2]*10, 
                            BSTClustersByLayer.get(l+1),
                            geo);
                    if(!closestClusters.isEmpty()) {
                        int sec = closestClusters.get(0).getSector();
                        Plane3D pl = geo.getPlane(l+1, sec);
                        double d = pl.normal().dot(pl.point().toVector3D())/10;
                        st = swimmer.SwimToPlaneBoundary(d, pl.normal(), 1);
                        Cluster match = this.getClosestCluster(st[0]*10, st[1]*10, st[2]*10, closestClusters);
                        if(map.containsKey(tidx)) {
                            map.get(tidx).add(match);
                        } else {
                            map.put(tidx, new ArrayList<>());
                            map.get(tidx).add(match);
                        }
                    }
                }
            }
            for(int l =0; l < 6; l++) {
                double r = bgeo.getRadiusMidDrift(l+1);
                double[] st = swimmer.SwimRho(r);
                if(BMTClustersByLayer.containsKey(l+1)) {
                    List<Cluster> closestClusters = this.findListofClosestBMTClusters(l+1, 
                            st[0]*10, st[1]*10, st[2]*10, 
                            BMTClustersByLayer.get(l+1),
                            bgeo);
                    if(!closestClusters.isEmpty()) {
                        Cluster match = this.getClosestCluster(st[0]*10, st[1]*10, st[2]*10, closestClusters);
                        if(map.containsKey(tidx)) {
                            map.get(tidx).add(match);
                        } else {
                            map.put(tidx, new ArrayList<>());
                            map.get(tidx).add(match);
                        }
                    }
                }
            }
        }
        for(Integer i : map.keySet()) {
            if(map.get(i).size()>9)  {
                goodTracks.put(i, map.get(i));
            }
        }
        return goodTracks;
    }
    
    private List<double[]> mcTrackPars(DataEvent event) {
        List<double[]> result = new ArrayList<>();
        if (event.hasBank("MC::Particle") == false) {
            return result;
        }
        DataBank bank = event.getBank("MC::Particle");
        
        // fills the arrays corresponding to the variables
        if(bank!=null) {
            for(int i = 0; i < bank.rows(); i++) {
                double[] value = new double[7];
                value[0] = (double) bank.getFloat("vx", i);
                value[1] = (double) bank.getFloat("vy", i);
                value[2] = (double) bank.getFloat("vz", i);
                value[3] = (double) bank.getFloat("px", i);
                value[4] = (double) bank.getFloat("py", i);
                value[5] = (double) bank.getFloat("pz", i);
                value[6] = this.PidToCharge(bank.getInt("pid", i));
                
                result.add(value);
            }
        }
        return result;
    }
   

    
    @Override
    public boolean processDataEvent(DataEvent event) {
        
        Swim swimmer = new Swim();
        
        int run = 11; 
        
        IndexedTable svtStatus          = this.getConstantsManager().getConstants(run, "/calibration/svt/status");
        IndexedTable svtLorentz         = this.getConstantsManager().getConstants(run, "/calibration/svt/lorentz_angle");
        IndexedTable bmtStatus          = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_status");
        IndexedTable bmtTime            = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_time");
        IndexedTable bmtVoltage         = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_voltage");
        IndexedTable bmtStripVoltage    = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_strip_voltage");
        IndexedTable bmtStripThreshold  = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_strip_voltage_thresholds");
        IndexedTable beamPos            = this.getConstantsManager().getConstants(run, "/geometry/beam/position");
        
        Geometry.initialize(this.getConstantsManager().getVariation(), 11, svtLorentz, bmtVoltage);
        
        int ev  = event.getBank("RUN::config").getInt("event", 0);
        CVTReconstruction reco = new CVTReconstruction(swimmer);
        AIHitSelector aihs = new AIHitSelector();
        Map<Double, List<ArrayList<Hit>>> selectedHits = aihs.getHitsWithBg(event, reco, svtStatus, 
                bmtStatus, bmtTime, bmtStripVoltage, bmtStripThreshold);
        if(selectedHits==null) 
            return false;
        ClusterFinder clf = new ClusterFinder();
        
        List<Cluster> useClusters = new ArrayList<>();
        for(Double d : selectedHits.keySet()) {
            List<Cluster> cl1=clf.findClusters(selectedHits.get(d).get(0));
            List<Cluster> cl2=clf.findClusters(selectedHits.get(d).get(1));
            useClusters.addAll(cl1);
            useClusters.addAll(cl2);
        }
        int cid=0;
        for(Cluster c : useClusters) {
            c.setId(cid++);
        }
        List<ArrayList<Cross>>    crosses = reco.findCrosses(useClusters);
        List<ArrayList<Cluster>> useClustersSplit = new ArrayList<>();
        useClustersSplit.add(new ArrayList<>());
        useClustersSplit.add(new ArrayList<>());
        for(Cluster c : useClusters) {
            if(c.getDetector()==DetectorType.BST)
                useClustersSplit.get(0).add(c);
            if(c.getDetector()==DetectorType.BMT)
                useClustersSplit.get(1).add(c);
        }
        
        if(crosses == null) return false;
            
        double[] xyBeam = CVTReconstruction.getBeamSpot(event, beamPos);
        TracksFromTargetRec  trackFinder = new TracksFromTargetRec(swimmer, xyBeam);
        //trackFinder.totTruthHits = reco.getTotalNbTruHits();
        trackFinder.use6LayerSVT=true;
        List<Seed>   seeds = trackFinder.getSeeds(useClustersSplit, crosses);

        List<org.jlab.rec.cvt.track.Track> tracks = trackFinder.getTracks(event, this.isInitFromMc(), 
                                                          this.isKfFilterOn(), 
                                                          this.getKfIterations(), 
                                                          true, this.getPid());

        
       if (event.hasBank("MC::Particle") == false) return false;
        DataBank bank = event.getBank("MC::Particle");
        if(bank==null) return false;
        
        double mcPx = (double) bank.getFloat("px", 0);
        double mcPy = (double) bank.getFloat("py", 0);
        double mcPz = (double) bank.getFloat("pz", 0);
        
        double mcP = Math.sqrt(mcPx*mcPx+mcPy*mcPy+mcPz*mcPz);
        double mcTheta = Math.acos(mcPz/mcP);
        double mcPhi = Math.atan2(mcPy,mcPx);
        
        int ta =1;
        if(tracks!=null) {
            if(tracks.size()>1) return false;
            for(org.jlab.rec.cvt.track.Track t : tracks) {
                t.setId(ta);
                t.getSeed().getClusters().sort(Comparator.comparing(Cluster::getTlayer));
                //this.PrintHitsToFile(ev, t,pw);
                //System.out.println(ev+"; " +t.toString());
                this.PrintClustersToFile(ev, t, mcP, mcTheta, mcPhi, pw, pw2);
                ta++;
            }
        }
        //System.out.println(aihs.svtTrkSectors[0]+" "+aihs.svtTrkSectors[1]+" "+aihs.svtTrkSectors[2] );
        //
        
        for(Cluster c : useClusters) { 
            if(c.getAssociatedTrackID()==-1) {
                double isnotBG = 0;
                double W =0;
                for(Hit h : c) { 
                    W+=h.getStrip().getEdep();
                    if(h.getTrueAssociatedTrackID()!=-1) {
                        isnotBG +=h.getStrip().getEdep();
                    }
                }
                double perTru = isnotBG/W;
                if(perTru>this.perTruHitOnCls) {
                    c.BG=1;
                    for(Hit h : c) {
                        if(h.getTrueAssociatedTrackID()!=-1) {
                            c.associatedTrueTrkId = h.getTrueAssociatedTrackID();
                        }
                    }
                } else {
                    c.BG=0;
                    c.associatedTrueTrkId = -1;
                }
                if(c.getSector()==aihs.svtTrkSectors[c.getRegion()-1] 
                        || c.getSector()-1==aihs.svtTrkSectors[c.getRegion()-1]
                        || c.getSector()+1==aihs.svtTrkSectors[c.getRegion()-1]) {
                    
                    //System.out.println(c.toString());
                    this.PrintUnmatchedClustersToFile(ev, c,  mcP, mcTheta, mcPhi, pw, pw2);
                }
            }
        }
        
        this.nevts++;
        
        return true;
    }

         
    public void loadConfiguration() {            
        
        // general (pass-independent) settings
        if (this.getEngineConfigString("cosmics")!=null) 
            this.isCosmics = Boolean.valueOf(this.getEngineConfigString("cosmics"));
               
        if (this.getEngineConfigString("svtOnly")!=null)
            this.svtOnly = Boolean.valueOf(this.getEngineConfigString("svtOnly"));
        
        if (this.getEngineConfigString("excludeLayers")!=null) 
            this.excludeLayers = this.getEngineConfigString("excludeLayers");
        
        if (this.getEngineConfigString("excludeBMTLayers")!=null) 
            this.excludeBMTLayers = this.getEngineConfigString("excludeBMTLayers");                

        if (this.getEngineConfigString("removeRegion")!=null) 
            this.removeRegion = Integer.valueOf(this.getEngineConfigString("removeRegion"));
        
        if (this.getEngineConfigString("beamSpotConst")!=null)
            this.beamSpotConstraint = Integer.valueOf(this.getEngineConfigString("beamSpotConst"));
        
        if (this.getEngineConfigString("beamSpotRadius")!=null)
            this.beamSpotRadius = Double.valueOf(this.getEngineConfigString("beamSpotRadius"));
            
        if(this.getEngineConfigString("targetMat")!=null)
            this.targetMaterial = this.getEngineConfigString("targetMat");

        if(this.getEngineConfigString("elossPreCorrection")!=null)
            this.elossPrecorrection = Boolean.parseBoolean(this.getEngineConfigString("elossPreCorrection"));
        
        if(this.getEngineConfigString("svtSeeding")!=null)
            this.svtSeeding = Boolean.parseBoolean(this.getEngineConfigString("svtSeeding"));
        
        if(this.getEngineConfigString("timeCuts")!=null) 
            this.timeCuts = Boolean.parseBoolean(this.getEngineConfigString("timeCuts")); 
        
        if(this.getEngineConfigString("hvCuts")!=null) 
            this.hvCuts = Boolean.parseBoolean(this.getEngineConfigString("hvCuts")); 
        
        if(this.getEngineConfigString("useSVTTimingCuts")!=null) 
            this.useSVTTimingCuts = Boolean.parseBoolean(this.getEngineConfigString("useSVTTimingCuts"));
        
        if(this.getEngineConfigString("removeOverlappingSeeds")!=null) 
            this.removeOverlappingSeeds = Boolean.parseBoolean(this.getEngineConfigString("removeOverlappingSeeds"));
        
        if(this.getEngineConfigString("flagSeeds")!=null) 
            this.flagSeeds = Boolean.parseBoolean(this.getEngineConfigString("flagSeeds"));
        
        if(this.getEngineConfigString("gemcIgnBMT0ADC")!=null) 
            this.gemcIgnBMT0ADC = Boolean.parseBoolean(this.getEngineConfigString("gemcIgnBMT0ADC"));

        if(this.getEngineConfigString("KFfailRecovery")!=null) 
            this.KFfailRecovery = Boolean.parseBoolean(this.getEngineConfigString("KFfailRecovery"));
        
        if(this.getEngineConfigString("KFfailRecovMisCls")!=null) 
            this.KFfailRecovMisCls = Boolean.parseBoolean(this.getEngineConfigString("KFfailRecovMisCls"));
        
        if (this.getEngineConfigString("matLib")!=null)
            this.matrixLibrary = this.getEngineConfigString("matLib");
        
        // service dependent configuration settings
        if(this.getEngineConfigString("elossPid")!=null) 
            this.pid = Integer.parseInt(this.getEngineConfigString("elossPid"));       

        if (this.getEngineConfigString("kfFilterOn")!=null)
            this.kfFilterOn = Boolean.valueOf(this.getEngineConfigString("kfFilterOn"));
        
        if (this.getEngineConfigString("initFromMC")!=null)
            this.initFromMc = Boolean.valueOf(this.getEngineConfigString("initFromMC"));
        
        if (this.getEngineConfigString("useOnlyTruthHits")!=null)
            this.useOnlyTruth = Boolean.valueOf(this.getEngineConfigString("useOnlyTruthHits"));
        
        if (this.getEngineConfigString("useSVTLinkerSeeder")!=null)
            this.useSVTLinkerSeeder = Boolean.valueOf(this.getEngineConfigString("useSVTLinkerSeeder"));
        
        if (this.getEngineConfigString("kfIterations")!=null)
            this.kfIterations = Integer.valueOf(this.getEngineConfigString("kfIterations"));
        
        if (this.getEngineConfigString("docacut")!=null)
            this.setDocacut((double) Double.valueOf(this.getEngineConfigString("docacut")));
        
        if (this.getEngineConfigString("docacutsum")!=null)
            this.setDocacutsum((double) Double.valueOf(this.getEngineConfigString("docacutsum")));
        
        if (this.getEngineConfigString("svtmaxclussize")!=null)
            this.setSvtmaxclussize((int) Integer.valueOf(this.getEngineConfigString("svtmaxclussize")));
        
        if (this.getEngineConfigString("bmtcmaxclussize")!=null)
            this.setBmtcmaxclussize((int) Integer.valueOf(this.getEngineConfigString("bmtcmaxclussize")));
        
        if (this.getEngineConfigString("bmtzmaxclussize")!=null)
            this.setBmtzmaxclussize((int) Integer.valueOf(this.getEngineConfigString("bmtzmaxclussize")));
        
        if (this.getEngineConfigString("rcut")!=null)
            this.rcut = Double.valueOf(this.getEngineConfigString("rcut"));
        
        if (this.getEngineConfigString("z0cut")!=null)
            this.z0cut = Double.valueOf(this.getEngineConfigString("z0cut"));
        
    }


    public void initConstantsTables() {
        String[] tables = new String[]{
            "/calibration/svt/status",
            "/calibration/svt/lorentz_angle",
            "/calibration/mvt/bmt_time",
            "/calibration/mvt/bmt_status",
            "/calibration/mvt/bmt_voltage",
            "/calibration/mvt/bmt_strip_voltage",
            "/calibration/mvt/bmt_strip_voltage_thresholds",
            "/geometry/beam/position"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");
    }
    
    
    
    public void printConfiguration() {            
        
        System.out.println("["+this.getName()+"] run with cosmics setting set to "+Constants.getInstance().isCosmics);        
        System.out.println("["+this.getName()+"] run with SVT only set to "+Constants.getInstance().svtOnly);
        if(this.excludeLayers!=null)
            System.out.println("["+this.getName()+"] run with layers "+this.excludeLayers+" excluded in fit, based on yaml");
        if(this.excludeBMTLayers!=null)
            System.out.println("["+this.getName()+"] run with BMT layers "+this.getEngineConfigString("excludeBMTLayers")+" excluded");
        if(this.removeRegion>0)
            System.out.println("["+this.getName()+"] run with region "+this.getEngineConfigString("removeRegion")+" removed");
        System.out.println("["+this.getName()+"] run with beamSpotConst set to "+Constants.getInstance().beamSpotConstraint+ " (0=no-constraint, 1=seed only, 2=seed and KF)");        
        System.out.println("["+this.getName()+"] run with beam spot size set to "+Constants.getInstance().getBeamRadius());                
        System.out.println("["+this.getName()+"] Target material set to "+ Constants.getInstance().getTargetMaterial().getName());
        System.out.println("["+this.getName()+"] Pre-Eloss correction set to " + Constants.getInstance().preElossCorrection);
        System.out.println("["+this.getName()+"] run SVT-based seeding set to "+ Constants.getInstance().svtSeeding);
        System.out.println("["+this.getName()+"] run BMT timing cuts set to "+ Constants.getInstance().timeCuts);
        System.out.println("["+this.getName()+"] run BMT HV masks "+ Constants.getInstance().bmtHVCuts);
        System.out.println("["+this.getName()+"] run with matLib "+ Constants.getInstance().KFMatrixLibrary.toString() + " library");
        System.out.println("["+this.getName()+"] ELoss mass set for particle "+ pid);
        System.out.println("["+this.getName()+"] run with Kalman-Filter status set to "+this.kfFilterOn);
        System.out.println("["+this.getName()+"] initialize KF from true MC information "+this.initFromMc);
        System.out.println("["+this.getName()+"] number of KF iterations set to "+this.kfIterations);
        System.out.println("["+this.getName()+"] SLA doca cut "+this.docacut);
        System.out.println("["+this.getName()+"] SLA docasum cut "+this.docacutsum);
        System.out.println("["+this.getName()+"] max svt  cluster size "+this.getSvtmaxclussize());
        System.out.println("["+this.getName()+"] max bmt-c  cluster size "+this.getBmtcmaxclussize());
        System.out.println("["+this.getName()+"] max btm-z  cluster size "+this.getBmtzmaxclussize());
        System.out.println("["+this.getName()+"] helix radius cut (mm) "+this.rcut);
        System.out.println("["+this.getName()+"] z0 cut (mm from target edges) "+this.z0cut); 
        
    }

   
    
    private void sortTracksByClusterCount(List<Track> tracks) {
        Collections.sort(tracks, new Comparator<Track>() {
            @Override
            public int compare(Track track1, Track track2) {
                int clusterCount1 = track1.getSeed().getClusters().size();
                int clusterCount2 = track2.getSeed().getClusters().size();
                return Integer.compare(clusterCount2, clusterCount1); // Compare in reverse order
            }
        });
    }
    
    public static void main(String[] args) {
        //String inputFile = "/Users/ziegler/BASE/Files/CVTDEBUG/AI/skim3svtcrosses-norecotracks.hipo";
        //String inputFile = "/Users/ziegler/BASE/Files/CVTDEBUG/AI/skim2_rgbbg50na.hipo";
        System.setProperty("CLAS12DIR", "/Users/ziegler/BASE/Tracking/CVT-Issues/AI/coatjava/coatjava");
        String mapDir = CLASResources.getResourcePath("etc")+"/data/magfield";
        System.out.println(mapDir);
        try {
            MagneticFields.getInstance().initializeMagneticFields(mapDir,
                    "Symm_torus_r2501_phi16_z251_24Apr2018.dat","Symm_solenoid_r601_phi1_z1201_13June2018.dat");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        String var = "fall2018_bg";
        String dir = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
        SchemaFactory schemaFactory = new SchemaFactory();
        schemaFactory.initFromDirectory(dir);
        MagFieldsEngine enf = new MagFieldsEngine();
        enf.setVariation(var); 

        enf.init();

        AIHitInfoEngine en = new AIHitInfoEngine();
        en.setVariation(var);
        en.fileName = "munosecSVT4.txt";
        //en.fileName = "goodtrksmunosecSVT.txt";
        en.fileName2 = "wrongtrksmunosecSVT.txt";
        en.init();

        int counter = 0;

        
        long t1 = 0;
        List<String> inputList = new ArrayList<>();
        //inputList.add("/Users/ziegler/BASE/Files/CVTDEBUG/AI/skim1_rgbbg50na.hipo");
        //inputList.add("/Users/ziegler/BASE/Files/CVTDEBUG/AI/skim2_rgbbg50na.hipo");
        //inputList.add("/Users/ziegler/BASE/Files/CVTDEBUG/AI/skim3_rgbbg50na.hipo");
        inputList.add("/Users/ziegler/BASE/Files/CVTDEBUG/AI/skim4_rgbbg50na.hipo");
        
        // org.jlab.clas.analysis.event.Reader ereader = new org.jlab.clas.analysis.event.Reader();
        
        for(String inputFile :  inputList) {
            HipoDataSource reader = new HipoDataSource();
            reader.open(inputFile);
        
            while (reader.hasEvent()) {

                counter++;
                DataEvent event = reader.getNextEvent();
                if (counter > 0) {
                    t1 = System.currentTimeMillis();
                }
                enf.processDataEvent(event);
                en.processDataEvent(event);
                if(counter%1000==0) 
                    System.out.println("PROCESSED "+counter+" EVENTS "+ "GOT "+en.nevts+" SELECTED EVENTS");

            }

            en.pw.close();
            en.pw2.close();
            double t = System.currentTimeMillis() - t1;
            System.out.println(t1 + " TOTAL  PROCESSING TIME = " + (t / (float) counter));
        }

    }

}
