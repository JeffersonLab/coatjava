package org.jlab.rec.muhd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.utils.groups.IndexedTable;

public class MUHDReconstruction {


    public int debugMode = 0;

    public MUHDReconstruction() {
    }
	
    public List<MUHDHit> initMUHD(DataEvent event, ConstantsManager manager, int run) {

        IndexedTable charge2Energy = manager.getConstants(run, "/calibration/ft/fthodo/charge_to_energy");
        IndexedTable timeOffsets   = manager.getConstants(run, "/calibration/ft/fthodo/time_offsets");
        IndexedTable status        = manager.getConstants(run, "/calibration/ft/fthodo/status");
        IndexedTable geometry      = manager.getConstants(run, "/geometry/ft/fthodo");
        
        if(debugMode>=1) System.out.println("\nAnalyzing new event");
        List<MUHDHit> allhits = null;
        
        if(event instanceof HipoDataEvent) {
            allhits = this.readRawHits(event,charge2Energy,timeOffsets,status,geometry);
        }
        if(debugMode>=1) {
            System.out.println("Found " + allhits.size() + " hits");
            for(int i = 0; i < allhits.size(); i++) {
                System.out.print(i + "\t");
                allhits.get(i).showHit();
            }
        }
        return allhits;
    }
    
    public List<MUHDHit> selectHits(List<MUHDHit> allhits) {

        if(debugMode>=1) System.out.println("\nSelecting hits");
        ArrayList<MUHDHit> hits = new ArrayList<>();
        
        for(int i = 0; i < allhits.size(); i++) 
        {
                if(MUHDHit.passHitSelection(allhits.get(i))) {
                        hits.add(allhits.get(i));	
                }
        }	
        Collections.sort(hits);
        if(debugMode>=1) {
            System.out.println("List of selected hits");
            for(int i = 0; i < hits.size(); i++) 
            {	
                System.out.print(i + "\t");
                hits.get(i).showHit();
            }
        }
        return hits;
    }

    public List<MUHDCluster> findClusters(List<MUHDHit> hits) {

        List<MUHDCluster> clusters = new ArrayList();
        
        if(debugMode>=1) System.out.println("\nBuilding clusters");
        for(int ihit=0; ihit<hits.size(); ihit++) {
            MUHDHit hit = hits.get(ihit);
            if(hit.get_ClusterIndex()==0)  {                       // this hit is not yet associated with a cluster
                for(int jclus=0; jclus<clusters.size(); jclus++) {
                    MUHDCluster cluster = clusters.get(jclus);
                    if(cluster.containsHit(hit)) {
                        hit.set_ClusterIndex(cluster.getID());     // attaching hit to previous cluster 
                        cluster.add(hit);
                        if(debugMode>=1) System.out.println("Attaching hit " + ihit + " to cluster " + cluster.getID());
                    }
                }
            }
            if(hit.get_ClusterIndex()==0)  {                       // new cluster found
                MUHDCluster cluster = new MUHDCluster(clusters.size()+1);
                hit.set_ClusterIndex(cluster.getID());
                cluster.add(hit);
                clusters.add(cluster);
                if(debugMode>=1) System.out.println("Creating new cluster with ID " + cluster.getID());
            }
        }
        return clusters;
    }
    
    public void writeBanks(DataEvent event, List<MUHDHit> hits, List<MUHDCluster> clusters){
        // hits banks
        if(!hits.isEmpty()) {
            DataBank bankHits = event.createBank("MUHD::hits", hits.size());    
            if(bankHits==null){
                System.out.println("ERROR CREATING BANK : MUHD::hits");
                return;
            }
            for(int i = 0; i < hits.size(); i++){
                bankHits.setByte("sector",i,(byte) hits.get(i).get_Sector());
                bankHits.setByte("layer",i,(byte) hits.get(i).get_Layer());
                bankHits.setShort("component",i,(short) hits.get(i).get_ID());
                bankHits.setFloat("x",i,(float) (hits.get(i).get_Dx()/10.0));
                bankHits.setFloat("y",i,(float) (hits.get(i).get_Dy()/10.0));
                bankHits.setFloat("z",i,(float) (hits.get(i).get_Dz()/10.0));
                bankHits.setFloat("energy",i,(float) hits.get(i).get_Edep());
                bankHits.setFloat("time",i,(float) hits.get(i).get_Time());
                bankHits.setShort("hitID",i,(short) hits.get(i).get_DGTZIndex());
                bankHits.setShort("clusterID",i,(short) hits.get(i).get_ClusterIndex());				
            }
            event.appendBanks(bankHits);
        }
        // cluster bank
        if(!clusters.isEmpty()){
            DataBank bankCluster = event.createBank("MUHD::clusters", clusters.size());    
            if(bankCluster==null){
                System.out.println("ERROR CREATING BANK : MUHD::clusters");
                return;
            }
            for(int i = 0; i < clusters.size(); i++){
                            bankCluster.setShort("id", i,(short) clusters.get(i).getID());
                            bankCluster.setShort("size", i,(short) clusters.get(i).getSize());
                            bankCluster.setFloat("x",i,(float) (clusters.get(i).getX()/10.0));
                            bankCluster.setFloat("y",i,(float) (clusters.get(i).getY()/10.0));
                            bankCluster.setFloat("z",i,(float) (clusters.get(i).getZ()/10.0));
                            bankCluster.setFloat("widthX",i,(float) (clusters.get(i).getWidthX()/10.0));
                            bankCluster.setFloat("widthY",i,(float) (clusters.get(i).getWidthY()/10.0));
                            bankCluster.setFloat("radius",i,(float) (clusters.get(i).getRadius()/10.0));
                            bankCluster.setFloat("time",i,(float) clusters.get(i).getTime());
                            bankCluster.setFloat("energy",i,(float) clusters.get(i).getEnergy());
            }
            event.appendBanks(bankCluster);
        }
    }
    
    public List<MUHDHit> readRawHits(DataEvent event, IndexedTable charge2Energy, IndexedTable timeOffsets, IndexedTable status, IndexedTable geometry) {
        // getting raw data bank
	if(debugMode>=1) System.out.println("Getting raw hits from MUHD:adc bank");

        List<MUHDHit>  hits = new ArrayList<>();
        if(event.hasBank("MUHD::adc")==true) {
            RawDataBank bankDGTZ = new RawDataBank("MUHD::adc");
            bankDGTZ.read(event);
            int nrows = bankDGTZ.rows();
            for(int row = 0; row < nrows; row++){
                int isector     = bankDGTZ.getByte("sector",row);
                int ilayer      = bankDGTZ.getByte("layer",row);
                int icomponent  = bankDGTZ.getShort("component",row);
                int adc         = bankDGTZ.getInt("ADC",row);
                float time      = bankDGTZ.getFloat("time",row);
                if(adc!=-1 && time!=-1 && status.getIntValue("status", isector, ilayer, icomponent)==0){
                    MUHDHit hit = new MUHDHit(bankDGTZ.trueIndex(row),isector,ilayer,icomponent, adc, time, charge2Energy,timeOffsets,geometry);
	             hits.add(hit); 
	        }	          
            }
        }
        return hits;
    }
}
