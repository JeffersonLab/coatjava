package org.jlab.rec.rich;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import org.jlab.io.base.DataEvent;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.detector.base.DetectorType;
import org.jlab.clas.detector.DetectorEvent;
import org.jlab.clas.detector.DetectorParticle;
import org.jlab.clas.detector.DetectorResponse;
import org.jlab.clas.detector.DetectorTrack;
import org.jlab.clas.detector.DetectorData;
import org.jlab.clas.detector.RingCherenkovResponse;
import org.jlab.io.base.DataBank;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.clas.pdg.PhysicsConstants;
import org.jlab.detector.geom.RICH.RICHGeoFactory;

public class RICHEventBuilder{
    
    public int Neve = 0;
    private RICHio                   richio;
    private RICHEvent                richevent;
    private RICHGeoFactory           richgeo;
    private DetectorEvent            clasevent;
    private HashMap<Integer,Integer> pindex_map = new HashMap<>();
    
    public RICHEventBuilder(DataEvent event, RICHEvent richeve, RICHGeoFactory richgeo, RICHio richio) {
        this.richevent      =   richeve;
        this.richio         =   richio;
        this.richgeo        =   richgeo;
        clasevent   =   new DetectorEvent();
        clasevent.clear();
        richevent.clear();
        set_EventInfo(event);
    }
    
    public boolean process_Data(DataEvent event, RICHParameters richpar, RICHCalibration richcal, RICHRayTrace richtrace) {
        
        //   look for RICH - DC matches
        if(!process_DCData(event, richpar)) return false;

        if(richio!=null) richio.write_RECBank(event, richevent, richpar);
        
        //   create RICH particles
        if(!process_RICHData(event, richtrace, richpar, richcal)) return false;
        
        //   analytic solution (direct light only)
        if(!analyze_Cherenkovs(richtrace, richpar)) return false;
        
        //   ray-traced solution (all photons)
        if(!reco_Cherenkovs(richtrace, richpar, richcal)) return false;
        
        if(richio!=null) richio.write_CherenkovBanks(event, richevent, richpar);
        
        return true;
    }
    
    
    public boolean process_DCData(DataEvent event, RICHParameters richpar) {
        
        // Load tracks from time based tracking
        if(!read_ForwardTracks(event)) return false;
        
        // Load the cluster information
        if(richpar.USE_SIGNAL_BANK==1){
            richevent.add_ResClus( RingCherenkovResponse.readHipoEvent(event, "RICH::Signal",DetectorType.RICH,1) );
        }else{
            richevent.add_ResClus( RingCherenkovResponse.readHipoEvent(event, "RICH::Cluster",DetectorType.RICH,1) );
        }
        
        // Perform the DC tracks to RICH clusters matching
        process_HitMatching(richpar);
        
        if(!clasevent.getParticles().isEmpty())richevent.add_Matches(getRichResponseList());
        
        return true;
    }
    
    
    public boolean process_RICHData(DataEvent event, RICHRayTrace richtrace, RICHParameters richpar, RICHCalibration richcal) {
        if(richpar.USE_SIGNAL_BANK==1){
            richevent.add_ResHits( RingCherenkovResponse.readHipoEvent(event, "RICH::Signal",DetectorType.RICH,0) );
        }else{
            richevent.add_ResHits( RingCherenkovResponse.readHipoEvent(event, "RICH::Hit",DetectorType.RICH,0) );
        }
        if(!clasevent.getParticles().isEmpty()){
            if(find_Hadrons(richtrace, richcal, richpar)){
                if(richevent.get_nResHit()>0){
                    find_Photons(richevent.get_ResHits(), richpar, richcal);
                }
            }
        }
        return true;
    }
    
    
    public boolean read_ForwardTracks(DataEvent event) {
        
        if(event.hasBank("REC::Track") && event.hasBank("REC::Particle") && event.hasBank("REC::Traj")){
            
            DataBank tbank = event.getBank("REC::Track");
            DataBank pbank = event.getBank("REC::Particle");
            DataBank rbank = event.getBank("REC::Traj");
            
            for(int i = 0 ; i < tbank.rows(); i++){
                
                int itk = (int) tbank.getShort("index",i);
                int ipr = (int) tbank.getShort("pindex",i);
                int idet = (int) tbank.getByte("detector",i);
                int isec = (int) tbank.getByte("sector", i);
                
                int charge = tbank.getByte("q", i);
                Vector3D pvec = DetectorData.readVector(pbank, ipr, "px", "py", "pz");
                Vector3D vertex = DetectorData.readVector(pbank, ipr, "vx", "vy", "vz");
                int CLASpid = check_CLASpid( pbank.getInt("pid",ipr) );
                
                DetectorTrack  tr = new DetectorTrack(charge,pvec.mag(),i);
                tr.setVector(pvec.x(), pvec.y(), pvec.z());
                tr.setVertex(vertex.x(), vertex.y(), vertex.z());
                tr.setSector(isec);
                
                // disregard central detector tracks
                if(idet!=6)continue;
                
                if(!richgeo.has_RICH(isec)) continue;
                
                int naero_cross = 0;
                int ntraj_cross = 0;
                double traj_path[] = {0.0, 0.0, 0.0};
                double aero_path[] = {0.0, 0.0, 0.0};
                Line3D traj_cross[]  = new Line3D[3];
                Line3D aero_cross[]  = new Line3D[3];
                int    aero_lay[]    = {0,0,0};
                for (int j=0; j<rbank.rows(); j++){
                    int jpr = (int) rbank.getShort("pindex",j);
                    int jtk = (int) rbank.getShort("index",j);
                    int jdet = (int) rbank.getByte("detector",j);
                    int jlay = (int) rbank.getByte("layer",j);
                    if (jpr==ipr && jtk==itk){
                        
                        // trajectory plane 42 till 5b.6.2 - 40 since 5c.7.0 - layer 36 since 6b.1.0
                        if( (jdet==DetectorType.DC.getDetectorId() && jlay==36) || jdet==DetectorType.RICH.getDetectorId()){
                            double jx =  (double) rbank.getFloat("x",j);
                            double jy =  (double) rbank.getFloat("y",j);
                            double jz =  (double) rbank.getFloat("z",j);
                            double jcx =  (double) rbank.getFloat("cx",j);
                            double jcy =  (double) rbank.getFloat("cy",j);
                            double jcz =  (double) rbank.getFloat("cz",j);
                            double path =  (double) rbank.getFloat("path",j);
                            
                            Vector3D vdir = new Vector3D(jcx, jcy, jcz);
                            if(!vdir.unit()) continue;
                            
                            if(jdet==DetectorType.DC.getDetectorId() && jlay==36){
                                traj_cross[0] = new Line3D(jx, jy, jz, vdir.x(), vdir.y(), vdir.z());
                                traj_path[0] = path;
                                ntraj_cross++;
                            }
                            if(jdet==DetectorType.RICH.getDetectorId() && jlay==1){
                                traj_cross[1] = new Line3D(jx, jy, jz, vdir.x(), vdir.y(), vdir.z());
                                traj_path[1] = path;
                                ntraj_cross++;
                            }
                            if(jdet==DetectorType.RICH.getDetectorId() && jlay>=2 && jlay<=4){
                                aero_cross[naero_cross] = new Line3D(jx, jy, jz, vdir.x(), vdir.y(), vdir.z());
                                aero_path[naero_cross] = path;
                                aero_lay[naero_cross] = jlay;
                                naero_cross++;
                            }
                        }
                    }
                }
                
                //overwrite DC3 with first AERO if present
                if(naero_cross>0){
                    double minpath = 999.;
                    for (int ia=0; ia<naero_cross; ia++){
                        if(aero_path[ia]>0 && aero_path[ia]<minpath){
                            if(traj_path[0]==0.0)ntraj_cross++;
                            traj_cross[0] = new Line3D( aero_cross[ia].origin(), aero_cross[ia].end());
                            traj_path[0] = aero_path[ia];
                            minpath = aero_path[ia];
                        }
                    }
                }
                
                int detid = DetectorType.RICH.getDetectorId();
                for (int k=0; k<ntraj_cross; k++){
                    if(traj_path[k]>0){
                        tr.addCross(traj_cross[k].origin().x(), traj_cross[k].origin().y(), traj_cross[k].origin().z(),
                            traj_cross[k].end().x(), traj_cross[k].end().y(), traj_cross[k].end().z() );
                        tr.getTrajectory().add(new DetectorTrack.TrajectoryPoint(detid, k, traj_cross[k], (float) traj_path[k], (float) 0., (float) 0.));
                        tr.setPath(traj_path[k]);
                    }
                    
                }
                
                tr.setStatus(ipr);
                
                DetectorParticle particle = new DetectorParticle(tr);
                particle.setPid(CLASpid);
                particle.setBeta( particle.getTheoryBeta(CLASpid) );
                particle.setMass( PDGDatabase.getParticleById(CLASpid).mass() );
                // ATT: FIX ME!
                clasevent.addParticle(particle);
                
            }
            
        }else{
            
            if(event.hasBank("TimeBasedTrkg::TBTracks") && event.hasBank("TimeBasedTrkg::Trajectory") && event.hasBank("TimeBasedTrkg::TBCovMat")){
                
                String trackBank = "TimeBasedTrkg::TBTracks";
                String tracjBank = "TimeBasedTrkg::Trajectory";
                String covBank   = "TimeBasedTrkg::TBCovMat";
                List<DetectorTrack>  tracks = DetectorData.readDetectorTracks(event, trackBank, tracjBank, covBank);
                
                for(int i = 0 ; i < tracks.size(); i++){
                    DetectorTrack tr = tracks.get(i);
                    if(tr.getSector()==4){
                        tr.setStatus(i);
                        DetectorParticle particle = new DetectorParticle(tr);
                        particle.setPid(211);
                        clasevent.addParticle(particle);
                    }
                }
                
            }else{
                return false;
            }
        }
        
        return !clasevent.getParticles().isEmpty();
    }
    
    
    public void process_HitMatching(RICHParameters richpar){
        
        int np = clasevent.getParticles().size();
        for(int n = 0; n < np; n++){
            DetectorParticle  p = this.clasevent.getParticle(n);
            
            // Matching tracks to RICH: from LastCross minimum distance in any direction
            Double rich_match_cut = richpar.RICH_DCMATCH_CUT;
            int index = p.getDetectorHit(richevent.get_ResClus(), DetectorType.RICH, -1, rich_match_cut);
            //int index = getDetectorHit(p, richevent.get_ResClus(), DetectorType.RICH, -1, rich_match_cut);
            if(index>=0){
                
                DetectorResponse res = richevent.get_ResClu(index);
                Point3D ocross = p.getLastCross().origin();
                double dz = res.getPosition().z() - ocross.z();
                
                // while storing the match, calculates the matched position as middle point between track and hit
                // and path (track last cross plus distance to hit) assuming the hit is downstream of last cross
                p.addResponse(res, true);
                if(dz<0){
                    // go backward instead of forward from LastCross
                    double extra = p.getPathLength(res.getPosition());
                    res.setPath( res.getPath()-2*extra);
                }
                
                // Status brings the pindex of the original track to fix the hipo cross-indexes
                res.setAssociation(p.getTrackStatus());
            }
            
        }
    }
    
    private void set_EventInfo(DataEvent event) {
        
        if(event.hasBank("REC::Event")==true){
            DataBank bankeve = event.getBank("REC::Event");
            float evttime = bankeve.getFloat("startTime",0);
            richevent.set_EventTime(evttime);
        }
        
        if(event.hasBank("RUN::config")==true){
            DataBank bankrun = event.getBank("RUN::config");
            richevent.set_RunID(bankrun.getInt("run",0));
            richevent.set_EventID(bankrun.getInt("event",0));
            int phase = (int) (bankrun.getLong("timestamp",0)%2);
            richevent.setFTOFphase(phase);
        }

    }
    
    
    public int get_NClasParticle() {
        return clasevent.getParticles().size();
    }
    
    public int get_nMatch() {
        return richevent.get_nMatch();
    }
    
    
    public boolean find_Hadrons(RICHRayTrace richtrace, RICHCalibration richcal, RICHParameters richpar) {
        
        int hindex = 0;
        for(DetectorParticle p : clasevent.getParticles()){
            
            double theta = p.vector().theta();
            int CLASpid  = check_CLASpid( p.getPid() );
            double CLASbeta = p.getBeta();
            
            DetectorResponse r = null;
            DetectorResponse exr = null;
            int nr = 0;
            int nexr = 0;
            double RICHtime = 0.0;
            double Match_chi2 = 0.0;
            int RICHiclu = -1;
            for(DetectorResponse rtest : p.getDetectorResponses()){
                if(rtest.getDescriptor().getType()==DetectorType.RICH){
                    r = rtest;
                    RICHtime = richevent.get_EventTime() + r.getPath()/CLASbeta/(PhysicsConstants.speedOfLight());
                    RICHiclu = r.getHitIndex();
                    double TRACKtime = richevent.get_EventTime() + p.getPathLength()/CLASbeta/(PhysicsConstants.speedOfLight());
                }
            }
            
            if( (nr==1) || (nr==0 && richpar.DO_MIRROR_HADS==1) ){
                exr = extrapolate_RICHResponse(p, r, richtrace);
                if(exr!=null) nexr++;
            }
            
            // define the response treatment in special cases
            if(nexr==0) continue;
            if(nr==1) Match_chi2 = 2*exr.getMatchedDistance()/richpar.RICH_HITMATCH_RMS;
            
            RICHParticle richhadron = new RICHParticle(hindex, p, exr, richpar);
            richhadron.set_StartTime(richevent.get_EventTime());
            richhadron.traced.set_time(exr.getTime());
            richhadron.traced.set_machi2(Match_chi2);
            
            if(!richhadron.find_AerogelPoints(richtrace, richcal) ) {
                continue;
            }
            
            richhadron.traced.set_path((float) richhadron.get_HitPos().distance(richhadron.aero_middle));
            
            if(!richhadron.set_rotated_points() ) {
                System.out.println(" ERROR: no rotation found \n");
                continue;
            }
            
            richevent.add_Hadron(richhadron);
            hindex++;
        }
        
        return richevent.get_nHad()>0;
    }
    
    public boolean find_Photons(List<DetectorResponse>  RichHits, RICHParameters richpar, RICHCalibration richcal){
        
        if(RichHits==null) return false;
        
        int id = 0;
        for(RICHParticle richhadron : richevent.get_Hadrons()){
            
            for(int hypo=0; hypo<RICHConstants.N_HYPO; hypo++){
                
                if(!is_WantedHypo(richpar, hypo))continue;
                for(int k=0 ; k<RichHits.size(); k++) {
                    
                    if(richhadron.get_sector() != RichHits.get(k).getDescriptor().getSector()) continue;
                    
                    Point3D dummy= new Point3D(0., 0., 0.);
                    RICHParticle photon = new RICHParticle(id, richhadron, RichHits.get(k), dummy, richpar);
                    
                    photon.set_rotated_points(richhadron);
                    photon.set_PixelProp(richcal);
                    photon.set_type(hypo);
                    int hypo_pid = RICHConstants.HYPO_LUND[hypo];
                    photon.traced.set_hypo(hypo_pid);
                    
                    richevent.add_Photon(photon);
                    id++;
                }
                
            }
        }
        
        return richevent.get_nPho()>0;
        
    }
    
    public boolean analyze_Cherenkovs(RICHRayTrace richtrace, RICHParameters richpar) {
        
        if(richpar.DO_ANALYTIC==1){
            
            int hypo = 1;
            richevent.analyze_Photons(hypo, richtrace);
            
            for(RICHParticle richhadron : richevent.get_Hadrons()){
                if (richhadron.get_Status()==1){
                    //richevent.get_ChMean(richhadron, hypo, 0);
                    richevent.get_pid(richhadron, 0, richpar);
                }
            }
        }
        return true;
    }
    
    
    public boolean is_WantedHypo(RICHParameters richpar, int hypo) {
        
        if(hypo==0 && richpar.THROW_ELECTRONS==1) return true;
        if(hypo==1 && richpar.THROW_PIONS    ==1) return true;
        if(hypo==2 && richpar.THROW_KAONS    ==1) return true;
        if(hypo==3 && richpar.THROW_PROTONS  ==1) return true;
        return false;
    }
    
    
    public boolean reco_Cherenkovs(RICHRayTrace richtrace, RICHParameters richpar, RICHCalibration richcal) {
        
        int recotype = RICHRecoType.TRACED.id();
        for(RICHParticle richhadron : richevent.get_Hadrons()){
            
            int Ntrials = richpar.THROW_PHOTON_NUMBER;
            
            for (int hypo=0; hypo<RICHConstants.N_HYPO ; hypo++){
                
                if(!is_WantedHypo(richpar, hypo)) continue;
                
                richevent.throw_Photons(richhadron, Ntrials, hypo, richtrace, richpar, richcal);
                
                if(richpar.TRACE_PHOTONS==1){
                    
                    richevent.associate_Throws(richhadron, hypo, richpar);
                    
                    richevent.trace_Photons(richhadron, hypo, richtrace, richcal);
                    //richevent.get_ChMean(richhadron, hypo, 1);
                }
            }
            
            /*for (int hypo=0; hypo<RICHConstants.N_HYPO ; hypo++){
            if(!is_WantedHypo(richpar, hypo)) continue;
            if(richpar.TRACE_PHOTONS==1){
            richevent.select_Photons(hypo, 1, richpar);
            }
            }*/
            
            richevent.select_Photons(richhadron, recotype, richpar);
            if(richpar.DO_PASS2_LIKE==1)richevent.get_HypoPID(richhadron, recotype, richpar);
            if(richpar.DO_PASS1_LIKE==1)richevent.get_pid(richhadron, recotype, richpar);
            if(richpar.DO_LHCB_LIKE==1)richevent.get_LHCbpid(richhadron, recotype, richpar);
            
        }
        
        return true;
    }
    
    
    public DetectorResponse extrapolate_RICHResponse(DetectorParticle p, DetectorResponse r, RICHRayTrace richtrace){
        
        int imir=0;
        Point3D extra = richtrace.find_IntersectionMAPMT( p.getTrackSector(), p.getLastCross() );
        if(extra!=null){
        }else{
            imir=1;
            extra = richtrace.find_IntersectionSpheMirror( p.getTrackSector(), p.getLastCross() );
        }
        if(extra==null) return null;
        
        // this extrapath is correct being calculated along the extrapolated trajectory
        double extrapath = extra.distance( p.getLastCross().origin() );
        if(extra.z()<p.getLastCross().origin().z()) extrapath*=-1;
        
        DetectorResponse exr = new DetectorResponse( p.getTrackSector(), imir, 0);
        exr.getDescriptor().setType(DetectorType.RICH);
        //ATT: this path is correctly extrapolatd along the trajectory
        //ATT: setPath should find r.getPath() if not zero value!
        exr.setPath(p.getPathLength() + extrapath);
        
        if(r!=null){
            
            Point3D rpos = r.getPosition().toPoint3D();
            Line3D  lmatch = new Line3D(rpos, extra);
            Point3D rmatch = lmatch.midpoint();
            exr.setPosition( rpos.x(), rpos.y(), rpos.z() );
            exr.setHitIndex( r.getHitIndex() );
            exr.setMatchPosition( rmatch.x(), rmatch.y(), rmatch.z());
            exr.setTime (r.getTime());
            exr.setStatus(1);
            exr.getDescriptor().setSectorLayerComponent( r.getDescriptor().getSector(), r.getDescriptor().getLayer(), r.getDescriptor().getComponent());
            
        }else{
            
            exr.setPosition( extra.x(), extra.y(), extra.z() );
            exr.setMatchPosition( extra.x(), extra.y(), extra.z());
            exr.setHitIndex( -1 );
            exr.setStatus(0);
            double CLASbeta = p.getBeta();
            exr.setTime( richevent.get_EventTime() + exr.getPath()/CLASbeta/(PhysicsConstants.speedOfLight()) );
            
        }
        
        return exr;
    }
    
    
    public int check_CLASpid(int pid) {
        /*
        *  force electron pid when in trouble
        *  (only gamma, e, pi, proton and k are allowed)
        */
        
        int checkpid = 0;
        if (Math.abs(pid)==22 || Math.abs(pid)==11 || Math.abs(pid)==211 | Math.abs(pid)==321 || Math.abs(pid)==2212) {
            checkpid = pid;
        }else{
            checkpid=11;
            if(pid<0)checkpid*=-1;
        }
        return checkpid;
    }
    
    
    public double Pi_Likelihood(double angolo) {
        double mean = 0.307;
        double sigma= 0.004;
        double Norm = Math.log(1+1/ sigma*(Math.sqrt(2* Math.PI)));
        double Argomento = 1+Math.exp((-0.5)*Math.pow((angolo - mean)/sigma, 2) )/ sigma*(Math.sqrt(2* Math.PI));
        double LikeLihood=  Math.log(Argomento)/Norm;
        return LikeLihood;
    }
    
    
    public Point3D Outer_Intersection(Point3D first, Point3D second, Vector3D direction ) {
        
        Point3D Emissione = new Point3D(0,0,0);
        // Define a Vector3D as: Second point of intersection  Minus first point of intersection
        Vector3D V_inter = second.vectorFrom(first);
        // See if V_int is pointing in the direction of the track. In this case the first point is the entrance and the second one is the exit
        if( V_inter.asUnit().dot(direction.asUnit()) >0  ) {
            Emissione.setX( second.x() );
            Emissione.setY( second.y() );
            Emissione.setZ( second.z() );
        }
        //Otherwise the first point is the exit point from the volume so I need to change the order
        else
        {
            Emissione.setX( first.x() );
            Emissione.setY( first.y() );
            Emissione.setZ( first.z() );
        }
        return Emissione;
    }
    
    
    public ArrayList<DetectorResponse>  getRichResponseList(){
        clasevent.setAssociation();
        ArrayList<DetectorResponse> responses = new ArrayList<>();
        for(DetectorParticle p : clasevent.getParticles()){
            for(DetectorResponse r : p.getDetectorResponses()){
                if(r.getDescriptor().getType()==DetectorType.RICH)
                    responses.add(r);
            }
        }
        return responses;
    }
    
    
    public HashMap<Integer, Integer> getPindexMap() {
        return this.pindex_map;
    }
    
    
    public void show_Particle(DetectorParticle pr) {
        System.out.format("    Particle pid %4d   mass %9.4f   beta %8.5f   vert %8.2f %8.2f %8.2f   mom %8.3f %8.3f %8.3f \n",
            pr.getPid(),pr.getMass(),pr.getBeta(),
            pr.vertex().x(),pr.vertex().y(),pr.vertex().z(),
            pr.vector().x(),pr.vector().y(),pr.vector().z());
    }
    
    
    public void show_Track(DetectorTrack tr) {
        Line3D first = tr.getFirstCross();
        Line3D last= tr.getLastCross();
        Point3D ori = first.origin();
        Point3D end = last.origin();
        System.out.format("    Track id %4d %4d  sec %4d   path %8.1f   origin  %8.2f %8.2f %8.2f   end %8.2f %8.2f %8.2f \n",
            tr.getStatus(),
            tr.getTrackIndex(),
            tr.getSector(),
            tr.getPath(),
            ori.x(),ori.y(),ori.z(),
            end.x(),end.y(),end.z());
    }
    
}
