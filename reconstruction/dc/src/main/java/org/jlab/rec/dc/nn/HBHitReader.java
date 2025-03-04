/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.dc.nn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.detector.banks.RawBank;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.geant4.v2.DCGeant4Factory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.banks.Banks;
import org.jlab.rec.dc.banks.HitReader;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.rec.dc.timetodistance.TimeToDistanceEstimator;
/**
 *
 * @author ziegler
 */
public class HBHitReader extends HitReader{
    private static final Logger LOGGER = Logger.getLogger(HBHitReader.class.getName());
    private static final int MAX_AITRACKS = 3;
    private String recBankName = "RECHB::Event";
    private DataEvent event;
    private final Map<int[], double[]> aimatchtrk = new HashMap<>();
    private final Map<Integer, List<Hit>> aimatchcls = new HashMap<>();//map cluster id to list of hits ids
    private final Map<Pair, Integer> aihits = new HashMap<>();//map of hits ids
    private TimeToDistanceEstimator tde;
    
    public HBHitReader(Banks names, RawBank.OrderType[] rawBankOrders, ConstantsManager manager, DCGeant4Factory detector) {
        super(names, rawBankOrders, manager, detector);
    }
    
    // Main method for reading NN hits
    public void read_AITrkgHBHits(DataEvent event) {
        this.initialize(event);
        this.event = event;
        tde = new TimeToDistanceEstimator();
        this.readHBHits();
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

    // Gets NN Seed Lists grouped by overlapping track information
    public static List<List<TrackInfo>> getNNSeedLists(List<TrackInfo> trackInfoL) {
        Collections.sort(trackInfoL);
        List<List<TrackInfo>> trackInfoLs = new ArrayList<>();
        trackInfoLs.add(new ArrayList<>());
        trackInfoLs.get(0).add(trackInfoL.get(0));

        for (int k = 1; k < trackInfoL.size(); k++) {
            boolean isInGroup = overlaps(trackInfoL.get(k), trackInfoLs.get(trackInfoLs.size() - 1));
            if (isInGroup) {
                trackInfoLs.get(trackInfoLs.size() - 1).add(trackInfoL.get(k));
            } else {
                trackInfoLs.add(new ArrayList<>());
                trackInfoLs.get(trackInfoLs.size() - 1).add(trackInfoL.get(k));
            }
        }
        return trackInfoLs;
    }

    // Reads InstruRec NN hits
    private void readHBHits() {
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

        
        DataBank bank = event.getBank(bankNames.getInputHitsBank());
        DataBank banktids = event.getBank(bankNames.getInputIdsBank());
        DataBank bankCls = event.getBank(bankNames.getInputClustersBank());
        DataBank bankTrks = event.getBank(bankNames.getInputTracksBank());

        List<TrackInfo> trackInfoL = processTrackInfo(bankTrks);
        List<List<TrackInfo>> trackInfoLs = getNNSeedLists(trackInfoL);

        // Process track info and match with top 3 tracks
        processTopTracks(trackInfoLs);

        // Process hits using the top 3 tracks
        processHits(bank,banktids,bankCls);
    }

    // Processes TrackInfo from the bank
    private List<TrackInfo> processTrackInfo(DataBank bank) {//bank = HitBasedTrkg::HBTracks
        List<TrackInfo> trackInfoL = new ArrayList<>();

        for (int j = 0; j < bank.rows(); j++) {
            int[] ids = new int[6];
            double[] tPars = new double[5];

            for (int s = 0; s < 6; s++) {
                String stg = "Cluster" + (s + 1);
                stg+="_ID";
                
                ids[s] = (int) bank.getShort(stg, j);
            }
            tPars[0] = (double) bank.getFloat("p0_x", j);
            tPars[1] = (double) bank.getFloat("p0_y", j);
            tPars[2] = (double) bank.getFloat("p0_z", j);
            
            tPars[3] = (double) bank.getShort("id", j);
            tPars[4] = (double) bank.getFloat("chi2", j);

            trackInfoL.add(new TrackInfo(ids, tPars));
            
        }
        return trackInfoL;
    }

    // Process top 3 tracks based on probability
    private void processTopTracks(List<List<TrackInfo>> trackInfoLs) {
        for (List<TrackInfo> trackInfoList : trackInfoLs) {
            trackInfoList.sort((a, b) -> Double.compare(a.getProb(), b.getProb()));

            // Only keep the top 3 tracks
            trackInfoList = trackInfoList.subList(0, Math.min(MAX_AITRACKS, trackInfoList.size()));

            for (TrackInfo trackInfo : trackInfoList) {
                aimatchtrk.put(trackInfo.getIds(), trackInfo.getTPars());
            }
        }
    }

    // Processes hits from the bank using the top 3 tracks
    private void processHits(DataBank bank, DataBank banktid, DataBank cbank) {
        aihits.clear();
        //get the list of valid hits
        for (int i = 0; i < bank.rows(); i++) {
            int hitID = bank.getShort("id", i);
            int clusterID = bank.getShort("clusterID", i);
            if(clusterID!=-1) {
                Pair pair = new Pair(hitID, clusterID);
                aihits.put(pair, i);//hit id and row
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
                Pair pair = new Pair(hids[ki],clusterID);
                if(aihits.containsKey(pair)) {
                    for (int[] cids : aimatchtrk.keySet()) {
                        boolean found = Arrays.stream(cids).anyMatch(id -> id == clusterID);
                        if (found) { 
                            FittedHit hit = createHit(bank, banktid, aihits.get(pair), cids);
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
    private FittedHit createHit(DataBank bank,DataBank bankti, int row) {
        double T_Start=0;
        double T_0=0;
        if (event.hasBank(recBankName) && 
                event.getBank(recBankName).getFloat("startTime", 0)==-1000) {
            return null;
        } 

        if (!event.hasBank("MC::Particle") &&
                event.getBank("RUN::config").getInt("run", 0) > 100) {
            //T_0 = this.getT0(sector[i], slayer[i], layer[i], wire[i], T0, T0ERR)[0];
            if (event.hasBank(recBankName))
                T_Start = event.getBank(recBankName).getFloat("startTime", 0);
        }  
            
            
        FittedHit hit = new FittedHit(
                bank.getByte("sector", row),
                bank.getByte("superlayer", row),
                bank.getByte("layer", row),
                bank.getShort("wire", row),
                bank.getInt("TDC", row),
                bank.getByte("jitter", row),
                bank.getShort("id", row)
        );
        
        T_0 = this.getT0((int)bank.getByte("sector", row), (int)bank.getByte("superlayer", row), 
               (int)bank.getByte("layer", row), (int)bank.getShort("wire", row), t0s)[0];
        hit.set_Id(bank.getShort("id", row));
        hit.calc_CellSize(detector);
        double posError = hit.get_CellSize() / Math.sqrt(12.);
        hit.set_DocaErr(posError);
        int status = bank.getShort("status", row);
        hit.set_QualityFac(status);
        if (hit.get_Doca() > hit.get_CellSize()) {
            hit.set_OutOfTimeFlag(true);
            hit.set_QualityFac(3);
        }
        hit.set_LeftRightAmb(bank.getByte("LR", row));
        hit.set_AssociatedClusterID(bank.getShort("clusterID", row));
        int tid = (int)bankti.getShort("tid", row);
        hit.set_AssociatedHBTrackID(tid);
        hit.setB((double)bankti.getFloat("B", row));
        hit.set_Beta(this.readBeta(event, tid));
        this.setBetaFlag(event, tid, hit, hit.get_Beta());//reset beta for out of range assuming the pion hypothesis and setting a flag
                
        if (!(event.hasBank("MC::Particle") ||
                event.getBank("RUN::config").getInt("run", 0) < 100)) {
            hit.setTProp((double)bankti.getFloat("TProp", row));
            hit.setTFlight((double)bankti.getFloat("TFlight", row));
                    ///hit.get_Beta0to1());
        }
        
        hit.setT0(T_0);
        hit.setTStart(T_Start);
        double T0Sub = (hit.get_TDC() - hit.getTProp() - hit.getTFlight() - T_0);

        if (Constants.getInstance().isUSETSTART()) {
            T0Sub -= T_Start;
        }
        hit.set_Time(T0Sub);
        if (hit.get_Time() < 0)
            hit.set_QualityFac(2);
        hit.set_TrkgStatus(0);
        hit.calc_CellSize(detector);
        hit.calc_GeomCorr(detector, 0);
        hit.set_ClusFitDoca((double)bank.getFloat("trkDoca", row));
        hit.set_TimeToDistance(event, 0.0, hit.getB(), time2dist, tde);
        hit.set_DocaErr(hit.get_PosErr(event, hit.getB(), docares, time2dist, tde));
        
        return hit;
    }
    private FittedHit createHit(DataBank bank,DataBank banktid, int row, int[] cids) {
        FittedHit hit = this.createHit(bank,banktid, row);
        
        // Assign track properties
        hit.NNTrkId = (int) aimatchtrk.get(cids)[3];
        //hit.NNTrkP = aimatchtrk.get(cids)[0];
        //hit.NNTrkTheta = aimatchtrk.get(cids)[1];
        //hit.NNTrkPhi = aimatchtrk.get(cids)[2];
        return hit;
    }

    private List<FittedHit> _DCHits;
    /**
     * @return the _DCHits
     */
    public List<FittedHit> getDCHits() {
        return _DCHits;
    }

    /**
     * @param _DCHits the _DCHits to set
     */
    public void setDCHits(List<FittedHit> _DCHits) {
        this._DCHits = _DCHits;
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

    // Pair class implementation 
    class Pair<T, U> {
        private final T first;
        private final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }

        public T getFirst() {
            return first;
        }

        public U getSecond() {
            return second;
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
        }
    }
    
}
