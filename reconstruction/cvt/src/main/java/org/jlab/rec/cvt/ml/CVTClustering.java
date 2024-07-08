/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.banks.RecoBankWriter;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cluster.ClusterFinder;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.mlanalysis.AIHitSelector;

/**
 *
 * @author ziegler
 */
public class CVTClustering extends CVTInitializer {
    
    public static boolean useMC;
    @Override
    public boolean processDataEvent(DataEvent event) {
        this.initialize(event);
        if(isMC) {
            useMC = true;
            this.processMC(event);
        } else {
            useMC = false;
            this.processData(event);
        }
        return true;
    }
    
    public void processData(DataEvent event) {
        
        List<ArrayList<Hit>>         hits = reco.readHits(event, svtStatus, bmtStatus, bmtTime, 
                                                            bmtStripVoltage, bmtStripThreshold);
        this.reNumberHits(hits);
        List<ArrayList<Cluster>> clusters = reco.findClusters();
        this.reNumberClusters(clusters);
        List<DataBank> banks = new ArrayList<>();
        
        banks.add(RecoBankWriter.fillSVTHitBank(event, hits.get(0), this.getSvtHitBank()));
        banks.add(RecoBankWriter.fillBMTHitBank(event, hits.get(1), this.getBmtHitBank()));
        banks.add(RecoBankWriter.fillSVTClusterBank(event, clusters.get(0), this.getSvtClusterBank()));
        banks.add(RecoBankWriter.fillBMTClusterBank(event, clusters.get(1), this.getBmtClusterBank()));

        //create structure for ml bank
        DataBank aibank = event.createBank("cvtml::clusters", clusters.get(0).size()+clusters.get(1).size());   
        
        ClusterBankIO.fillBank(aibank, clusters.get(0), clusters.get(1));
        
        banks.add(aibank);
        
        event.appendBanks(banks.toArray(new DataBank[0]));
    }
    
    private void processMC(DataEvent event) {
        AIHitSelector aihs = new AIHitSelector();
        Map<Double, List<ArrayList<Hit>>> selectedHits = aihs.getHitsWithBg(event, reco, svtStatus, 
                bmtStatus, bmtTime, bmtStripVoltage, bmtStripThreshold);
        if(selectedHits==null) 
            return ;
        ClusterFinder clf = new ClusterFinder();
        List<Hit> hl1=new ArrayList<>();
        List<Hit> hl2=new ArrayList<>();
        List<Cluster> cl1=new ArrayList<>();
        List<Cluster> cl2=new ArrayList<>();
        for(Double d : selectedHits.keySet()) {
            hl1.addAll(selectedHits.get(d).get(0));
            hl2.addAll(selectedHits.get(d).get(1));
            cl1=clf.findClusters(hl1);
            cl2=clf.findClusters(hl2);
        }
        int cid=0;
        for(Cluster c : cl1) {
            cid++;
            c.setId(cid);
            for(Hit h: c) {
                h.setAssociatedClusterID(cid);
            }
        }
        for(Cluster c : cl2) {
            cid++;
            c.setId(cid);
            for(Hit h: c) {
                h.setAssociatedClusterID(cid); 
            }
        }
        
        List<DataBank> banks = new ArrayList<>();
        banks.add(RecoBankWriter.fillSVTHitBank(event, hl1, this.getSvtHitBank()));
        banks.add(RecoBankWriter.fillBMTHitBank(event, hl2, this.getBmtHitBank()));
        banks.add(RecoBankWriter.fillSVTClusterBank(event, cl1, this.getSvtClusterBank()));
        banks.add(RecoBankWriter.fillBMTClusterBank(event, cl2, this.getBmtClusterBank()));

        //create structure for ml bank
        DataBank aibank = event.createBank("cvtml::clusters", cl1.size()+cl2.size());   
        ClusterBankIO.fillBank(aibank, cl1, cl2);
        
        banks.add(aibank);
        
        int bankSize = 0;
        for(Cluster c :cl1) {
            bankSize+=c.size();
        }
        for(Cluster c :cl2) {
            bankSize+=c.size();
        }

        DataBank haibank = event.createBank("cvtml::hits", bankSize);
        int index =0;
        for(Cluster c :cl1) {
            for(Hit h : c) {
                haibank.setShort("id", index, (short)h.getId());
                haibank.setShort("status", index, (short)h.MCstatus);
                haibank.setShort("truetid", index, (short)h.getTrueAssociatedTrackID());
                haibank.setShort("tid", index, (short)h.getAssociatedTrackID());
                index++;
            }
        }
        for(Cluster c :cl2) {
            for(Hit h : c) {
                haibank.setShort("id", index, (short)h.getId());
                haibank.setShort("status", index, (short)h.MCstatus);
                haibank.setShort("truetid", index, (short)h.getTrueAssociatedTrackID());
                haibank.setShort("tid", index, (short)h.getAssociatedTrackID());
                index++;
            }
        }
        banks.add(haibank);
       
        event.appendBanks(banks.toArray(new DataBank[0]));
    }

    private void reNumberHits(List<ArrayList<Hit>> hits) {
        int id=1;
        for(int i = 0; i<2; i++) {
            for(Hit h : hits.get(i))
                h.setId(id++);
        }
    }
    private void reNumberClusters(List<ArrayList<Cluster>> clusters) {
        int id=1;
        for(int i = 0; i<2; i++) {
            for(Cluster c : clusters.get(i)) {
                c.setId(id++);
                for(Hit h : c) {
                    h.setAssociatedClusterID(c.getId());
                }
            }
        }
    }
}
