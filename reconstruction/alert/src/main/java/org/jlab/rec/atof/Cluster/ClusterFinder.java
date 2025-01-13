package org.jlab.rec.atof.Cluster;

import java.util.ArrayList;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.rec.atof.Hit.AtofHit;
import org.jlab.rec.atof.Hit.BarHit;
import org.jlab.rec.atof.Hit.HitFinder;

/**
 *
 * @author npilleux
 */
public class ClusterFinder {

    private ArrayList<AtofCluster> clusters;
    
    public void MakeClusters(HitFinder hitfinder) {

        ArrayList<AtofHit> wedge_hits = hitfinder.getWedgeHits();
        ArrayList<BarHit> bar_hits = hitfinder.getBarHits();
        
        double sigma_Phi = 6.0; //angle opening of a layer. to be read from DB in the future
        double sigma_Z = 2.0;//wedge length. to be read from DB in the future
        double sigma_T = 10;//timing resolution to be read from DB in the future
        
        
        for(int i_wedge = 0; i_wedge<wedge_hits.size(); i_wedge++)
        {
            //Make a cluster for each wedge hit
            AtofHit this_wedge_hit = wedge_hits.get(i_wedge);
            if(this_wedge_hit.getIs_in_a_cluster()) continue;
            if(this_wedge_hit.getEnergy() < 0.5 )continue; //energy threshold

            ArrayList<AtofHit> this_cluster_wedge_hits = new ArrayList<>();
            ArrayList<BarHit> this_cluster_bar_hits = new ArrayList<>();

            this_wedge_hit.setIs_in_a_cluster(true);
            this_cluster_wedge_hits.add(this_wedge_hit);
            
            
            //Check if other wedge hits should be clustered
            for(int j_wedge = 0; j_wedge<wedge_hits.size(); j_wedge++)
                   {
                        if(i_wedge==j_wedge) continue;  
                        AtofHit other_wedge_hit = wedge_hits.get(j_wedge);
                        if(other_wedge_hit.getIs_in_a_cluster()) continue;
                        if(other_wedge_hit.getEnergy() < 0.5 )continue; //energy threshold
                        if(Math.abs(this_wedge_hit.getPhi() - other_wedge_hit.getPhi()) < sigma_Phi)
                        {
                            if(Math.abs(this_wedge_hit.getZ() - other_wedge_hit.getZ()) < sigma_Z)
                            {
                                if(Math.abs(this_wedge_hit.getTime() - other_wedge_hit.getTime()) < sigma_T)
                                {
                                    other_wedge_hit.setIs_in_a_cluster(true);
                                    this_cluster_wedge_hits.add(other_wedge_hit);
                                }   
                            }
                        }
                   }
            
            //Check if bar hits should be clustered
            for(int j_bar = 0; j_bar<bar_hits.size(); j_bar++)
                   {
                        BarHit other_bar_hit = bar_hits.get(j_bar);
                        if(other_bar_hit.getIs_in_a_cluster()) continue;
                        if(other_bar_hit.getEnergy() < 0.5 )continue; //energy threshold
                        if(Math.abs(this_wedge_hit.getPhi() - other_bar_hit.getPhi()) < sigma_Phi)
                        {
                            if(Math.abs(this_wedge_hit.getZ() - other_bar_hit.getZ()) < sigma_Z)
                            {
                                if(Math.abs(this_wedge_hit.getTime() - other_bar_hit.getTime()) < sigma_T)
                                {
                                    other_bar_hit.setIs_in_a_cluster(true);
                                    this_cluster_bar_hits.add(other_bar_hit);
                                }   
                            }
                        }
                   }
            AtofCluster cluster = new AtofCluster(this_cluster_bar_hits, this_cluster_wedge_hits);
            clusters.add(cluster);
            }

        for(int i_bar = 0; i_bar<bar_hits.size(); i_bar++)
        {
            //Now add unmatched bar hits in their own cluster
            BarHit this_bar_hit = bar_hits.get(i_bar);
            if(this_bar_hit.getIs_in_a_cluster()) continue;
            if(this_bar_hit.getEnergy() < 0.5 )continue; //energy threshold
            ArrayList<AtofHit> this_cluster_wedge_hits = new ArrayList<AtofHit>();
            ArrayList<BarHit> this_cluster_bar_hits = new ArrayList<BarHit>();
            this_cluster_bar_hits.add(this_bar_hit);
            AtofCluster cluster = new AtofCluster(this_cluster_bar_hits, this_cluster_wedge_hits);
            clusters.add(cluster);
        }
    }
    
    public ClusterFinder() 
	{
            clusters = new ArrayList<AtofCluster>();
	}
  
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        Detector atof = factory.createDetectorCLAS(cp);
        
        //Input to be read
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/reconstruction/alert/src/main/java/org/jlab/rec/atof/Hit/test_tdc_atof.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);
        
        HitFinder hitfinder = new HitFinder();

        int event_number = 0;
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            event_number++;
            hitfinder.FindHits(event, atof);
            ClusterFinder clusterfinder = new ClusterFinder();
            clusterfinder.MakeClusters(hitfinder);
            
        }
    }
    
}
