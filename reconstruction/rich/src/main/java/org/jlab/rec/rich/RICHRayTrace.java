package org.jlab.rec.rich;

import org.jlab.detector.geom.RICH.RICHLayer;
import org.jlab.detector.geom.RICH.RICHRay;
import org.jlab.detector.geom.RICH.RICHComponent;
import org.jlab.detector.geom.RICH.RICHIntersection;
import org.jlab.detector.geom.RICH.RICHGeoConstants;
import org.jlab.detector.geom.RICH.RICHGeoFactory;
import java.util.ArrayList;
import org.jlab.geom.prim.Vector3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.freehep.math.minuit.FCNBase;
import org.freehep.math.minuit.FunctionMinimum;
import org.freehep.math.minuit.MnMigrad;
import org.freehep.math.minuit.MnUserParameters;
import org.jlab.clas.pdg.PhysicsConstants;

/**
 *
 * @author mcontalb
 */
public class RICHRayTrace{
    
    private final static RICHGeoConstants geocost =  new RICHGeoConstants();
    
    private RICHGeoFactory richgeo;
    private RICHParameters  richpar;
    
    public RICHRayTrace() {}
    
    public RICHRayTrace(RICHGeoFactory richgeo, RICHParameters richpar){
        this.richgeo = richgeo;
        this.richpar = richpar;
    }
    
    public RICHLayer get_Layer(int isec, String slay){
        return richgeo.get_Layer(isec, slay);
    }
    
    public RICHLayer get_Layer(int isec, int ilay){
        return richgeo.get_Layer(isec, ilay);
    }
    
    public RICHComponent get_Component(int isec, int ilay, int ico){
        return richgeo.get_Layer(isec, ilay).get(ico);
    }
    
    public Vector3D Reflection(Vector3D vector1, Vector3D normal) {
        
        Vector3D vin = vector1.asUnit();
        Vector3D vnorm = normal.asUnit();
        
        double cosI  =  vin.dot(vnorm);
        if (cosI > 0) {
            vnorm.scale(-1.0);
        }
        
        double refle = 2*(vin.dot(vnorm));
        Vector3D vout = vin.sub(vnorm.multiply(refle));
        
        return vout.asUnit();
    }
    
    public Vector3D Transmission2(Vector3D vector1, Vector3D normal, double n_1, double n_2) {
        
        double rn = n_1 / n_2;
        
        Vector3D vin = vector1.asUnit();
        Vector3D vnorm = normal.asUnit();
        
        double cosI  =  vin.dot(vnorm);
        if (cosI < 0) {
            vnorm.scale(-1.0);
        }
        
        Vector3D vrot = (vnorm.cross(vin)).asUnit();
        
        double angi = Math.acos(vin.dot(vnorm)) ;
        double ango = Math.asin( rn * Math.sin(angi));
        
        Quaternion q = new Quaternion(ango, vrot);
        
        Vector3D vout = q.rotate(vnorm);
        
        return vout;
    }
    
    public RICHRay OpticalRotation(RICHRay rayin, RICHIntersection intersection) {
        
        Point3D vori = rayin.origin();
        Vector3D inVersor = rayin.direction().asUnit();
        Vector3D newVersor = new Vector3D(0.0, 0.0, 0.0);
        RICHRay rayout = null;
        int type = 0;
        
        RICHLayer layer = richgeo.get_Layer(intersection.get_sector(), intersection.get_layer());
        
        if(layer.is_optical()==true){
            
            Vector3D vnorm = intersection.get_normal();
            if(vnorm != null ){
                if(layer.is_mirror()==true){
                    newVersor = Reflection(inVersor, vnorm);
                    type=10000+intersection.get_layer()*100+intersection.get_component()+1;
                }else{
                    newVersor = Transmission2(inVersor, vnorm, intersection.get_nin(), intersection.get_nout());
                    type=20000+intersection.get_layer()*100+intersection.get_component()+1;
                }
            }
        }
        
        rayout = new RICHRay(vori, newVersor.multiply(200));
        rayout.set_type(type);
        return rayout;
    }
    
    public ArrayList<RICHRay> RayTrace(RICHParticle photon, Vector3D vlab) {
        RICHLayer layer = get_Layer(photon.get_sector(), photon.ilay_emission);
        return RayTrace(photon, vlab, layer.get(photon.ico_emission).get_index());
    }
    
    
    public ArrayList<RICHRay> RayTrace(RICHParticle photon, Vector3D vlab, double naero) {
        // return the hit position on the PMT plane of a photon emitted at emission with direction vlab
        
        ArrayList<RICHRay> raytracks = new ArrayList<RICHRay>();
        
        int orilay = photon.ilay_emission;
        int orico  = photon.ico_emission;
        int isec   = photon.get_sector();
        Point3D emi = photon.lab_emission;
        Vector3D vdir = vlab;
        
        RICHRay lastray = new RICHRay(emi, vdir.multiply(200));
        RICHLayer layer = get_Layer(isec, orilay);
        if(layer==null)return null;
        
        RICHIntersection first_intersection = null;
        if(richpar.DO_CURVED_AERO==1){
            first_intersection = layer.find_ExitCurved(lastray.asLine3D(), orico);
        }else{
            first_intersection = layer.find_Exit(lastray.asLine3D(), orico);
        }
        if(first_intersection==null)return null;
        
        Point3D new_pos = first_intersection.get_pos();
        RICHRay oriray = new RICHRay(emi, new_pos);
        
        /* rewrite the refractive index to be consistent with photon theta
        only valid for initial aerogel
        the rest of components take ref index from CCDB database
        */
        //oriray.set_refind(layer.get(orico).get_index());
        first_intersection.set_nin((float) naero);
        oriray.set_refind(naero);
        raytracks.add(oriray);
        
        RICHRay rayin = new RICHRay(new_pos, oriray.direction().multiply(200));
        lastray = OpticalRotation(rayin, first_intersection);
        lastray.set_refind(geocost.RICH_AIR_INDEX);
        RICHIntersection last_intersection = first_intersection;
        
        int jj = 1;
        int front_nrefl = 0;
        boolean detected = false;
        boolean lost = false;
        while( detected == false && lost == false && raytracks.size()<10){
            
            Point3D last_ori  = lastray.origin();
            Point3D new_hit = null;
            RICHIntersection new_intersection = null;
            
            if(last_intersection.get_layer()<4){
                
                // planar mirrors
                RICHIntersection test_intersection = get_Layer(isec, "MIRROR_BOTTOM").find_Entrance(lastray.asLine3D(), -1);
                if(test_intersection==null)test_intersection = get_Layer(isec, "MIRROR_LEFT_L1").find_Entrance(lastray.asLine3D(), -1);
                if(test_intersection==null)test_intersection = get_Layer(isec, "MIRROR_RIGHT_R1").find_Entrance(lastray.asLine3D(), -1);
                if(test_intersection==null)test_intersection = get_Layer(isec, "MIRROR_LEFT_L2").find_Entrance(lastray.asLine3D(), -1);
                if(test_intersection==null)test_intersection = get_Layer(isec, "MIRROR_RIGHT_R2").find_Entrance(lastray.asLine3D(), -1);
                if(test_intersection!=null){
                    //if(test_intersection.get_pos().distance(last_ori)>RICHConstants.PHOTON_DISTMIN_TRACING)new_intersection = test_intersection;
                    new_intersection = test_intersection;
                }
                
                // shperical mirrors
                if(lastray.direction().costheta()>0){
                    test_intersection = get_Layer(isec, "MIRROR_SPHERE").find_EntranceCurved(lastray.asLine3D(), -1);
                    
                    if(test_intersection!=null){
                        //if(test_intersection.get_pos().distance(last_ori)>RICHConstants.PHOTON_DISTMIN_TRACING){
                        if(new_intersection==null || (new_intersection!=null && test_intersection.get_pos().z()<new_intersection.get_pos().z())) {
                            new_intersection = test_intersection;
                        }
                    }
                    
                    RICHIntersection pmt_inter = get_Layer(isec, "MAPMT").find_Entrance(lastray.asLine3D(), -1);
                    if(pmt_inter!=null) {
                        Point3D test_hit = pmt_inter.get_pos();
                        //if(test_hit.distance(last_ori)>RICHConstants.PHOTON_DISTMIN_TRACING){
                        new_hit=test_hit;
                    }
                }else{
                    test_intersection = get_Layer(isec, "MIRROR_FRONT_B1").find_Entrance(lastray.asLine3D(), -1);
                    if(test_intersection==null)test_intersection = get_Layer(isec, "MIRROR_FRONT_B2").find_Entrance(lastray.asLine3D(), -1);
                    if(test_intersection!=null){
                        //if(test_intersection.get_pos().distance(last_ori)>RICHConstants.PHOTON_DISTMIN_TRACING)new_intersection = test_intersection;
                        new_intersection = test_intersection;
                        front_nrefl++;
                    }
                }
            }
            
            if(new_hit!=null){
                if(new_intersection==null || new_hit.distance(last_ori) <= new_intersection.get_pos().distance(last_ori)) {
                    detected=true;
                }
            }
            if(front_nrefl>richpar.RAY_NFRONT_REFLE){
                lost = true;
                new_hit=new_intersection.get_pos();
            }
            if(new_hit==null && new_intersection==null){
                lost = true;
                Point3D point = new Point3D(0.0, 0.0, 0.0);;
                new_hit = new Point3D(lastray.end());
                Plane3D plane = richgeo.toTriangle3D(get_Layer(isec, "MAPMT").get_Face(0)).plane();
                if(plane.intersection(lastray.asLine3D(), point)==1){
                    double vers = lastray.direction().costheta();
                    double Delta_z = point.z()-lastray.origin().z();
                    if(Delta_z*vers>0){
                        new_hit=point;
                    }
                }
            }
            
            if(lost || detected){
                
                RICHRay newray = new RICHRay(last_ori, new_hit);
                newray.set_type(lastray.get_type());
                newray.set_refind((float) geocost.RICH_AIR_INDEX);
                if(detected)newray.set_detected();
                raytracks.add(newray);
                
            }else{
                
                RICHRay newray = new RICHRay(last_ori, new_intersection.get_pos());
                newray.set_refind(new_intersection.get_nin());
                newray.set_type(lastray.get_type());
                raytracks.add(newray);
                
                // new ray starting at intersection, to be rotated
                rayin = new RICHRay(new_intersection.get_pos(), newray.direction().multiply(200));
                lastray = OpticalRotation(rayin, new_intersection);
                lastray.set_refind(new_intersection.get_nout());
                
            }
            jj++;
            
        }
        
        return raytracks;
    }
    
    
    public void find_EtaC_raytrace_migrad(RICHParticle hadron, RICHParticle photon) {
        
        double n_a = geocost.RICH_AIR_INDEX;
        
        double Phi_ini = photon.trial_pho.lab_phi;
        double Theta_ini = photon.trial_pho.lab_theta;
        if(Phi_ini!=0 && Theta_ini!=0) {
            
            double Theta_P = hadron.lab_theta;
            double Phi_P = hadron.lab_phi;
            double start_EtaC=Math.sin(Theta_P)* Math.sin(Theta_ini)*Math.cos(Phi_ini-Phi_P)+Math.cos(Theta_P)*Math.cos(Theta_ini);
            
            // Minimizing function
            FCNBase myFunction = new FCNBase() {
                public double valueOf(double[] par) {
                    
                    double theta = par[0];
                    double phi = par[1];
                    Vector3D vpho = new Vector3D( Math.sin(theta)*Math.cos(phi), Math.sin(theta)*Math.sin(phi), Math.cos(theta));
                    ArrayList<RICHRay> rays = RayTrace(photon, vpho);
                    double Function = 999;
                    Point3D pmt_hit = new Point3D(0.0, 0.0, 0.0);
                    if(rays!=null){
                        pmt_hit = rays.get(rays.size()-1).end();
                        Function = pmt_hit.distance(photon.get_HitPos());
                    }
                    
                    return Function;
                }
            };
            
            MnUserParameters myParameters = new MnUserParameters();
            myParameters.add("Theta",Theta_ini, 0.01);
            myParameters.add("Phi",Phi_ini, 0.01);
            MnMigrad migrad = new MnMigrad(myFunction, myParameters);
            FunctionMinimum min = migrad.minimize();
            double Theta_Min = min.userParameters().value(0);
            double Phi_Min = min.userParameters().value(1);
            
            int CLASpid = photon.get_CLASpid();
            double n_tile = 1/(hadron.get_beta(CLASpid)*(Math.sin(Theta_P)* Math.sin(Theta_Min)*Math.cos(Phi_Min-Phi_P)+Math.cos(Theta_P)*Math.cos(Theta_Min)));
            double arg = Math.pow(n_a, 2)-Math.pow(n_tile, 2)*Math.pow(Math.sin(Theta_Min), 2);
            double Denominator = 1e-4;
            if(arg>0) Denominator = Math.sqrt(arg);
            
            double Cos_EtaC=Math.sin(Theta_P)* Math.sin(Theta_Min)*Math.cos(Phi_Min-Phi_P)+Math.cos(Theta_P)*Math.cos(Theta_Min);
            
            photon.traced.set_theta((float) Theta_Min);
            photon.traced.set_phi((float) Phi_Min);
            photon.traced.set_aeron((float) n_tile);
            photon.traced.set_EtaC((float) Math.acos(Cos_EtaC));
            
            Vector3D vmin = new Vector3D( Math.sin(Theta_Min)*Math.cos(Phi_Min), Math.sin(Theta_Min)*Math.sin(Phi_Min), Math.cos(Theta_Min));
            ArrayList<RICHRay> raysmin = RayTrace(photon, vmin);
            if(raysmin!=null)photon.traced.set_raytracks(raysmin);
        }
    }
    
    
    public void find_EtaC_raytrace_steps(RICHParticle hadron, RICHParticle photon, int hypo) {
        
        if(hypo<0 || hypo>=RICHConstants.N_HYPO ) return;
        double n_a = geocost.RICH_AIR_INDEX;
        int hypo_pid = RICHConstants.HYPO_LUND[hypo];
        
        if(photon.trial_pho==null){
            return;
        }
        
        double Theta_P = hadron.lab_theta;
        double Phi_P = hadron.lab_phi;
        
        // taking starting point (from most closed throws)
        double phi_min    = photon.trial_pho.lab_phi;
        double the_min    = photon.trial_pho.lab_theta;
        double dphi_min   = 0;
        double dthe_min   = 0;
        Point3D pmt_min  = photon.trial_pho.get_HitPos();
        int nrefle_min = photon.trial_pho.traced.get_nrefle();
        ArrayList<RICHRay> rays_min = new ArrayList();
        rays_min = photon.trial_pho.traced.get_raytracks();
        
        Vector3D vec_dist = photon.get_HitPos().vectorFrom(pmt_min);
        // ATT: this takes the projection on the z plane. Equivalent but unnecessary.
        double dist = Math.sqrt(vec_dist.x()*vec_dist.x()+vec_dist.y()*vec_dist.y());
        double Cos_EtaC = Math.sin(Theta_P)* Math.sin(the_min)*Math.cos(phi_min-Phi_P)+Math.cos(Theta_P)*Math.cos(the_min);
        double EtaCmin = 0.0;
        if(Math.abs(Cos_EtaC)<1.)EtaCmin = Math.acos(Cos_EtaC);
        
        int ntrials = 0;
        while (dist > photon.nominal_sChAngle()*RICHConstants.GAP_NOMINAL_SIZE*richpar.RAYTRACE_RESO_FRAC && ntrials<richpar.RAYTRACE_MAX_NSTEPS){
            
            double dthe = 0.0;
            double dphi = 0.0;
            
            for (int nthe=1; nthe<=4; nthe++){
                double theta_dthe = the_min + photon.nominal_sChAngle()/nthe;
                Vector3D vpho_dthe = new Vector3D( Math.sin(theta_dthe)*Math.cos(phi_min), Math.sin(theta_dthe)*Math.sin(phi_min), Math.cos(theta_dthe));
                double naero = 1/(hadron.get_beta(hypo_pid)*(Math.sin(Theta_P)* Math.sin(theta_dthe)*Math.cos(phi_min-Phi_P)+Math.cos(Theta_P)*Math.cos(theta_dthe)));
                
                ArrayList<RICHRay> rays_dthe = RayTrace(photon, vpho_dthe, naero);
                if(rays_dthe!=null && rays_dthe.get(rays_dthe.size()-1).is_detected()){
                    int nrefle_dthe = get_Nrefle(rays_dthe);
                    if(nrefle_dthe==nrefle_min){
                        Point3D pmt_dthe = rays_dthe.get(rays_dthe.size()-1).end();
                        Vector3D vers_dthe = pmt_dthe.vectorFrom(pmt_min);
                        // shift corresponding to an angular sigma
                        dthe_min = pmt_dthe.distance(pmt_min)*nthe;
                        // theta step for minimization
                        dthe = (vec_dist.x()*vers_dthe.x() + vec_dist.y()*vers_dthe.y()) / (vers_dthe.x()*vers_dthe.x() + vers_dthe.y()*vers_dthe.y()) * photon.nominal_sChAngle();
                        break;
                    }else{
                    }
                }
            }
            
            for (int nphi=1; nphi<=4; nphi++){
                double phi_dphi = phi_min + photon.nominal_sChAngle()/nphi;
                Vector3D vpho_dphi = new Vector3D( Math.sin(the_min)*Math.cos(phi_dphi), Math.sin(the_min)*Math.sin(phi_dphi), Math.cos(the_min));
                double naero = 1/(hadron.get_beta(hypo_pid)*(Math.sin(Theta_P)* Math.sin(the_min)*Math.cos(phi_dphi-Phi_P)+Math.cos(Theta_P)*Math.cos(the_min)));
                
                ArrayList<RICHRay> rays_dphi = RayTrace(photon, vpho_dphi, naero);
                if(rays_dphi!=null && rays_dphi.get(rays_dphi.size()-1).is_detected()){
                    int nrefle_dphi = get_Nrefle(rays_dphi);
                    if(nrefle_dphi==nrefle_min){
                        Point3D pmt_dphi = rays_dphi.get(rays_dphi.size()-1).end();
                        Vector3D vers_dphi = (pmt_dphi.vectorFrom(pmt_min));
                        // shift corresponding to an angular sigma
                        dphi_min = pmt_dphi.distance(pmt_min)*nphi;
                        // phi step for minimization
                        dphi = (vec_dist.x()*vers_dphi.x() + vec_dist.y()*vers_dphi.y()) / (vers_dphi.x()*vers_dphi.x() + vers_dphi.y()*vers_dphi.y()) * photon.nominal_sChAngle();
                        break;
                    }else{
                    }
                }
            }
            
            if(dthe!=0 && dphi!=0){
                int found = 0;
                for (int nn=1; nn<=4; nn++){
                    double the_new  = the_min + dthe/nn;
                    double phi_new  = phi_min + dphi/nn;
                    
                    Vector3D vpho_min = new Vector3D( Math.sin(the_new)*Math.cos(phi_new), Math.sin(the_new)*Math.sin(phi_new), Math.cos(the_new));
                    double naero = 1/(hadron.get_beta(hypo_pid)*(Math.sin(Theta_P)* Math.sin(the_new)*Math.cos(phi_new-Phi_P)+Math.cos(Theta_P)*Math.cos(the_new)));
                    
                    rays_min = RayTrace(photon, vpho_min, naero);
                    if(rays_min!=null && rays_min.get(rays_min.size()-1).is_detected()){
                        int nrefle_new = get_Nrefle(rays_min);
                        if(nrefle_new==nrefle_min){
                            the_min = the_new;
                            phi_min = phi_new;
                            pmt_min = rays_min.get(rays_min.size()-1).end();
                            vec_dist = photon.get_HitPos().vectorFrom(pmt_min);
                            dist = Math.sqrt(vec_dist.x()*vec_dist.x()+vec_dist.y()*vec_dist.y());
                            Cos_EtaC = Math.sin(Theta_P)* Math.sin(the_min)*Math.cos(phi_min-Phi_P)+Math.cos(Theta_P)*Math.cos(the_min);
                            EtaCmin = 0.0;
                            if(Math.abs(Cos_EtaC)<1.)EtaCmin = Math.acos(Cos_EtaC);
                            found = 1;
                            break;
                        }
                    }
                }
                if(found==0){
                    return;
                }
            }else{
                return;
            }
            
            ntrials++;
        }
        
        if(dist < photon.nominal_sChAngle()*RICHConstants.GAP_NOMINAL_SIZE){
            int CLASpid = photon.get_CLASpid();
            double n_tile = 1/(hadron.get_beta(hypo_pid)*(Math.sin(Theta_P)* Math.sin(the_min)*Math.cos(phi_min-Phi_P)+Math.cos(Theta_P)*Math.cos(the_min)));
            photon.traced.set_theta((float) the_min);
            photon.traced.set_phi((float) phi_min);
            photon.traced.set_dthe_res((float) dthe_min);
            photon.traced.set_dphi_res((float) dphi_min);
            photon.traced.set_dthe_bin((float) photon.trial_pho.traced.get_dthe_bin());
            photon.traced.set_dphi_bin((float) photon.trial_pho.traced.get_dphi_bin());
            photon.traced.set_aeron((float) n_tile);
            photon.traced.set_EtaC((float) EtaCmin);
            photon.traced.set_raytracks(rays_min);
        }
        
    }
    
    
    public double find_dthe_steps(RICHParticle photon) {
        
        double pho_phi = photon.traced.get_phi();
        Point3D pho_hit = photon.traced.get_hit();
        int pho_nrefle = photon.traced.get_Nrefle();
        
        double dthe_res = RICHConstants.TRACE_NOMINAL_DTHE;
        for (int nthe=1; nthe<=4; nthe++){
            double theta_dthe = photon.traced.get_theta() + photon.nominal_sChAngle()/nthe;
            Vector3D vpho_dthe = new Vector3D( Math.sin(theta_dthe)*Math.cos(pho_phi), Math.sin(theta_dthe)*Math.sin(pho_phi), Math.cos(theta_dthe));
            ArrayList<RICHRay> rays_dthe = RayTrace(photon, vpho_dthe);
            if(rays_dthe!=null){
                int nrefle_dthe = get_Nrefle(rays_dthe);
                if(nrefle_dthe==pho_nrefle){
                    Point3D pmt_dthe = rays_dthe.get(rays_dthe.size()-1).end();
                    dthe_res = pmt_dthe.distance(pho_hit)*nthe;
                    break;
                }
            }
        }
        
        return dthe_res;
        
    }
    
    
    public double find_dphi_steps(RICHParticle photon) {
        
        double pho_the = photon.traced.get_theta();
        Point3D pho_hit = photon.traced.get_hit();
        int pho_nrefle = photon.traced.get_Nrefle();
        
        double dphi_res = RICHConstants.TRACE_NOMINAL_DPHI;
        for (int nphi=1; nphi<=4; nphi++){
            double phi_dphi = photon.traced.get_phi() + photon.nominal_sChAngle()/nphi;
            Vector3D vpho_dphi = new Vector3D( Math.sin(pho_the)*Math.cos(phi_dphi), Math.sin(pho_the)*Math.sin(phi_dphi), Math.cos(pho_the));
            ArrayList<RICHRay> rays_dphi = RayTrace(photon, vpho_dphi);
            if(rays_dphi!=null){
                int nrefle_dphi = get_Nrefle(rays_dphi);
                if(nrefle_dphi==pho_nrefle){
                    Point3D pmt_dphi = rays_dphi.get(rays_dphi.size()-1).end();
                    dphi_res = pmt_dphi.distance(pho_hit)*nphi;
                    break;
                }
            }
        }
        
        return dphi_res;
        
    }
    
    
    public void find_EtaC_analytic_migrad (RICHParticle hadron, RICHParticle photon) {
        
        double n_a = geocost.RICH_AIR_INDEX;
        
        // The following definition should be read by the geometry
        // ATT: mismatch con la definizione di emission a 3/4 dell'aerogel
        // ATT: L deve essere calcolato con il coseno
        double T_r = geocost.AERO_REF_THICKNESS*geocost.CM;
        double L = T_r/2.; // middle point is Thickness
        double T_g = hadron.ref_impact.z()-hadron.ref_emission.z()-L;
        
        Vector3D vec_b = photon.ref_impact.vectorFrom(hadron.ref_proj);
        double radius = vec_b.mag();
        if(radius >=-1 ) {
            
            // Starting values
            double Phi = photon.ref_phi;
            double Theta_ini = photon.ref_theta;
            double Theta_P = hadron.ref_theta;
            double Phi_P = hadron.ref_phi;
            
            // Minimizing function
            int CLASpid = photon.get_CLASpid();
            FCNBase myFunction = new FCNBase() {
                public double valueOf(double[] par) {
                    double Theta = par[0];
                    double nn_tile = 1/(hadron.get_beta(CLASpid)*(Math.sin(Theta_P)* Math.sin(Theta)*Math.cos(Phi-Phi_P)+Math.cos(Theta_P)*Math.cos(Theta)));
                    double n_tile = nn_tile;
                    //double n_tile = 1.05;
                    double arg = Math.pow(n_a, 2)-Math.pow(n_tile, 2)*Math.pow(Math.sin(Theta), 2);
                    double Denominator = 1e-4;
                    if(arg>0) Denominator = Math.sqrt(arg);
                    
                    double Fun = (T_r -L) * Math.tan(Theta)+T_g* (n_tile * Math.sin(Theta))/Denominator;
                    double Function = Math.pow(radius - Fun, 2);
                    return Function;
                }
            };
            
            MnUserParameters myParameters = new MnUserParameters();
            myParameters.add("Theta",Theta_ini, 0.01);
            MnMigrad migrad = new MnMigrad(myFunction, myParameters);
            FunctionMinimum min = migrad.minimize();
            double Theta_Min = min.userParameters().value(0);
            photon.analytic.set_theta((float) Theta_Min);
            photon.analytic.set_phi((float) Phi);
            
            double Cos_EtaC = Math.sin(Theta_P)* Math.sin(Theta_Min)*Math.cos(Phi-Phi_P)+Math.cos(Theta_P)*Math.cos(Theta_Min);
            
            double n_tile = 1/(hadron.get_beta(CLASpid)*Cos_EtaC);
            double arg = Math.pow(n_a, 2)-Math.pow(n_tile, 2)*Math.pow(Math.sin(Theta_Min), 2);
            double Denominator = 1e-4;
            if(arg>0) Denominator = Math.sqrt(arg);
            
            double migrad_path = ((T_r -L)/Math.cos(Theta_Min) + (T_g*n_a/Denominator) );
            double migrad_time = ( ((T_r -L)/Math.cos(Theta_Min)/n_tile) + (T_g*n_a/Denominator) )/PhysicsConstants.speedOfLight();
            
            photon.analytic.set_time((float) migrad_time);
            photon.analytic.set_path((float) migrad_path);
            photon.analytic.set_aeron((float) n_tile);
            photon.analytic.set_EtaC((float) Math.acos(Cos_EtaC) );
            
        }
    }


    public Point3D find_IntersectionSpheMirror(int isec, Line3D ray){
        return richgeo.find_IntersectionSpheMirror(isec, ray);
    }


    public Point3D find_IntersectionMAPMT(int isec, Line3D ray){
        return richgeo.find_IntersectionMAPMT(isec, ray);
    }
   

    public int get_Nrefle(ArrayList<RICHRay> rays) {
        int nrfl=0;
        for (RICHRay ray : rays) {
            int refe = (int) ray.get_type()/10000;
            if(refe == 1) nrfl++;
        }
        return nrfl;
    }
    
    
    public void dump_raytrack(String head, ArrayList<RICHRay> raytracks) {
        int ii=0;
        for(RICHRay ray: raytracks){
            if(head!=null){
                System.out.format("%s",head);
            }
            System.out.format(" %8d %8d %8d ",ii,get_RefleLayers(raytracks),get_RefleCompos(raytracks));
            ray.dumpRay();
            ii++;
        }
    }
    
    
    public int get_RefleLayers(ArrayList<RICHRay> raytracks) {
        int relay = 0 ;
        if(raytracks.size()<=2) return relay;
        for(int i=2; i<raytracks.size(); i++){
            double off = Math.pow(10,i-2);
            int ilay = (int) ( raytracks.get(i).get_type() - 10000)/100;
            if (ilay==11){
                relay += off*2;
            }else{
                relay += off*1;
            }
        }
        return relay;
    }
    
    public int get_RefleCompos(ArrayList<RICHRay> raytracks) {
        int recompo = 0 ;
        if(raytracks.size()<=2) return recompo;
        for(int i=2; i<raytracks.size(); i++){
            double off = Math.pow(10,i-2);
            int ilay = (int) ( raytracks.get(i).get_type() - 10000)/100;
            int icompo = 0;
            if (ilay==11){
                icompo = (int) ( raytracks.get(i).get_type() - 10000 - ilay*100 - 1);
            }else{
                icompo = (int) ilay;
            }
            recompo += off*icompo;
        }
        return recompo;
    }
    
}
