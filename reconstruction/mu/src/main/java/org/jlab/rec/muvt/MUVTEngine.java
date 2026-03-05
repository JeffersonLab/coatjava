package org.jlab.rec.muvt;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.geant4.v2.MPGD.MUVT.MUVTStripFactory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.io.base.*;

import org.jlab.rec.muvt.track.fit.KFitter;
import org.jlab.rec.muvt.track.fit.StateVecs.StateVec;

/**
 * Service to return reconstructed track candidates - the output is in hipo format
 *
 * @author ziegler, benkel, devita
 */
public class MUVTEngine extends ReconstructionEngine {

    boolean debug = false;

    public MUVTStripFactory factory = null;

    public MUVTEngine() {
        super("MUVT", "ziegler", "5.0");
    }

    @Override
    public boolean init() {
        
        String variationName = Optional.ofNullable(this.getEngineConfigString("variation")).orElse("default");
        
        factory = new MUVTStripFactory(11, variationName);

        String[] tables = new String[]{
            "/geometry/beam/position"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation(variationName);
        // Register output banks
        super.registerOutputBank("MUVT::hits");
        super.registerOutputBank("MUVT::clusters");
        super.registerOutputBank("MUVT::crosses");
        super.registerOutputBank("MUVT::tracks");
        super.registerOutputBank("MUVT::trajectory");
        
        return true;
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        // Initial setup.
        if(debug) System.out.println("\nNew event");
        
        // Set run number.
        DataBank runConfig = event.getBank("RUN::config");
        if (runConfig == null || runConfig.rows() == 0) return true;
        int run         = runConfig.getInt("run", 0);
                        
        // Set swimmer.
        Swim swimmer = new Swim();

        // Set beam shift. NOTE: Set to zero for the time being, when beam alignment is done
        //                       uncomment this code.
        IndexedTable beamOffset = this.getConstantsManager().getConstants(run, "/geometry/beam/position");
        double xB = beamOffset.getDoubleValue("x_offset", 0,0,0);
        double yB = beamOffset.getDoubleValue("y_offset", 0,0,0);       
        
                
        // === from STRIPS to CROSSES =============================================================
        List<MUVTStrip>     strips = MUVTStrip.getStrips(event, factory, this.getConstantsManager());
        List<MUVTCluster> clusters = MUVTCluster.createClusters(strips);
        List<MUVTCross>    crosses = MUVTCross.createCrosses(clusters);

        //System.out.println(clusters.size());
        //for (int i = 0; i < clusters.size(); i++) System.out.println(clusters.get(i).toString());
                
        // === DC TRACKS ===========================================================================
        MUVTTrack trk = new MUVTTrack();
        List<MUVTTrack> tracks = trk.getDCTracks(event, swimmer, factory);
        if(tracks.isEmpty()) return true;

        // === SEEDS =============================================================================
        Point3D target = new Point3D(0,0,0);
        for(int i=0; i<tracks.size(); i++) {
            MUVTTrack track = tracks.get(i);                           
           
            List<MUVTCross>[] trackCrosses = new ArrayList[MUVTConstants.NLAYER/2];
            for(int j=0; j<trackCrosses.length; j++) trackCrosses[j] = new ArrayList<>(); 
            for(int j=0; j<crosses.size(); j++) {                    
                    
                MUVTCross cross = crosses.get(j);                    
                
                MUVTTrajectory trj = track.getDCTraj((MUVTConstants.NLAYER/MUVTConstants.NREGION)*cross.getRegion()+1);
                if (trj==null || track.getSector()!=cross.getSector()) continue; 

                // Match the layers from traj.
                double d = cross.point().distance(trj.getPosition());
                double e = cross.getDeltaEnergy();
                if (d < MUVTConstants.CIRCLECONFUSION && Math.abs(e)<MUVTConstants.CROSSDELTAE) {
                    trackCrosses[cross.getRegion()-1].add(cross);
                }
            }
            System.out.println();
            for(int j=0; j<trackCrosses.length; j++) System.out.print(trackCrosses[j].size() + " "); 
                    
            List<List<MUVTCross>> segments = new ArrayList<>();
            List<MUVTCross> end = new ArrayList<>();
            for(int r=6; r>3; r--)
                end.addAll(trackCrosses[r-1]);
            for(MUVTCross ce : end) {
                Line3D ve = new Line3D(ce.point(),target);
                for(int ro=1; ro<ce.getRegion()-3; ro++) {
                    for(MUVTCross co : trackCrosses[ro-1]) {
                        double dei = ce.point().distance(co.point());
                        double dti = ve.distance(co.point()).length();
                        if(dti<0.1*dei) {
                            Line3D vei = new Line3D(ce.point(),co.point());
                            List<MUVTCross> segment = new ArrayList();
                            segment.add(ce);
                            for(int r=co.getRegion()+1; r<ce.getRegion(); r++) {
                                for(MUVTCross cr : trackCrosses[r]) {
                                    if(vei.distance(cr.point()).length()<0.1) {
                                        segment.add(cr);
                                        break;
                                    }
                                }
                            }
                            segment.add(co);
                            if(segment.size()>=4)
                                segments.add(segment);
                        }
                    }
                }
            }
            System.out.print(segments.size());
            if(!segments.isEmpty()) {
                segments.sort(Comparator.comparingInt(List<MUVTCross>::size).reversed());
                for(MUVTCross cross : segments.get(0)) {
                    track.addCluster(clusters.get(cross.getCluster1()-1));
                    track.addCluster(clusters.get(cross.getCluster2()-1));
                }
            }
        }
        
        List<MUVTTrack> filtedTracks = new ArrayList();
        for(MUVTTrack track : tracks){
            if (track.getClusters().size() > 4) filtedTracks.add(track);
        }
        
      
        // === TRACKS ==============================================================================
        KFitter kf = null;
        
        // Iterate on list to run the fit.
        for(int i=0; i<filtedTracks.size(); i++) {
            MUVTTrack track = tracks.get(i);                
                            
            // Set status and stop if there are not at least two measurements to fit against.
            List<MUVTCluster> trackClusters = track.getClusters();           

            kf = new KFitter(track, swimmer, 0);            
            
            kf.runFitter(track.getSector());


            // Do one last KF pass with filtering off to get the final Chi^2.
            kf.totNumIter = 1;
            kf.filterOn   = false;
            kf.runFitter(track.getSector());

            if (kf.finalStateVec != null) {
                StateVec sv = kf.finalStateVec;
                
                // swim to beamline to get vertex parameters
                int charge = (int)Math.signum(sv.Q);
                
                Point3D posGlobal = track.transLocaltoGlobal(track.getSector(), sv.x, sv.y, sv.z);                
                Point3D momGlobal = track.transLocaltoGlobal(track.getSector(), sv.getPx()/sv.getP()*track.getP(), sv.getPy()/sv.getP()*track.getP(), sv.getPz()/sv.getP()*track.getP());  
                
                swimmer.SetSwimParameters(posGlobal.x(),posGlobal.y(),posGlobal.z(), -momGlobal.x(),-momGlobal.y(),-momGlobal.z(),-charge);
                double[] Vt = swimmer.SwimToBeamLine(xB, yB);
                
                // if successful, save track parameters
                if(Vt == null) continue;
                track.setStatus(0);
                track.setNDF(trackClusters.size());
                track.setQ(charge);
                track.setChi2(kf.chi2);
                track.setX(Vt[0]);
                track.setY(Vt[1]);
                track.setZ(Vt[2]);
                track.setPx(-Vt[3]);
                track.setPy(-Vt[4]);
                track.setPz(-Vt[5]);
            }
        }
                
        this.writeHipoBanks(event, strips, clusters, crosses, tracks);
        return true;
   }


    
    private void writeHipoBanks(DataEvent de, 
                                List<MUVTStrip>     strips, 
                                List<MUVTCluster> clusters, 
                                List<MUVTCross>    crosses, 
                                List<MUVTTrack>    tracks){
        
        DataBank bankS = de.createBank("MUVT::hits", strips.size());
        for(int h = 0; h < strips.size(); h++){
            bankS.setShort("id",        h, (short) strips.get(h).getId());
            bankS.setByte("sector",     h,  (byte) strips.get(h).getDescriptor().getSector());
            bankS.setByte("layer",      h,  (byte) strips.get(h).getDescriptor().getLayer());
            bankS.setShort("strip",     h, (short) strips.get(h).getDescriptor().getComponent());
            bankS.setFloat("energy",    h, (float) strips.get(h).getEnergy());
            bankS.setFloat("time",      h, (float) strips.get(h).getTime());                
            bankS.setShort("status",    h, (short) strips.get(h).getStatus());
            bankS.setShort("clusterId", h, (short) strips.get(h).getClusterId());
        }
        
        DataBank bankC = de.createBank("MUVT::clusters", clusters.size());        
        for(int c = 0; c < clusters.size(); c++){
            bankC.setShort("id",       c, (short) clusters.get(c).getId());
            bankC.setByte("sector",    c,  (byte) clusters.get(c).get(0).getDescriptor().getSector());
            bankC.setByte("layer",     c,  (byte) clusters.get(c).get(0).getDescriptor().getLayer());
            bankC.setShort("strip",    c, (short) clusters.get(c).getMaxStrip());
            bankC.setFloat("energy",   c, (float) clusters.get(c).getEnergy());
            bankC.setFloat("time",     c, (float) clusters.get(c).getTime());
            bankC.setFloat("xo",       c, (float) clusters.get(c).getLine().origin().x());
            bankC.setFloat("yo",       c, (float) clusters.get(c).getLine().origin().y());
            bankC.setFloat("zo",       c, (float) clusters.get(c).getLine().origin().z());
            bankC.setFloat("xe",       c, (float) clusters.get(c).getLine().end().x());
            bankC.setFloat("ye",       c, (float) clusters.get(c).getLine().end().y());
            bankC.setFloat("ze",       c, (float) clusters.get(c).getLine().end().z());
            bankC.setShort("size",     c, (short) clusters.get(c).size());
            bankC.setShort("status",   c, (short) clusters.get(c).getStatus()); 
        }       
        
        DataBank bankX = de.createBank("MUVT::crosses", crosses.size());        
        for(int c = 0; c < crosses.size(); c++){
            bankX.setShort("id",       c, (short) crosses.get(c).getId());
            bankX.setByte("sector",    c,  (byte) crosses.get(c).getSector());
            bankX.setByte("region",    c,  (byte) crosses.get(c).getRegion());
            bankX.setFloat("energy",   c, (float) crosses.get(c).getEnergy());
            bankX.setFloat("time",     c, (float) crosses.get(c).getTime());
            bankX.setFloat("x",        c, (float) crosses.get(c).point().x());
            bankX.setFloat("y",        c, (float) crosses.get(c).point().y());
            bankX.setFloat("z",        c, (float) crosses.get(c).point().z());
            bankX.setShort("cluster1", c, (short) crosses.get(c).getCluster1()); 
            bankX.setShort("cluster2", c, (short) crosses.get(c).getCluster2()); 
            bankX.setShort("status",   c, (short) crosses.get(c).getStatus()); 
        }       


        DataBank bankT = de.createBank("MUVT::tracks", tracks.size());
        for (int i=0; i<tracks.size(); i++) {
            bankT.setShort("index",  i, (short) tracks.get(i).getIndex());
            bankT.setByte( "status", i, (byte)  tracks.get(i).getStatus());
            bankT.setByte( "sector", i, (byte)  tracks.get(i).getSector());
            bankT.setByte( "charge", i, (byte)  tracks.get(i).getQ());
            bankT.setFloat("chi2",   i, (float) tracks.get(i).getChi2());
            bankT.setByte( "NDF",    i, (byte)  tracks.get(i).getNDF());
            bankT.setFloat("vx",     i, (float) tracks.get(i).getX());
            bankT.setFloat("vy",     i, (float) tracks.get(i).getY());
            bankT.setFloat("vz",     i, (float) tracks.get(i).getZ());
            bankT.setFloat("px",     i, (float) tracks.get(i).getPx());
            bankT.setFloat("py",     i, (float) tracks.get(i).getPy());
            bankT.setFloat("pz",     i, (float) tracks.get(i).getPz());
        }
        de.appendBanks(bankS,bankC,bankX,bankT);

    }
}