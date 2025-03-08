/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.dc.nn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.jlab.detector.banks.RawBank;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.geant4.v2.DCGeant4Factory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.banks.Banks;
import org.jlab.rec.dc.banks.HitReader;
import org.jlab.rec.dc.hit.Hit;
/**
 *
 * @author ziegler
 */
public class AIHitReader extends HitReader {
    private static final Logger LOGGER = Logger.getLogger(AIHitReader.class.getName());
    public static final int MAX_AITRACKS = 3;
    private final Map<int[], double[]> aimatchtrk = new HashMap<>();
    private final Map<Integer, List<Hit>> aimatchcls = new HashMap<>();//map cluster id to list of hits ids
    private final Map<Integer, Integer> aihits = new HashMap<>();//map of hits ids
    private DataEvent event;
    public static final double OUTLIERCUT=1.2;

    public AIHitReader(Banks names, RawBank.OrderType[] rawBankOrders, ConstantsManager manager, DCGeant4Factory detector) {
        super(names, rawBankOrders, manager, detector);
    }
    public AIHitReader(Banks names, DCGeant4Factory detector) {
        super(names, detector);      
    }
    
    // Main method for reading NN hits
    public void read_NNHits(DataEvent event, boolean readInstarec, boolean enableMulti) {
        this.initialize(event);
        this.event = event;
        boolean multi = false;
        if (readInstarec) 
            multi = enableMulti;
           
        this.readNNHits(readInstarec, multi);
    }

    // Checks if two TrackInfo objects overlap
    static boolean overlaps(TrackInfo t, List<TrackInfo> trackInfoL) {
        for (TrackInfo ti : trackInfoL) {
            boolean isInGroup = false;
            for (int i = 0; i < 6; i++) {
                if (ti.getIds()[i] == t.getIds()[i] && t.getIds()[i] > 0) {
                    isInGroup = true;
                }
            }
            if (!isInGroup) return false;
        }
        return true;
    }


    // Reads InstruRec NN hits
    private void readNNHits(boolean instarec, boolean enableMulti) {
        setDCHits(new ArrayList<>());
        aimatchtrk.clear();

        if (!event.hasBank(bankNames.getInputHitsBank())) {
            LOGGER.warning("Missing bank " + bankNames.getInputHitsBank());
        }

        if (!event.hasBank(bankNames.getInputClustersBank())) {
            LOGGER.warning("Missing bank " + bankNames.getInputClustersBank());
        }

        

        if (!(event.hasBank(bankNames.getInputHitsBank())
                && event.hasBank(bankNames.getInputClustersBank()))) {
            LOGGER.warning("Missing one or more required banks");
            return;
        }

        DataBank bankAI ;
        boolean cartesian = false;
        if(instarec) {
            if (!event.hasBank(bankNames.getInstarecBank())) {
                LOGGER.warning("Missing bank " + bankNames.getInstarecBank());
                return;
            }
            bankAI = event.getBank(bankNames.getInstarecBank());
            cartesian = true;
        } else {
            if (!event.hasBank(bankNames.getInstarecBank())) {
                LOGGER.warning("Missing bank " + bankNames.getAiBank());
                return;
            }
            bankAI = event.getBank(bankNames.getAiBank());
        }
        DataBank bank = event.getBank(bankNames.getInputHitsBank());
        DataBank bankCls = event.getBank(bankNames.getInputClustersBank());

        Map<TrackInfo, List<TrackInfo>> trackInfoL = processTrackInfo(bankAI, cartesian, enableMulti);

        // Process track info and match with top 3 tracks
        processTopTracks(trackInfoL);

        // Process hits using the top 3 tracks
        processHits(bank,bankCls);
    }

    private Map<TrackInfo, List<TrackInfo>> processTrackInfo(DataBank bankAI, boolean cartesian, boolean enableMulti) {
        Map<TrackInfo, List<TrackInfo>> trackInfoLM = new HashMap<>();
        List<TrackInfo> trackInfoL = new ArrayList<>();
        nnTrks=0;
        for (int j = 0; j < bankAI.rows(); j++) {
            int[] ids = new int[6];
            double[] tPars = new double[5];

            for (int s = 0; s < 6; s++) {
                String stg = "c" + (s + 1);
                ids[s] = (int) bankAI.getShort(stg, j);
            }

            if(cartesian) {
                tPars[0] = (double) bankAI.getFloat("px", j);
                tPars[1] = (double) bankAI.getFloat("py", j);
                tPars[2] = (double) bankAI.getFloat("pz", j);
            } else {
                tPars[0] = (double)bankAI.getFloat("p", j);
                tPars[1] = (double)bankAI.getFloat("theta", j);
                tPars[2] = (double)bankAI.getFloat("phi", j);
            }
            tPars[3] = (double) bankAI.getShort("id", j);
            tPars[4] = (double) bankAI.getFloat("prob", j);

            int status = (int) bankAI.getShort("status", j);
            if (status != 0) {
                    setNNTrks(getNNTrks() + 1); //for debugging
            }
            if (!enableMulti) {
                if (status != 0) {
                    trackInfoL.add(new TrackInfo(ids, tPars));
                }
            } else {
                TrackInfo ti = new TrackInfo(ids, tPars);
                if(status!=0) {System.out.println("SEED "+Arrays.toString(ti.getIds()));
                    trackInfoLM.computeIfAbsent(ti,  k-> new ArrayList<>()).add(ti); //this is the seed
                } else {
                    trackInfoL.add(ti); //these are the seed overlaps
                    System.out.println("OVL "+Arrays.toString(ti.getIds()));
                }
            }
        }
        Set<Integer> setA = new HashSet<>();
        Set<Integer> setB = new HashSet<>();
        for(TrackInfo ti: trackInfoLM.keySet()) {
            for(TrackInfo tj : trackInfoL) {
                setA.clear();
                for (int num : ti.ids) {
                    setA.add(num);
                }
                setB.clear();
                for (int num : tj.ids) {
                    setB.add(num);
                }
                setA.retainAll(setB);
                if (!setA.isEmpty()) {
                    System.out.println("There is an overlap. Common values: " + setA);
                    trackInfoLM.get(ti).add(tj);
                }
            }
        }
            return trackInfoLM;
    }
    
    // Process top 3 tracks based on probability
    private void processTopTracks(Map<TrackInfo, List<TrackInfo>> trackInfoLs) { 
        for (List<TrackInfo> trackInfoList : trackInfoLs.values()) {System.out.println("***********");
            trackInfoList.sort((a, b) -> Double.compare(b.getProb(), a.getProb()));
            for(TrackInfo t : trackInfoList)
                System.out.println("NN "+Arrays.toString(t.getIds()));
            // Only keep the top 3 tracks including the seed
            
            trackInfoList = trackInfoList.subList(0, Math.min(MAX_AITRACKS, trackInfoList.size()));
            for(TrackInfo t : trackInfoList)
                System.out.println("UpdatedNN "+Arrays.toString(t.getIds()));
            for (TrackInfo trackInfo : trackInfoList) {
                aimatchtrk.put(trackInfo.getIds(), trackInfo.getTPars());
            }
        }
    }

    // Processes hits from the bank using the top 3 tracks
    private void processHits(DataBank bank, DataBank cbank) {
        aihits.clear();
        //get the list of valid hits
        for (int i = 0; i < bank.rows(); i++) {
            int hitID = bank.getShort("id", i);
            int clusterID = bank.getShort("clusterID", i);
            if(clusterID!=-1) {
                aihits.put(hitID, i);//hit id and row
            }
        }
        aimatchcls.clear();
        //read the cluster bank to get the list of hits belonging to it.
        for (int i = 0; i < cbank.rows(); i++) {
            int clusterID = cbank.getShort("id", i);
            int[] hids = new int[12];
            for(int ki =0; ki<12; ki++) {
                String st = "Hit";
                st+=(ki+1);
                st+="_ID";
                hids[ki]=(int)cbank.getShort(st, i);
                if(aihits.containsKey(hids[ki])) {
                    for (int[] cids : aimatchtrk.keySet()) {
                        boolean found = Arrays.stream(cids).anyMatch(id -> id == clusterID);
                        if (found) { 
                            Hit hit = createHit(bank, aihits.get(hids[ki]), cids);
                            hit.NNClusId = clusterID;
                            aimatchcls.computeIfAbsent(clusterID,  k-> new ArrayList<>()).add(hit);
                            this.getDCHits().add(hit); 
                        }
                    }
                }
            }
        }
    }
        

    // Creates a Hit object and populates its data
    private Hit createHit(DataBank bank, int row) {
        Hit hit = new Hit(
                bank.getByte("sector", row),
                bank.getByte("superlayer", row),
                bank.getByte("layer", row),
                bank.getShort("wire", row),
                bank.getInt("TDC", row),
                bank.getByte("jitter", row),
                bank.getShort("id", row)
        );
        hit.set_Id(bank.getShort("id", row));
        hit.calc_CellSize(detector);
        hit.aiClusDoca = bank.getFloat("trkDoca", row);
        if(hit.aiClusDoca/hit.get_CellSize()>this.OUTLIERCUT) hit.outlier = true;
        double posError = hit.get_CellSize() / Math.sqrt(12.);
        hit.set_DocaErr(posError);

        return hit;
    }
    private Hit createHit(DataBank bank, int row, int[] cids) {
        Hit hit = this.createHit(bank, row);
        
        // Assign track properties
        hit.NNTrkId = (int) aimatchtrk.get(cids)[3];
        
        //hit.NNTrkP = aimatchtrk.get(cids)[0];
        //hit.NNTrkTheta = aimatchtrk.get(cids)[1];
        //hit.NNTrkPhi = aimatchtrk.get(cids)[2];
        
        return hit;
    }

    private List<Hit> _DCHits;
    /**
     * @return the _DCHits
     */
    public List<Hit> getDCHits() {
        return _DCHits;
    }

    /**
     * @param _DCHits the _DCHits to set
     */
    public void setDCHits(List<Hit> _DCHits) {
        this._DCHits = _DCHits;
    }

    private static int nnTrks=0;
    /**
     * @return the nnTrks
     */
    public static int getNNTrks() {
        return nnTrks;
    }

    /**
     * @param _nnTrks the nnTrks to set
     */
    public static void setNNTrks(int _nnTrks) {
        nnTrks = _nnTrks;
    }


    // Helper class to store track information
    public static class TrackInfo implements Comparable<TrackInfo> {
        private final int[] ids; // To store c1-c6 values
        private final double[] tPars;
        private final double prob;

        public TrackInfo(int[] ids, double[] tPars) {
            // Create defensive copies of the arrays to ensure immutability
            this.ids = ids.clone(); 
            this.tPars = tPars.clone();
            this.prob = tPars[4]; // prob is at index 4
        }

        public int[] getIds() {
            return ids.clone(); // Return a copy to prevent external modification
        }

        public double[] getTPars() {
            return tPars.clone(); // Return a copy to prevent external modification
        }

        public double getProb() {
            return prob;
        }

        // Override equals to only compare the ids
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrackInfo trackInfo = (TrackInfo) o;
            return Arrays.equals(ids, trackInfo.ids); // Compare only ids
        }

        // Override hashCode to only depend on the ids
        @Override
        public int hashCode() {
            return Arrays.hashCode(ids);  // Use only ids for hashCode
        }
        @Override
        public int compareTo(TrackInfo other) {
            int[] c = new int[6];
            for (int i = 0; i < 6; i++) {
                c[i] = this.ids[i] < other.ids[i] ? -1 : this.ids[i] == other.ids[0] ? 0 : 1;
            }

            int return_val = ((c[0] == 0) ? c[1] : c[0]);
            for (int i = 1; i < 6; i++) {
                return_val = ((c[i] == 0) ? return_val : c[i]);
            }
            return return_val;
        }
    }

    
    
}
