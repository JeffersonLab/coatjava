package org.jlab.service.dc;

import java.util.List;
import java.util.ArrayList;
import org.jlab.clas.swimtools.Swim;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.banks.HitReader;
import org.jlab.rec.dc.banks.RecoBankWriter;
import org.jlab.rec.dc.cluster.ClusterCleanerUtilities;
import org.jlab.rec.dc.cluster.ClusterFinder;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.rec.urwell.reader.URWellCross;
import org.jlab.rec.urwell.reader.URWellReader;
/**
 * @author tongtong
 * 
 */
public class DCURWellHBClustering extends DCEngine {
    //some identifier for the type of clustering,
    //ability to plug in more than once

    public DCURWellHBClustering() {
        super("DCCR");
    }
    
    @Override
    public void setDropBanks() {        
        super.registerOutputBank(this.getBanks().getHitsBank());
        super.registerOutputBank(this.getBanks().getClustersBank());
        super.registerOutputBank(this.getBanks().getURWellDCClustersBank());
    }
     
    
    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        int run = this.getRun(event);
        if(run==0) return true;
        
        /* 1 */
        // get Field
        Swim dcSwim = new Swim();
        /* 2 */
        ClusterFitter cf = new ClusterFitter();
        /* 3 */
        ClusterCleanerUtilities ct = new ClusterCleanerUtilities();
        /* 4 */
        RecoBankWriter rbc = new RecoBankWriter(this.getBanks());
        /* 5 */
        HitReader hitRead = new HitReader(this.getBanks(), this.getRawBankOrders(), super.getConstantsManager(), Constants.getInstance().dcDetector);
        /* 6 */
        hitRead.fetch_DCHits(event);
        /* 7 */
        //I) get the hits
        List<Hit> hits = hitRead.get_DCHits(Constants.getInstance().SECTORSELECT);
        //II) process the hits
        //1) exit if hit list is empty
        if (hits.isEmpty()) {
            return true;
        }
        URWellReader uRWellReader = new URWellReader(event, "HB");
        List<URWellCross> urCrosses = uRWellReader.getUrwellCrosses();
        /* 8 */
        //2) find the clusters from these hits
        ClusterFinder clusFinder = new ClusterFinder();
        List<FittedCluster> clusters = clusFinder.FindHitBasedClustersWithURWell(hits, urCrosses, 
                ct,
                cf,
                Constants.getInstance().dcDetector, hitRead.get_NumTDCBankRows());
        if (clusters.isEmpty()) {
            return true;
        } else {
            List<FittedHit> fhits = rbc.createRawHitList(hits);
            List<FittedCluster> uRWellDCClusters = new ArrayList();
            for(FittedCluster cls : clusters){
                if(!cls.getMatchedURWellCrosses().isEmpty()) uRWellDCClusters.add(cls);
            }
            
            /* 9 */
            rbc.updateListsWithClusterInfo(fhits, clusters);
            event.appendBanks(rbc.fillHitsBank(event, fhits),
                    rbc.fillHBClustersBank(event, clusters),
                    rbc.fillHBURWellDCClustersBank(event, uRWellDCClusters)
            );
        }
        
        return true;
    }

}
