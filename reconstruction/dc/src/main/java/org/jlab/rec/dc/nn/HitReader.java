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
import java.util.logging.Logger;
import org.jlab.detector.banks.RawBank;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.geant4.v2.DCGeant4Factory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.banks.Banks;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.service.dc.DCEngine;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author ziegler
 */
public class HitReader {
    private static final Logger LOGGER = Logger.getLogger(HitReader.class.getName());
    private static final int MAX_AITRACKS = 3;
    
    private Banks bankNames;
    private DCGeant4Factory detector;
    private ConstantsManager manager;
    private RawBank.OrderType[] rawBankOrders;

    private int run = 0;
    private long tiTimeStamp = 0;
    private DataEvent event;

    private IndexedTable tt, reverseTT, dcrbjitters, timejitter, wirestat, tdccuts, docares, time2dist, t0s;

    private List<Hit> _DCHits;

    private final double timeBuf = 25.0;

    private final Map<int[], double[]> aimatchtrk = new HashMap<>();
    private final Map<Integer, List<Hit>> aimatchcls = new HashMap<>();//map cluster id to list of hits ids
    private final Map<Integer, Integer> aihits = new HashMap<>();//map of hits ids
    public HitReader(Banks names, DCGeant4Factory detector) {
        this.bankNames = names;
        this.detector = detector;
    }

    public HitReader(Banks names, RawBank.OrderType[] rawBankOrders, ConstantsManager manager, DCGeant4Factory detector) {
        this(names, detector);
        this.manager = manager;
        this.rawBankOrders = rawBankOrders;
    }

    // Initialize various constants and parameters
    public void initialize(DataEvent event) {
        this.event = event;
        if (event.hasBank("RUN::config")) {
            DataBank bank = event.getBank("RUN::config");
            run = bank.getInt("run", 0);
            tiTimeStamp = bank.getLong("timestamp", 0);
        }
        if (manager != null) {
            tt = manager.getConstants(run, Constants.TT);
            timejitter = manager.getConstants(run, Constants.TIMEJITTER);
            wirestat = manager.getConstants(run, Constants.WIRESTAT);
            tdccuts = manager.getConstants(run, Constants.TDCTCUTS);
            docares = manager.getConstants(run, Constants.DOCARES);
            time2dist = manager.getConstants(run, Constants.TIME2DIST);
            t0s = manager.getConstants(run, Constants.T0CORRECTION);
        }
    }

    // Main method for reading NN hits
    public void read_NNHits(DataEvent event, boolean readInstarec, boolean enableMulti) {
        this.initialize(event);
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

        List<TrackInfo> trackInfoL = processTrackInfo(bankAI, cartesian, enableMulti);
        List<List<TrackInfo>> trackInfoLs = getNNSeedLists(trackInfoL);

        // Process track info and match with top 3 tracks
        processTopTracks(trackInfoLs);

        // Process hits using the top 3 tracks
        processHits(bank,bankCls);
    }

    // Processes TrackInfo from the bank
    private List<TrackInfo> processTrackInfo(DataBank bankAI, boolean cartesian, boolean enableMulti) {
        List<TrackInfo> trackInfoL = new ArrayList<>();

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

            if (!enableMulti) {
                int status = (int) bankAI.getShort("status", j);
                if (status != 0) {
                    trackInfoL.add(new TrackInfo(ids, tPars));
                }
            } else {
                trackInfoL.add(new TrackInfo(ids, tPars));
            }
        }
        return trackInfoL;
    }

    // Process top 3 tracks based on probability
    private void processTopTracks(List<List<TrackInfo>> trackInfoLs) {
        for (List<TrackInfo> trackInfoList : trackInfoLs) {
            trackInfoList.sort((a, b) -> Double.compare(b.getProb(), a.getProb()));

            // Only keep the top 3 tracks
            trackInfoList = trackInfoList.subList(0, Math.min(MAX_AITRACKS, trackInfoList.size()));

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

    
    
}
