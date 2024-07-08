/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jlab.clas.swimtools.Swim;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.banks.RecoBankReader;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.mlanalysis.AIObject;

/**
 *
 * @author ziegler
 */
public class ClusterBankIO {

    
    private List<Cluster> SVTClusters;
    private List<Cluster> BMTClusters;
    private List<Hit> SVTHits;
    private List<Hit> BMTHits;
    
    public List<Cluster> getMLClusters(DataEvent event, Swim swimmer, double ... stats) {
        setSVTClusters(new ArrayList<>());
        setBMTClusters(new ArrayList<>());
        List<Hit> SVThits = RecoBankReader.readBSTHitBank(event);
        List<Hit> BMThits = RecoBankReader.readBMTHitBank(event);
        setSVTHits(SVThits);
        setBMTHits(BMThits);
        if(SVThits!= null) {
            Collections.sort(SVThits);
        }
        if(BMThits!=null) {
            for(Hit hit : BMThits) { 
                hit.getStrip().calcBMTStripParams(hit.getSector(), hit.getLayer(), swimmer);
            }
            Collections.sort(BMThits);
        }
        
        Map<Integer, Cluster> SVTclusters = RecoBankReader.readBSTClusterBank(event, SVThits);
        Map<Integer, Cluster> BMTclusters = RecoBankReader.readBMTClusterBank(event, BMThits);
        DataBank aibank = event.getBank("cvtml::clusters");
        
        int n = stats.length;
        List<Cluster> seeds = new ArrayList<>();
        for(int i = 0; i < aibank.rows(); i++) { 
            boolean pass = false;
            int status = aibank.getShort("status", i);
            for(int j =0; j<n; j++) { 
                if(status>=stats[j])
                    pass = true;
            }
            int id   = aibank.getShort("id", i); 
            double x = aibank.getFloat("x", i);
            double y = aibank.getFloat("y", i);
            double z = aibank.getFloat("z", i);
            double p = aibank.getFloat("p", i);
            double th = aibank.getFloat("th", i);
            double fi = aibank.getFloat("fi", i);
            double pars[] = new double[]{x,y,z,p,th,fi};//to be used later
            
            if(SVTclusters.containsKey(id)) { 
                if(pass) { 
                    SVTclusters.get(id).setAssociatedTrackPars(pars);
                    getSVTClusters().add(SVTclusters.get(id));
                }
            }  
            if(BMTclusters.containsKey(id)) {
                if(pass) {
                    BMTclusters.get(id).setAssociatedTrackPars(pars);
                    getBMTClusters().add(BMTclusters.get(id)); 
                }
            }  
        }
       
        seeds.addAll(getSVTClusters());
        seeds.addAll(getBMTClusters());
        return seeds;
    }
    
    public List<Cluster> getClusters(DataEvent event, Swim swimmer) { //Conventional
        setSVTClusters(new ArrayList<>());
        setBMTClusters(new ArrayList<>());
        List<Hit> SVThits = RecoBankReader.readBSTHitBank(event);
        List<Hit> BMThits = RecoBankReader.readBMTHitBank(event);
        setSVTHits(SVThits);
        setBMTHits(BMThits);
        if(SVThits!= null) {
            Collections.sort(SVThits);
        }
        if(BMThits!=null) {
            for(Hit hit : BMThits) { 
                hit.getStrip().calcBMTStripParams(hit.getSector(), hit.getLayer(), swimmer);
            }
            Collections.sort(BMThits);
        }
        
        Map<Integer, Cluster> SVTclusters = RecoBankReader.readBSTClusterBank(event, SVThits);
        Map<Integer, Cluster> BMTclusters = RecoBankReader.readBMTClusterBank(event, BMThits);
        DataBank aibank = event.getBank("cvtml::clusters");
        
        List<Cluster> seeds = new ArrayList<>();
        for(int i = 0; i < aibank.rows(); i++) { 
            boolean pass = false;
            int status = aibank.getShort("status", i);
            if(status>=0) { 
                    pass = true;
            }
            
            int id   = aibank.getShort("id", i); 
            if(SVTclusters.containsKey(id)) { 
                if(pass) { 
                    getSVTClusters().add(SVTclusters.get(id));
                }
            }  
            if(BMTclusters.containsKey(id)) {
                if(pass) {
                    getBMTClusters().add(BMTclusters.get(id)); 
                }
            }  
        }
       
        seeds.addAll(getSVTClusters());
        seeds.addAll(getBMTClusters());
        return seeds;
    }
    
    public static void fillBank(DataBank aibank, List<Cluster> sVTClusters, List<Cluster> bMTClusters) {
        for(int l = 0; l<sVTClusters.size(); l++){
            aibank.setShort("id", l,  (short)sVTClusters.get(l).getId());
            aibank.setShort("status", l,  (short)sVTClusters.get(l).getMLStatus());
            aibank.setByte("sector", l, (byte)sVTClusters.get(l).getSector());
            aibank.setShort("layer", l, (short)sVTClusters.get(l).getLayer());
            aibank.setFloat("xo", l, (float)sVTClusters.get(l).getLine().origin().x());
            aibank.setFloat("yo", l, (float)sVTClusters.get(l).getLine().origin().y());
            aibank.setFloat("zo", l, (float)sVTClusters.get(l).getLine().origin().z());
            aibank.setFloat("xe", l, (float)sVTClusters.get(l).getLine().end().x());
            aibank.setFloat("ye", l, (float)sVTClusters.get(l).getLine().end().y());
            aibank.setFloat("ze", l, (float)sVTClusters.get(l).getLine().end().z());
            double[] trkpars = sVTClusters.get(l).getAssociatedTrackPars();
            aibank.setFloat("x", l, (float)trkpars[0]);
            aibank.setFloat("y", l, (float)trkpars[1]);
            aibank.setFloat("z", l, (float)trkpars[2]);
            aibank.setFloat("p", l, (float)trkpars[3]);
            aibank.setFloat("th", l, (float)trkpars[4]);
            aibank.setFloat("fi", l, (float)trkpars[5]);  
        }
        int l0 = sVTClusters.size();
        for(int l = 0; l<bMTClusters.size(); l++){ 
            aibank.setShort("id", l+l0,  (short)bMTClusters.get(l).getId());
            aibank.setShort("status", l+l0,  (short)bMTClusters.get(l).getMLStatus());
            aibank.setByte("sector", l+l0, (byte)bMTClusters.get(l).getSector());
            aibank.setShort("layer", l+l0, (short)bMTClusters.get(l).getLayer());
            if(bMTClusters.get(l).getType()==BMTType.Z) {
                aibank.setFloat("xo", l+l0, (float)bMTClusters.get(l).getLine().origin().x());
                aibank.setFloat("yo", l+l0, (float)bMTClusters.get(l).getLine().origin().y());
                aibank.setFloat("zo", l+l0, (float)bMTClusters.get(l).getLine().origin().z());
                aibank.setFloat("xe", l+l0, (float)bMTClusters.get(l).getLine().end().x());
                aibank.setFloat("ye", l+l0, (float)bMTClusters.get(l).getLine().end().y());
                aibank.setFloat("ze", l+l0, (float)bMTClusters.get(l).getLine().end().z());
            } else {
                aibank.setFloat("xo", l+l0, (float)bMTClusters.get(l).getAxis().origin().x());
                aibank.setFloat("yo", l+l0, (float)bMTClusters.get(l).getAxis().origin().y());
                aibank.setFloat("zo", l+l0, (float)bMTClusters.get(l).getAxis().origin().z());
                aibank.setFloat("xe", l+l0, (float)bMTClusters.get(l).getAxis().end().x());
                aibank.setFloat("ye", l+l0, (float)bMTClusters.get(l).getAxis().end().y());
                aibank.setFloat("ze", l+l0, (float)bMTClusters.get(l).getAxis().end().z());
            }
            double[] trkpars = bMTClusters.get(l).getAssociatedTrackPars();
            aibank.setFloat("x", l+l0, (float)trkpars[0]);
            aibank.setFloat("y", l+l0, (float)trkpars[1]);
            aibank.setFloat("z", l+l0, (float)trkpars[2]);
            aibank.setFloat("p", l+l0, (float)trkpars[3]);
            aibank.setFloat("th", l+l0, (float)trkpars[4]);
            aibank.setFloat("fi", l+l0, (float)trkpars[5]);
        }
    }

    static void fillBank(DataBank aibank, List<AIObject> aios) {
    for(int l = 0; l<aios.size(); l++){
            aibank.setShort("id", l,  aios.get(l).id);
            aibank.setShort("status", l,  aios.get(l).status);
            aibank.setByte("sector", l, aios.get(l).sector);
            aibank.setShort("layer", l, aios.get(l).layer);
            aibank.setFloat("xo", l, aios.get(l).xo);
            aibank.setFloat("yo", l, aios.get(l).yo);
            aibank.setFloat("zo", l, aios.get(l).zo);
            aibank.setFloat("xe", l, aios.get(l).xe);
            aibank.setFloat("ye", l, aios.get(l).ye);
            aibank.setFloat("ze", l, aios.get(l).ze);
            aibank.setFloat("x", l, aios.get(l).x);
            aibank.setFloat("y", l, aios.get(l).y);
            aibank.setFloat("z", l, aios.get(l).z);
            aibank.setFloat("p", l, aios.get(l).p);
            aibank.setFloat("th", l, aios.get(l).th);
            aibank.setFloat("fi", l, aios.get(l).fi);
        }    
    }
    /**
     * @return the SVTClusters
     */
    public ArrayList<Cluster> getSVTClusters() {
        return (ArrayList<Cluster>) SVTClusters;
    }

    /**
     * @param SVTClusters the SVTClusters to set
     */
    public void setSVTClusters(ArrayList<Cluster> SVTClusters) {
        this.SVTClusters = SVTClusters;
    }

    /**
     * @return the BMTClusters
     */
    public ArrayList<Cluster> getBMTClusters() {
        return (ArrayList<Cluster>) BMTClusters;
    }

    /**
     * @param BMTClusters the BMTClusters to set
     */
    public void setBMTClusters(ArrayList<Cluster> BMTClusters) {
        this.BMTClusters = BMTClusters;
    }

    /**
     * @return the SVTHits
     */
    public List<Hit> getSVTHits() {
        return SVTHits;
    }

    /**
     * @param SVTHits the SVTHits to set
     */
    public void setSVTHits(List<Hit> SVTHits) {
        this.SVTHits = SVTHits;
    }

    /**
     * @return the BMTHits
     */
    public List<Hit> getBMTHits() {
        return BMTHits;
    }

    /**
     * @param BMTHits the BMTHits to set
     */
    public void setBMTHits(List<Hit> BMTHits) {
        this.BMTHits = BMTHits;
    }
    
    public class ArrayKey {
        private final double[] key;

        public ArrayKey(double[] key) {
            this.key = Arrays.copyOf(key, key.length);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrayKey that = (ArrayKey) o;
            return Arrays.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(key);
        }
    }
}
