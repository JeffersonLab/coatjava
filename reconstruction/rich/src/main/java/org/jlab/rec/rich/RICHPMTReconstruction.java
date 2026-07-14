package org.jlab.rec.rich;

import java.util.ArrayList;
import java.util.Collections;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.detector.banks.RawDataBank;

import org.jlab.detector.geom.RICH.RICHGeoFactory;

public class RICHPMTReconstruction {
    
    private RICHEvent          richevent;
    private RICHGeoFactory     richgeo;
    private RICHio             richio;
    
    public RICHPMTReconstruction(RICHEvent richeve, RICHGeoFactory richgeo, RICHio richio) {
        this.richevent = richeve;
        this.richgeo = richgeo;
        this.richio = richio;
    }
    
    public void process_RawData(DataEvent event, RICHParameters richpar, RICHCalibration richcal) {
        
        // get edges fron banks
        ArrayList<RICHEdge> allEdges = read_RawBank(event);
        
        // select good edges and order them
        ArrayList<RICHEdge> Leads = selectLeadEdges(allEdges);
        ArrayList<RICHEdge> Trails = selectTrailEdges(allEdges);
        
        // build hits
        ArrayList<RICHHit> Hits = reco_PMTHits(Leads, Trails, richcal);
        ArrayList<RICHCluster> AllClusters = findClusters(Hits);
        ArrayList<RICHCluster> Clusters = selectGoodClusters(AllClusters);
        
        find_XTalk(Hits, AllClusters, richpar);
        
        richevent.add_Hits(Hits);
        richevent.add_Clusters(Clusters);
        richevent.select_Signals();
        
        richio.write_PMTBanks(event, richevent);
    }
    
    public ArrayList<RICHEdge> read_RawBank(DataEvent event) {
        ArrayList<RICHEdge> allEdges = null;
        if(event instanceof HipoDataEvent) {
            allEdges = this.read_RawEdgesHIPO(event);
        }
        return allEdges;
    }
    
    
    public ArrayList<RICHEdge> selectLeadEdges(ArrayList<RICHEdge> allEdges) {
        
        ArrayList<RICHEdge> Leads = new ArrayList<>();
        
        for(int i = 0; i < allEdges.size(); i++) {
            RICHEdge edge = allEdges.get(i);
            if(edge.pass_EdgeSelection()) {
                if(edge.get_polarity()==RICHConstants.LEADING_EDGE_POLARITY)Leads.add(edge);
            }
        }
        
        Collections.sort(Leads);
        // redefine IDs accroding to the sorting
        for(int il=0; il<Leads.size(); il++) Leads.get(il).set_id(il);
        
        return Leads;
    }
    
    
    public ArrayList<RICHEdge> selectTrailEdges(ArrayList<RICHEdge> allEdges) {
        
        ArrayList<RICHEdge> Trails = new ArrayList<>();
        
        for(int i = 0; i < allEdges.size(); i++)
        {
            RICHEdge edge = allEdges.get(i);
            if(edge.pass_EdgeSelection()) {
                if(edge.get_polarity()==RICHConstants.TRAILING_EDGE_POLARITY)Trails.add(edge);
            }
        }
        
        
        Collections.sort(Trails);
        // redefine IDs accroding to the sorting
        for(int it=0; it<Trails.size(); it++) Trails.get(it).set_id(it);
        
        return Trails;
    }
    
    
    public ArrayList<RICHEdge> read_RawEdgesHIPO(DataEvent event) {
        // getting raw data bank
        
        ArrayList<RICHEdge>  edges = new ArrayList<>();
        if(event.hasBank("RICH::tdc")==true) {
            RawDataBank bankDGTZ = new RawDataBank("RICH::tdc");
            bankDGTZ.read(event);
            int nrows = bankDGTZ.rows();
            for(int row = 0; row < nrows; row++){
                int isector     = bankDGTZ.getByte("sector",row);
                int ilayer      = bankDGTZ.getByte("layer",row);
                int icomponent  = bankDGTZ.getShort("component",row);
                int iorder      = bankDGTZ.trueOrder(row);
                int itdc        = bankDGTZ.getInt("TDC",row);
                if(ilayer<0)ilayer=ilayer+256;
                if(itdc!=-1){
                    RICHEdge edge = new RICHEdge(bankDGTZ.trueIndex(row), isector, ilayer, icomponent, iorder, itdc);
                    edges.add(edge);
                }
            }
        }
        return edges;
    }
    
    
    public ArrayList<RICHHit> reco_PMTHits(ArrayList<RICHEdge> Leads, ArrayList<RICHEdge >Trails, RICHCalibration richcal) {
        
        int nhit=0;
        ArrayList<RICHHit> hits = new ArrayList();
        
        for(int iled=0; iled<Leads.size(); iled++) {
            RICHEdge lead = Leads.get(iled);
            
            if(lead.get_hit()>0)continue;
            
            for(int itra=0; itra<Trails.size(); itra++) {
                RICHEdge trail = Trails.get(itra);
                
                if(trail.get_hit()>0)continue;
                if(trail.get_sector() == lead.get_sector() &&
                    trail.get_tile()*192+trail.get_channel() ==  lead.get_tile()*192+lead.get_channel()){
                    if(trail.get_tdc() > lead.get_tdc()){
                        nhit++;
                        lead.set_hit(nhit);
                        trail.set_hit(nhit);
                        RICHHit hit = new RICHHit(nhit, richevent.getFTOFphase(), lead, trail, richgeo, richcal);
                        hits.add(hit);
                        break;
                    }
                }
            }
        }
        
        Collections.sort(hits);
        // redefine the IDs following the sorting
        for(int ih=0; ih<hits.size(); ih++) {
            hits.get(ih).set_id(ih);
            // likely unnecessary
            int ilead = hits.get(ih).get_lead();
            Leads.get(ilead).set_hit(ih);
            int itrail = hits.get(ih).get_trail();
            Trails.get(itrail).set_hit(ih);
        }
        
        return hits;
    }
    
    
    public ArrayList<RICHCluster> findClusters(ArrayList<RICHHit> hits) {
        
        ArrayList<RICHCluster> allclusters = new ArrayList();
        
        for(int ihit=0; ihit<hits.size(); ihit++) {
            RICHHit hit = hits.get(ihit);
            if(hit.get_cluster()==0)  {                       // this hit is not yet associated with a cluster
                
                for(int jclus=0; jclus<allclusters.size(); jclus++) {
                    RICHCluster cluster = allclusters.get(jclus);
                    if(cluster.containsHit(hit)) {
                        hit.set_cluster(cluster.get_id());     // attaching hit to previous cluster
                        cluster.add(hit);
                    }
                }
            }
            
            if(hit.get_cluster()==0)  {                       // new cluster found
                RICHCluster cluster = new RICHCluster(allclusters.size()+1);
                hit.set_cluster(cluster.get_id());
                cluster.add(hit);
                allclusters.add(cluster);
            }
        }
        
        return allclusters;
    }
    
    
    public ArrayList<RICHCluster> selectGoodClusters(ArrayList<RICHCluster> allclusters) {
        
        ArrayList<RICHCluster> clusters = new ArrayList();
        
        int nclu = 0;
        for(int i=0; i<allclusters.size(); i++) {
            if(allclusters.get(i).isgoodCluster()) {
                RICHCluster goodclu = allclusters.get(i);
                int merge = 0 ;
                for (int j=0; j<clusters.size(); j++){
                    if(clusters.get(j).get(0).get_pmt() == goodclu.get(0).get_pmt()){
                        clusters.get(j).merge(goodclu);
                        merge = 1;
                    }
                }
                if(merge==0){
                    nclu++;
                    goodclu.set_id(nclu);
                    clusters.add(goodclu);
                }
            }else{
                // cancel hit to cluster link
                RICHCluster badclu = allclusters.get(i);
                for(int j = 0; j< badclu.size(); j++) {
                    badclu.get(j).set_cluster(0);
                }
            }
        }
        
        return clusters;
    }
    
    
    public void find_XTalk(ArrayList<RICHHit> hits, ArrayList<RICHCluster> allclusters, RICHParameters richpar) {
        for(int ih=0; ih<hits.size(); ih++) {
            RICHHit hiti = hits.get(ih);
            if(hiti.get_cluster()!=0)  continue; // this hit is not yet associated with a cluster
            for(int jh=ih+1; jh<hits.size(); jh++) {
                RICHHit hitj = hits.get(jh);
                if(hiti.get_cluster()!=0)  continue; // this hit is not yet associated with a cluster
                
                if(hiti.get_pmt()==hitj.get_pmt() && hitj.get_duration()*100 < hiti.get_duration()*richpar.GOODHIT_FRAC){
                    for(int k=-1; k<=1; k+=2 ) {
                        if(hiti.get_channel() == (k+hitj.get_channel())) {
                            hitj.set_xtalk(1000+hiti.get_id()+1);
                        }
                    }
                }
            }
        }
        for(int iclu=0; iclu<allclusters.size(); iclu++) {
            if(allclusters.get(iclu).get_size()< RICHConstants.CLUSTER_MIN_SIZE) {
                RICHCluster clu = allclusters.get(iclu);
                for(int ih = 0; ih< clu.size(); ih++) {
                    RICHHit hiti = clu.get(ih);
                    for(int jh = ih+1; jh< clu.size(); jh++) {
                        RICHHit hitj = clu.get(jh);
                        if(hitj.get_duration()*100 < hiti.get_duration()*richpar.GOODHIT_FRAC) {
                            hitj.set_xtalk(hiti.get_id()+1);
                        }
                    }
                }
            }
        }
    }

}
