package org.jlab.rec.mucal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedTable;


public class MUCALReconstruction {

	
    public int debugMode = 0;

    public MUCALReconstruction() {
    }
	
    public List<MUCALHit> initMUCAL(DataEvent event, ConstantsManager manager, int run) {

        IndexedTable charge2Energy = manager.getConstants(run, "/calibration/ft/ftcal/charge_to_energy");
        IndexedTable timeOffsets   = manager.getConstants(run, "/calibration/ft/ftcal/time_offsets");
        IndexedTable timeWalk      = manager.getConstants(run, "/calibration/ft/ftcal/time_walk");
        IndexedTable cluster       = manager.getConstants(run, "/calibration/ft/ftcal/cluster");
        IndexedTable status        = manager.getConstants(run, "/calibration/ft/ftcal/status");

        if(this.debugMode>=1) System.out.println("\nAnalyzing new event");
               
        List<MUCALHit> allhits = this.readRawHits(event,charge2Energy,timeOffsets,timeWalk,cluster,status);
        
        if(debugMode>=1) {
            System.out.println("Found " + allhits.size() + " hits");
            for(int i = 0; i < allhits.size(); i++) {
                System.out.print(i + "\t");
                allhits.get(i).show();
            }
        }
        return allhits;
    }
    
    public List<MUCALHit> selectHits(List<MUCALHit> allhits, ConstantsManager manager, int run) {

        if(debugMode>=1) System.out.println("\nSelecting hits");
        ArrayList<MUCALHit> hits = new ArrayList<>();
        
        IndexedTable thresholds = manager.getConstants(run, "/calibration/ft/ftcal/thresholds");

        for(int i = 0; i < allhits.size(); i++) 
        {
                if(MUCALHit.passHitSelection(allhits.get(i), thresholds)) {
                        hits.add(allhits.get(i));	
                }
        }	
        Collections.sort(hits);
        if(debugMode>=1) {
            System.out.println("List of selected hits");
            for(int i = 0; i < hits.size(); i++) 
            {	
                System.out.print(i + "\t");
                hits.get(i).show();
            }
        }
        return hits;
    }
			
    public List<MUCALCluster> findClusters(List<MUCALHit> hits, ConstantsManager manager, int run) {

        List<MUCALCluster> clusters = new ArrayList();
        
        IndexedTable   thresholds   = manager.getConstants(run, "/calibration/ft/ftcal/thresholds");
        IndexedTable   clusterTable = manager.getConstants(run, "/calibration/ft/ftcal/cluster");
        
        if(debugMode>=1) System.out.println("\nBuilding clusters");
        for(int ihit=0; ihit<hits.size(); ihit++) {
            MUCALHit hit = hits.get(ihit);
            if(hit.get_ClusID()==0)  {                       // this hit is not yet associated with a cluster
                for(int jclus=0; jclus<clusters.size(); jclus++) {
                    MUCALCluster cluster = clusters.get(jclus);
                    if(cluster.containsHit(hit, thresholds, clusterTable)) {
                        hit.set_ClusID(cluster.getID());     // attaching hit to previous cluster 
                        cluster.add(hit);
                        if(debugMode>=1) System.out.println("Attaching hit " + ihit + " to cluster " + cluster.getID());
                        break;
                    }
                }
            }
            if(hit.get_ClusID()==0)  {                       // new cluster found
                MUCALCluster cluster = new MUCALCluster(clusters.size()+1);
                hit.set_ClusID(cluster.getID());
                cluster.add(hit);
                clusters.add(cluster);
                if(debugMode>=1) System.out.println("Creating new cluster with ID " + cluster.getID());
            }
        }
        return clusters;
    }
       
    public void selectClusters(List<MUCALCluster> clusters, ConstantsManager manager, int run) {

        IndexedTable   clusterTable = manager.getConstants(run, "/calibration/ft/ftcal/cluster");
        
        for(int i=0; i<clusters.size(); i++) {
            clusters.get(i).setStatus(clusterTable);
            if(debugMode>=1) System.out.println("Setting status for cluster " + i + " " + clusters.get(i).toString());
        }
    }
        
    
    public void writeBanks(DataEvent event, List<MUCALHit> hits, List<MUCALCluster> clusters, ConstantsManager manager, int run){
        
        IndexedTable   energyTable = manager.getConstants(run, "/calibration/ft/ftcal/energycorr");
        
        // hits banks
        if(!hits.isEmpty()) {
            DataBank bankHits = event.createBank("MUCAL::hits", hits.size());    
            if(bankHits==null){
                System.out.println("ERROR CREATING BANK : MUCAL::hits");
                return;
            }
            for(int i = 0; i < hits.size(); i++){
                bankHits.setByte("idx",i,(byte) hits.get(i).get_IDX());
                bankHits.setByte("idy",i,(byte) hits.get(i).get_IDY());
                bankHits.setFloat("x",i,(float) (hits.get(i).get_Dx()/10.0));
                bankHits.setFloat("y",i,(float) (hits.get(i).get_Dy()/10.0));
                bankHits.setFloat("z",i,(float) (hits.get(i).get_Dz()/10.0));
                bankHits.setFloat("energy",i,(float) hits.get(i).get_Edep());
                bankHits.setFloat("time",i,(float) hits.get(i).get_Time());
                bankHits.setShort("hitID",i,(short) hits.get(i).get_DGTZIndex());
                if(!clusters.isEmpty() && clusters.get(hits.get(i).get_ClusID()-1).getStatus()) {
                    bankHits.setShort("clusterID",i,(short) hits.get(i).get_ClusID());
                }
                else {
                    bankHits.setShort("clusterID",i,(short) 0);
                }
            }	
            if(debugMode>=1) bankHits.show();
            event.appendBanks(bankHits);
        }
        // cluster bank
        if(!clusters.isEmpty()) {
            List<MUCALCluster> selectedClusters  = new ArrayList();
            for(int i =0; i< clusters.size(); i++) {
                if(clusters.get(i).getStatus()) selectedClusters.add(clusters.get(i));
            }
            if(!selectedClusters.isEmpty()) {                
                DataBank bankCluster = event.createBank("MUCAL::clusters", selectedClusters.size());    
                if(bankCluster==null){
                    System.out.println("ERROR CREATING BANK : MUCAL::clusters");
                    return;
                }
                for(int i = 0; i < selectedClusters.size(); i++){
                    bankCluster.setShort("id", i,(short) selectedClusters.get(i).getID());
                    bankCluster.setShort("size", i,(short) selectedClusters.get(i).getSize());
                    bankCluster.setFloat("x",i,(float) (selectedClusters.get(i).getX()/10.0));
                    bankCluster.setFloat("y",i, (float) (selectedClusters.get(i).getY()/10.0));
                    bankCluster.setFloat("z",i, (float) (selectedClusters.get(i).getZ()/10.0));
                    bankCluster.setFloat("widthX",i, (float) (selectedClusters.get(i).getWidthX()/10.0));
                    bankCluster.setFloat("widthY",i, (float) (selectedClusters.get(i).getWidthY()/10.0));
                    bankCluster.setFloat("radius",i, (float) (selectedClusters.get(i).getRadius()/10.0));
                    bankCluster.setFloat("time",i, (float) selectedClusters.get(i).getTime());
                    bankCluster.setFloat("energy",i, (float) selectedClusters.get(i).getFullEnergy(energyTable));
                    bankCluster.setFloat("recEnergy",i, (float) selectedClusters.get(i).getEnergy());
                    bankCluster.setFloat("maxEnergy",i, (float) selectedClusters.get(i).getSeedEnergy());                   
                }
                if(debugMode>=1) bankCluster.show();
                event.appendBanks(bankCluster);
            }
        }
    }

    public List<MUCALHit> readRawHits(DataEvent event, IndexedTable charge2Energy, IndexedTable timeOffsets, IndexedTable timeWalk, IndexedTable cluster, IndexedTable status) {
        // getting raw data bank
	if(debugMode>=1) System.out.println("Getting raw hits from MUCAL:adc bank");

        List<MUCALHit>  hits = new ArrayList<>();
        if(event.hasBank("MUCAL::adc")==true) {
            RawDataBank bankDGTZ = new RawDataBank("MUCAL::adc");
            bankDGTZ.read(event);
            int nrows = bankDGTZ.rows();
            for(int row = 0; row < nrows; row++){
                int isector     = bankDGTZ.getByte("sector",row);
                int ilayer      = bankDGTZ.getByte("layer",row);
                int icomponent  = bankDGTZ.getShort("component",row);
                int adc         = bankDGTZ.getInt("ADC",row);
                float time      = bankDGTZ.getFloat("time",row);
                if(ilayer==0) ilayer=1; // fix for wrong layer in TT
                if(adc!=-1 && time!=-1 && status.getIntValue("status", isector, ilayer, MUCALHit.REFCOMPONENT)==0){
                    MUCALHit hit = new MUCALHit(bankDGTZ.trueIndex(row),icomponent, adc, time, charge2Energy, timeOffsets, timeWalk, cluster);
                    hits.add(hit);
	        }	          
            }
        }
        return hits;
    }
    
}
