package org.jlab.rec.rich;

import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.clas.detector.DetectorResponse;

public class RICHio {
    
    public RICHio() {}
    
    public void write_PMTBanks(DataEvent event, RICHEvent richevent) {
        if(richevent.get_nHit()>0)write_HitBank(event, richevent);
        if(richevent.get_nClu()>0)write_ClusterBank(event, richevent);
        //ATT: pass2
        //if(richevent.get_nHit()>0 || richevent.get_nClu()>0)
        //  write_SignalBank(event, richevent);
    }
    
    
    public void write_RECBank(DataEvent event, RICHEvent richevent, RICHParameters richpar) {
        if(richevent.get_nMatch() > 0){
            String richBank = "RICH::Response";
            DataBank bankRich = get_ResponseBank(richevent.get_Matches(), event, richBank, richpar);
            if(bankRich!=null)event.appendBanks(bankRich);
        }
    }
    
    
    public void write_CherenkovBanks(DataEvent event, RICHEvent richevent, RICHParameters richpar) {
        if(richevent.get_nHad()>0)write_HadronBank(event, richevent);
        if(richevent.get_nPho()>0)write_PhotonBank(event, richevent, richpar);
        if(richevent.get_nPho()>0)write_RingBank(event, richevent, richpar);
        if(richevent.get_nHad()>0)write_ParticleBank(event, richevent, richpar);
    }
    
    
    private void write_HitBank(DataEvent event, RICHEvent richevent) {
        
        int NHIT = richevent.get_nHit();
        
        if(NHIT>0) {
            DataBank bankHits = event.createBank("RICH::Hit", NHIT);
            if(bankHits==null){
                System.out.println("ERROR CREATING BANK : RICH::Hit");
                return;
            }
            
            for(int i = 0; i < NHIT; i++){
                
                RICHHit hit = richevent.get_Hit(i);
                
                bankHits.setShort("id",      i, (short) hit.get_id());
                bankHits.setShort("sector",  i, (short) hit.get_sector());
                bankHits.setShort("tile",    i, (short) hit.get_tile());
                bankHits.setShort("pmt",     i, (short) hit.get_pmt());
                bankHits.setShort("anode",   i, (short) hit.get_anode());
                bankHits.setFloat("x",       i, (float) hit.get_x());
                bankHits.setFloat("y",       i, (float) hit.get_y());
                bankHits.setFloat("z",       i, (float) hit.get_z());
                bankHits.setFloat("time",    i, (float) hit.get_Time());
                bankHits.setFloat("rawtime", i, (float) hit.get_rawtime());
                bankHits.setShort("cluster", i, (short) hit.get_cluster());
                bankHits.setShort("xtalk",   i, (short) hit.get_xtalk());
                bankHits.setShort("status",  i, (short) hit.get_status());
                bankHits.setShort("duration",i, (short) hit.get_duration());
            }
            event.appendBanks(bankHits);
        }
        
    }
    
    private void write_ClusterBank(DataEvent event, RICHEvent richevent) {
        
        int NCLU = richevent.get_nClu();
        
        if(NCLU>0) {
            DataBank bankCluster = event.createBank("RICH::Cluster", NCLU);
            if(bankCluster==null){
                System.out.println("ERROR CREATING BANK : RICH::Cluster");
                return;
            }
            
            for(int i = 0; i < NCLU; i++){
                
                RICHCluster clu = richevent.get_Cluster(i);
                
                bankCluster.setShort("id",      i, (short) clu.get_id());
                bankCluster.setShort("size",    i, (short) clu.get_size());
                bankCluster.setShort("sector",  i, (short) clu.get(0).get_sector());
                bankCluster.setShort("tile",    i, (short) clu.get(0).get_tile());
                bankCluster.setShort("pmt",     i, (short) clu.get(0).get_pmt());
                bankCluster.setFloat("charge",  i, (float) clu.get_charge());
                bankCluster.setFloat("time",    i, (float) clu.get_time());
                bankCluster.setFloat("rawtime", i, (float) clu.get_rawtime());
                bankCluster.setFloat("x",       i, (float) clu.get_x());
                bankCluster.setFloat("y",       i, (float) clu.get_y());
                bankCluster.setFloat("z",       i, (float) clu.get_z());
                bankCluster.setFloat("wtime",   i, (float) clu.get_wtime());
                bankCluster.setFloat("wx",      i, (float) clu.get_wx());
                bankCluster.setFloat("wy",      i, (float) clu.get_wy());
                bankCluster.setFloat("wz",      i, (float) clu.get_wz());
            }
            event.appendBanks(bankCluster);
        }
    }
    
    
    private void write_SignalBank(DataEvent event, RICHEvent richevent) {
        
        int NHIT = richevent.get_nHit();
        int NCLU = richevent.get_nClu();
        
        if(NHIT>0 || NCLU>0) {
            
            int nsig = richevent.count_Signals();
            
            DataBank bankSignal = event.createBank("RICH::Signal", nsig);
            if(bankSignal==null){
                System.out.println("ERROR CREATING BANK : RICH::Signal");
                return;
            }
            
            int one = 1;
            int ientry  = 0;
            for(int i=0; i < NHIT; i++){
                
                RICHHit hit = richevent.get_Hit(i);
                
                if(ientry<nsig && hit.get_signal()>0){
                    
                    bankSignal.setShort("id",       ientry, (short) ientry);
                    bankSignal.setShort("hindex",   ientry, (short) hit.get_id());
                    bankSignal.setShort("sector",   ientry, (short) hit.get_sector());
                    bankSignal.setShort("pmt",      ientry, (short) hit.get_pmt());
                    bankSignal.setShort("anode",    ientry, (short) hit.get_anode());
                    bankSignal.setShort("size",     ientry, (short) one);
                    bankSignal.setShort("status",   ientry, (short) hit.get_status());
                    bankSignal.setFloat("x",        ientry, (float) hit.get_x());
                    bankSignal.setFloat("y",        ientry, (float) hit.get_y());
                    bankSignal.setFloat("z",        ientry, (float) hit.get_z());
                    bankSignal.setFloat("time",     ientry, (float) hit.get_Time());
                    bankSignal.setFloat("rawtime",  ientry, (float) hit.get_rawtime());
                    bankSignal.setFloat("charge"  , ientry, (float) hit.get_duration());
                    
                    ientry++;
                }

            }
            
            for(int j=0; j < NCLU; j++){
                
                RICHCluster clu = richevent.get_Cluster(j);
                
                if(ientry<nsig && clu.get_signal()>0){
                    
                    int imax = clu.get_iMax();
                    bankSignal.setShort("id",      ientry, (short) ientry);
                    bankSignal.setShort("hindex",  ientry, (short) clu.get_id());
                    bankSignal.setShort("sector",  ientry, (short) clu.get(imax).get_sector());
                    bankSignal.setShort("pmt",     ientry, (short) clu.get(imax).get_pmt());
                    bankSignal.setShort("anode",   ientry, (short) clu.get(imax).get_anode());
                    bankSignal.setShort("size",    ientry, (short) clu.get_size());
                    bankSignal.setShort("status",  ientry, (short) clu.get(imax).get_status());
                    bankSignal.setFloat("x",       ientry, (float) clu.get_x());
                    bankSignal.setFloat("y",       ientry, (float) clu.get_y());
                    bankSignal.setFloat("z",       ientry, (float) clu.get_z());
                    bankSignal.setFloat("time",    ientry, (float) clu.get_time());
                    bankSignal.setFloat("rawtime", ientry, (float) clu.get_rawtime());
                    bankSignal.setFloat("charge",  ientry, (float) clu.get_charge());
                    
                    ientry++;
                }
                
            }
            event.appendBanks(bankSignal);
        }
    }
    
    
    public void write_HadronBank(DataEvent event, RICHEvent richevent) {
        
        int NHAD = richevent.get_nHad();
        
        if(NHAD>0) {
            DataBank bankHads = event.createBank("RICH::Hadron", NHAD);
            if(bankHads==null){
                System.out.println("ERROR CREATING BANK : RICH::Hadron");
                return;
            }
            
            for(int i = 0; i < NHAD; i++){
                
                RICHParticle had = richevent.get_Hadron(i);
                
                bankHads.setShort("id",            i, (short) had.get_id());
                bankHads.setShort("hindex",        i, (short) had.get_HitIndex());
                bankHads.setByte("pindex",         i, (byte)  had.get_ParentIndex());
                bankHads.setByte("sector",         i, (byte)  had.get_sector());
                
                bankHads.setFloat("traced_the",    i, (float) had.lab_theta);
                bankHads.setFloat("traced_phi",    i, (float) had.lab_phi);
                bankHads.setFloat("traced_hitx",   i, (float) had.get_HitPos().x());
                bankHads.setFloat("traced_hity",   i, (float) had.get_HitPos().y());
                bankHads.setFloat("traced_hitz",   i, (float) had.get_HitPos().z());
                bankHads.setFloat("traced_time",   i, (float) had.traced.get_time());
                bankHads.setFloat("traced_path",   i, (float) had.traced.get_path());
                bankHads.setFloat("traced_mchi2",  i, (float) had.traced.get_machi2());
                
                bankHads.setShort("traced_ilay",   i, (short) had.ilay_emission);
                bankHads.setShort("traced_ico",    i, (short) had.ico_emission);
                bankHads.setFloat("traced_emix",   i, (float) had.lab_emission.x());
                bankHads.setFloat("traced_emiy",   i, (float) had.lab_emission.y());
                bankHads.setFloat("traced_emiz",   i, (float) had.lab_emission.z());
                
                bankHads.setFloat("etaC_dir",      i, (float) had.changle(11,0));
                bankHads.setFloat("etaC_lat",      i, (float) had.changle(11,1));
                bankHads.setFloat("etaC_sphe",     i, (float) had.changle(11,2));
                bankHads.setFloat("etaC_rms",      i, (float) had.schangle(0));
                
            }
            event.appendBanks(bankHads);
        }
        
    }
    
    
    public void write_ParticleBank(DataEvent event, RICHEvent richevent, RICHParameters richpar) {
        
        int NHAD = richevent.get_nHad();
        
        if(NHAD>0) {
            DataBank bankPart = event.createBank("RICH::Particle", NHAD);
            if(bankPart==null){
                System.out.println("ERROR CREATING BANK : RICH::Particle");
                return;
            }
            
            for(int i = 0; i < NHAD; i++){
                
                RICHParticle had = richevent.get_Hadron(i);
                if(had.get_id()>255)continue;
                if(had.get_HitIndex()>255)continue;
                if(had.get_ParentIndex()>255)continue;
                if(had.ilay_emission>255)continue;
                if(had.ico_emission>255)continue;
                if(had.ico_entrance>255)continue;
                
                bankPart.setByte("id",             i ,(byte) had.get_id());
                bankPart.setShort("hindex",        i, (short) had.get_HitIndex());
                bankPart.setByte("pindex",         i, (byte) had.get_ParentIndex());
                
                bankPart.setByte("emilay",         i, (byte) had.ilay_emission);
                bankPart.setByte("emico",          i, (byte) had.ico_emission);
                bankPart.setByte("enico",          i, (byte) had.ico_entrance);
                bankPart.setShort("emqua",         i, (short) had.iqua_emission);
                bankPart.setFloat("mchi2",         i, (float) had.traced.get_machi2());
                
                bankPart.setShort("best_PID",      i, (short) had.traced.get_BestH(had.charge()));
                bankPart.setFloat("RQ",            i, (float) had.traced.get_RQP());
                bankPart.setFloat("ReQ",           i, (float) had.traced.get_ReQP());
                bankPart.setFloat("el_logl",       i, (float) had.traced.get_ElProb());
                bankPart.setFloat("pi_logl",       i, (float) had.traced.get_PiProb());
                bankPart.setFloat("k_logl",        i, (float) had.traced.get_KProb());
                bankPart.setFloat("pr_logl",       i, (float) had.traced.get_PrProb());
                
                bankPart.setFloat("best_ch",       i, (float) had.traced.get_BestCH());
                bankPart.setFloat("best_c2",       i, (float) had.traced.get_BestC2());
                bankPart.setFloat("best_RL",       i, (float) had.traced.get_BestRL());
                bankPart.setFloat("best_ntot",     i, (float) had.traced.get_BestNpho());
                bankPart.setFloat("best_mass",     i, (float) had.traced.get_BestMass());
            }
            event.appendBanks(bankPart);
        }
        
    }
    
    
    public void write_RingBank(DataEvent event, RICHEvent richevent, RICHParameters richpar) {
        
        int NPHO = richevent.get_nPho();
        
        if(NPHO!=0) {
            
            int Nring = 0;
            
            for(int i = 0; i < NPHO; i++){
                RICHParticle pho = richevent.get_Photon(i);
                RICHParticle had = richevent.get_Hadron( pho.get_ParentIndex() );
                int hypo_pid  = pho.traced.get_hypo(had.charge());
                if(!pho.is_real()) continue;
                if(richpar.RING_ONLY_USED==1 && !pho.traced.is_used())continue;
                if(richpar.RING_ONLY_BEST==1 && hypo_pid!=had.traced.get_BestH(had.charge()))continue;
                if(pho.traced.get_OK()>=110) Nring++;
            }
            
            DataBank bankRing = event.createBank("RICH::Ring", Nring);
            if(bankRing==null){
                System.out.println("ERROR CREATING BANK : RICH::Ring");
                return;
            }
            
            /*if(debugMode>=1)System.out.format(" --> Creating the RICH::reference  Bank for Npho %5d Nref  %5d \n",NPHO,Nring);
            DataBank bankRefe = event.createBank("RICH::reference", Nring);
            if(bankRefe==null){
            System.out.println("ERROR CREATING BANK : RICH::reference");
            return;
            }*/
            
            
            int ientry = 0;
            for(int i = 0; i < NPHO; i++){
                
                RICHParticle pho = richevent.get_Photon(i);
                RICHParticle had = richevent.get_Hadron( pho.get_ParentIndex() );
                int pmt          = pho.get_HitPMT();
                
                double a_time   = pho.get_StartTime() + pho.analytic.get_time();
                double t_time   = pho.get_StartTime() + pho.traced.get_time();
                
                double a_etaC = pho.analytic.get_EtaC();
                double t_etaC = pho.traced.get_EtaC();
                int hypo_pid  = pho.traced.get_hypo(had.charge());
                
                if(!pho.is_real())continue;
                
                // skip no real Cherenkov solution
                boolean reject=false;
                if(richpar.RING_ONLY_USED==1 && !pho.traced.is_used())reject=true;
                if(richpar.RING_ONLY_BEST==1 && hypo_pid!=had.traced.get_BestH(had.charge()))reject=true;
                if(ientry<Nring && pho.traced.get_OK()>=110 && !reject){  //by default, all good reconstructions regardeless of status
                    //double htime  = pho.get_HitTime() + pho.chtime();
                    double htime    = pho.get_HitTime();
                    int use = pho.traced.get_OK();
                    if(use>=1000)use=-1*(use-1000);
                    
                    if(had.get_ParentIndex()<255 && pho.get_HitAnode()<255 && pho.traced.get_nrefle()<255){
                        
                        //bankRing.setShort("id",      ientry, (short) pho.get_id());
                        bankRing.setShort("id",      ientry, (short) ientry);
                        bankRing.setShort("hindex",  ientry, (short) pho.get_HitIndex());
                        bankRing.setByte( "pindex",  ientry, (byte)  had.get_ParentIndex());
                        
                        bankRing.setByte( "sector",  ientry, (byte)  pho.get_HitSector());
                        bankRing.setShort("pmt",     ientry, (short) pho.get_HitPMT());
                        bankRing.setByte( "anode",   ientry, (byte)  pho.get_HitAnode());
                        bankRing.setFloat("dtime",   ientry, (float) (htime-t_time));
                        
                        bankRing.setInt(  "hypo",    ientry, (int) hypo_pid);
                        bankRing.setFloat("etaC",    ientry, (float) t_etaC );
                        
                        double prob = pho.traced.get_ElProb();
                        if(Math.abs(hypo_pid)==211) prob = pho.traced.get_PiProb();
                        if(Math.abs(hypo_pid)==321) prob = pho.traced.get_KProb();
                        if(Math.abs(hypo_pid)==2212) prob = pho.traced.get_PrProb();
                        bankRing.setFloat("prob",   ientry, (float) prob);
                        
                        bankRing.setByte("use",    ientry, (byte) use);
                        bankRing.setInt("layers",   ientry, (int) pho.traced.get_RefleLayers());
                        bankRing.setInt("compos",   ientry, (int) pho.traced.get_RefleCompos());
                        
                        double dangle = pho.traced.get_dphi_pixel()*pho.traced.get_dthe_pixel();
                        bankRing.setFloat("dangle",   ientry, (float) dangle);
                        
                        /*bankRefe.setShort("id",      ientry, (short) pho.get_id());
                        bankRefe.setShort("hindex",  ientry, (short) pho.get_HitIndex());
                        bankRefe.setByte( "pindex",  ientry, (byte)  had.get_ParentIndex());
                        
                        bankRefe.setFloat("path",    ientry, (float) pho.traced.get_path());
                        bankRefe.setFloat("time",    ientry, (float) t_time );
                        bankRefe.setFloat("stime",   ientry, (float) pho.schtime());
                        bankRefe.setFloat("hittime",   ientry, (float) htime);
                        
                        bankRefe.setFloat("eff",     ientry, (float) pho.cheff());
                        bankRefe.setFloat("back",    ientry, (float) pho.chbackgr());
                        
                        bankRefe.setFloat("etac_ref",  ientry, (float) had.changle(hypo_pid,irefle));
                        bankRefe.setFloat("etac_rms",  ientry, (float) had.schangle(irefle));*/
                        
                        ientry++;
                    }
                }
            }
            event.appendBanks(bankRing);
            //event.appendBanks(bankRefe);
        }
    }
    
    
    public void write_PhotonBank(DataEvent event, RICHEvent richevent, RICHParameters richpar) {
        
        int NPHO = richevent.get_nPho();
        
        if(NPHO!=0) {
            
            int Nrow = 0;
            for(int i = 0; i < NPHO; i++){
                RICHParticle pho = richevent.get_Photon(i);
                if((pho.is_real() && richpar.SAVE_PHOTONS==1) || (!pho.is_real() && richpar.SAVE_THROWS==1))Nrow++;
            }
            
            DataBank bankPhos = event.createBank("RICH::Photon", Nrow);
            if(bankPhos==null){
                System.out.println("ERROR CREATING BANK : RICH::Photon");
                return;
            }
            
            int ientry = 0;
            
            for(int i = 0; i < NPHO; i++){
                
                RICHParticle pho = richevent.get_Photon(i);
                RICHParticle had = richevent.get_Hadron( pho.get_ParentIndex() );
                int hypo_pid  = pho.traced.get_hypo(had.charge());
                int use = pho.traced.get_OK();
                if(use>=1000)use=-1*(use-1000);
                
                if((pho.is_real() && richpar.SAVE_PHOTONS==1) || (!pho.is_real() && richpar.SAVE_THROWS==1)){
                    if(ientry<Nrow){
                        
                        double htime = pho.get_StartTime()+pho.traced.get_time();
                        if(pho.is_real())htime = pho.get_HitTime();
                        
                        //bankPhos.setShort("id",             ientry,(short) pho.get_id());
                        bankPhos.setShort("id",             ientry,(short) ientry);
                        bankPhos.setByte( "pindex",         ientry, (byte) had.get_ParentIndex());
                        bankPhos.setShort("hindex",         ientry,(short) pho.get_HitIndex());
                        
                        bankPhos.setShort("type",           ientry,(short) pho.get_type());
                        bankPhos.setByte( "used",           ientry,(byte)  use);
                        bankPhos.setFloat("start_time",     ientry,(float) pho.get_StartTime());
                        
                        bankPhos.setFloat("eff",            ientry,(float) pho.cheff());
                        bankPhos.setFloat("back",           ientry,(float) pho.chbackgr());
                        
                        bankPhos.setInt(  "hypo_pid",       ientry,(int)   hypo_pid);
                        bankPhos.setFloat("traced_stime",   ientry,(float) pho.schtime());
                        
                        int irefle    = pho.traced.get_RefleType();
                        bankPhos.setFloat("etac_rms",       ientry,(float) had.schangle(irefle));
                        bankPhos.setFloat("etac_ref",       ientry,(float) had.changle(hypo_pid,irefle));
                        
                        if(pho.traced.exist()){
                            
                            bankPhos.setFloat("traced_the",     ientry,(float) pho.traced.get_theta());
                            bankPhos.setFloat("traced_phi",     ientry,(float) pho.traced.get_phi());
                            bankPhos.setFloat("traced_hitx",    ientry,(float) pho.traced.get_hit().x());
                            bankPhos.setFloat("traced_hity",    ientry,(float) pho.traced.get_hit().y());
                            bankPhos.setFloat("traced_hitz",    ientry,(float) pho.traced.get_hit().z());
                            bankPhos.setFloat("traced_path",    ientry,(float) pho.traced.get_path());
                            bankPhos.setFloat("traced_time",    ientry,(float) pho.traced.get_time());
                            bankPhos.setShort("traced_nrfl",    ientry,(short) pho.traced.get_nrefle());
                            bankPhos.setShort("traced_nrfr",    ientry,(short) pho.traced.get_nrefra());
                            bankPhos.setShort("traced_1rfl",    ientry,(short) pho.traced.get_FirstRefle());
                            bankPhos.setInt("traced_layers",  ientry,(int) pho.traced.get_RefleLayers());
                            bankPhos.setInt("traced_compos",  ientry,(int) pho.traced.get_RefleCompos());
                            
                            bankPhos.setFloat("traced_etaC",    ientry,(float) pho.traced.get_EtaC());
                            bankPhos.setFloat("traced_aeron",   ientry,(float) pho.traced.get_aeron());
                            bankPhos.setFloat("traced_dthe",    ientry,(float) pho.traced.get_dthe_pixel());
                            bankPhos.setFloat("traced_dphi",    ientry,(float) pho.traced.get_dphi_pixel());
                            
                            double prob = pho.traced.get_ElProb();
                            if(Math.abs(hypo_pid)==211) prob = pho.traced.get_PiProb();
                            if(Math.abs(hypo_pid)==321) prob = pho.traced.get_KProb();
                            if(Math.abs(hypo_pid)==2212) prob = pho.traced.get_PrProb();
                            bankPhos.setFloat("prob",           ientry, (float) prob);
                            
                        }

                        ientry++;
                    }
                }
            }
            
            event.appendBanks(bankPhos);
        }
        
    }
    
    public DataBank get_ResponseBank(List<DetectorResponse> responses, DataEvent event, String bank_name, RICHParameters richpar){
        
        DataBank bank = event.createBank(bank_name, responses.size());
        for(int row = 0; row < responses.size(); row++){
            DetectorResponse r = (DetectorResponse) responses.get(row);
            
            bank.setShort("index", row, (short) r.getHitIndex());
            bank.setShort("pindex", row, (short) r.getAssociation());
            bank.setByte("detector", row, (byte) r.getDescriptor().getType().getDetectorId());
            bank.setByte("sector", row, (byte) r.getDescriptor().getSector());
            bank.setByte("layer", row, (byte) r.getDescriptor().getLayer());
            bank.setFloat("x", row, (float) r.getPosition().x());
            bank.setFloat("y", row, (float) r.getPosition().y());
            bank.setFloat("z", row, (float) r.getPosition().z());
            bank.setFloat("hx", row, (float) r.getMatchedPosition().x());
            bank.setFloat("hy", row, (float) r.getMatchedPosition().y());
            bank.setFloat("hz", row, (float) r.getMatchedPosition().z());
            bank.setFloat("path", row, (float) r.getPath());
            bank.setFloat("time", row, (float) r.getTime());
            bank.setFloat("energy", row, (float) r.getEnergy());
            float chi2 = (float) (2*Math.sqrt(Math.pow(r.getMatchedPosition().x()-r.getPosition().x(),2)+
                Math.pow(r.getMatchedPosition().y()-r.getPosition().y(),2)+
                Math.pow(r.getMatchedPosition().z()-r.getPosition().z(),2))/richpar.RICH_HITMATCH_RMS);
            bank.setFloat("chi2", row, (float) chi2);
        }
        return bank;
    }
    
}
