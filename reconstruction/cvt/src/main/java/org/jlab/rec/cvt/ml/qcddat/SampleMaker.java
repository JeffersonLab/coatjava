package org.jlab.rec.cvt.ml.qcddat;

import org.jlab.rec.cvt.ml.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.utils.groups.IndexedTable;

import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Arc3D;
import org.jlab.rec.cvt.banks.HitReader;

/**
 * Service to return reconstructed TRACKS
 * format
 *
 * @author ziegler
 *
 */
public class SampleMaker extends ReconstructionEngine {

    private String svtHitBank;
    
    public SampleMaker(String name) {
        super(name, "ziegler", "6.0");
    }

    public SampleMaker() {
        super("CVTQCDDATEngine", "ziegler", "6.0");
    }

    @Override
    public void detectorChanged(int run) {}

    @Override
    public boolean init() {   
        this.initConstantsTables();
        this.registerBanks();
        return true;    
    }
    
    public void registerBanks() {
        this.setSvtHitBank("CVT::QCDDATHit");
        super.registerOutputBank(this.svtHitBank);
    }
    
    public int getRun(DataEvent event) {
    
        if (event.hasBank("RUN::config") == false) {
            System.err.println("RUN CONDITIONS NOT READ!");
            return 0;
        }

        DataBank bank = event.getBank("RUN::config");
        int run = bank.getInt("run", 0);  
        if(Constants.getInstance().seedingDebugMode) {
            System.out.println("EVENT "+bank.getInt("event", 0));
        }
        return run;
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
       
        int run = this.getRun(event); 
        Swim swimmer = new Swim();
        IndexedTable svtStatus          = this.getConstantsManager().getConstants(run, "/calibration/svt/status");
        IndexedTable svtLorentz         = this.getConstantsManager().getConstants(run, "/calibration/svt/lorentz_angle");
        IndexedTable bmtStatus          = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_status");
        IndexedTable bmtTime            = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_time");
        IndexedTable bmtVoltage         = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_voltage");
        IndexedTable bmtStripVoltage    = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_strip_voltage");
        IndexedTable bmtStripThreshold  = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_strip_voltage_thresholds");
        IndexedTable adcStatus            = this.getConstantsManager().getConstants(run, "/calibration/svt/adcstatus");
        
        Geometry.getInstance().initialize(this.getConstantsManager().getVariation(), run, svtLorentz, bmtVoltage);
        
        HitReader hitRead = new HitReader();
        hitRead.fetch_SVTHits(event, -1, -1, svtStatus, adcStatus);
        hitRead.fetch_BMTHits(event, swimmer, bmtStatus, bmtTime, 
                  bmtStripVoltage, bmtStripThreshold);
        List<ArrayList<Hit>>  hits = new ArrayList<>();
        if(hitRead.getSVTHits() == null) {
            hits.add(new ArrayList<>());
        }
        else {
            hits.add((ArrayList<Hit>) hitRead.getSVTHits());
        }
        if(hitRead.getBMTHits() == null) {
            hits.add(new ArrayList<>());
        }
        else {
            hits.add((ArrayList<Hit>) hitRead.getBMTHits());
        }
        
        if (event.hasBank("MC::True")) {
            DataBank mcTrue = event.getBank("MC::True");
            TrackingPerformance.MatchHitsToMC(hits, mcTrue);
        }
        
        if (hits.isEmpty()) 
            return false;
        DataBank bank = event.createBank(this.svtHitBank, hits.get(0).size()+hits.get(1).size());
        int index=0;
        for(int i = 0; i < hits.size(); i++) {
            for(int j = 0; j < hits.get(i).size(); j++) {
                bank.setShort("id", index, (short) hits.get(i).get(j).getId());
                bank.setShort("mctid", index, (short) hits.get(i).get(j).getAssociateMCTrkId());
                bank.setByte("sector", index, (byte) hits.get(i).get(j).getSector());
                int layer = hits.get(i).get(j).getLayer();
                if(i>0) layer+=6;
                bank.setByte("layer", index, (byte) layer);
                bank.setShort("strip", index, (short) hits.get(i).get(j).getStrip().getStrip());
                bank.setFloat("energy", index, (short) hits.get(i).get(j).getStrip().getEdep());
                bank.setFloat("time", index, (short) hits.get(i).get(j).getStrip().getTime());
                int mctrue=-1;
                if(hits.get(i).get(j).MCstatus==0) {
                    mctrue=0;
                } else {
                    mctrue=1;
                }
                bank.setByte("mctrue", index, (byte) mctrue);
                if(hits.get(i).get(j).getDetector()==DetectorType.BST ||
                        (hits.get(i).get(j).getDetector()==DetectorType.BMT 
                        && hits.get(i).get(j).getType()==BMTType.Z)) {
                    Line3D sline = hits.get(i).get(j).getStrip().getLine();
                    bank.setFloat("x1",   index,  (float) sline.origin().x()/10);
                    bank.setFloat("y1",   index,  (float) sline.origin().y()/10);
                    bank.setFloat("z1",   index,  (float) sline.origin().z()/10);
                    bank.setFloat("x2",   index,  (float) sline.midpoint().x()/10);
                    bank.setFloat("y2",   index,  (float) sline.midpoint().y()/10);
                    bank.setFloat("z2",   index,  (float) sline.midpoint().z()/10);
                    bank.setFloat("x3",   index,  (float) sline.end().x()/10);
                    bank.setFloat("y3",   index,  (float) sline.end().y()/10);
                    bank.setFloat("z3",   index,  (float) sline.end().z()/10);

                }
                if(hits.get(i).get(j).getDetector()==DetectorType.BMT 
                        && hits.get(i).get(j).getType()==BMTType.C) {
                    Arc3D sarc = hits.get(i).get(j).getStrip().getArc();
                    bank.setFloat("x1",   index,  (float) sarc.origin().x()/10);
                    bank.setFloat("y1",   index,  (float) sarc.origin().y()/10);
                    bank.setFloat("z1",   index,  (float) sarc.origin().z()/10);
                    bank.setFloat("x2",   index,  (float) sarc.point(sarc.theta()/2).x()/10);
                    bank.setFloat("y2",   index,  (float) sarc.point(sarc.theta()/2).y()/10);
                    bank.setFloat("z2",   index,  (float) sarc.point(sarc.theta()/2).z()/10);
                    bank.setFloat("x3",   index,  (float) sarc.end().x()/10);
                    bank.setFloat("y3",   index,  (float) sarc.end().y()/10);
                    bank.setFloat("z3",   index,  (float) sarc.end().z()/10);
                }
            
                index++;
            }  
        }
        //bank.show();
        event.appendBanks(bank);
        
        return true;
    }

         
    
    public void initConstantsTables() {
        String[] tables = new String[]{
            "/calibration/svt/status",
            "/calibration/svt/lorentz_angle",
            "/calibration/mvt/bmt_time",
            "/calibration/mvt/bmt_status",
            "/calibration/mvt/bmt_voltage",
            "/calibration/mvt/bmt_strip_voltage",
            "/calibration/mvt/bmt_strip_voltage_thresholds",
            "/geometry/beam/position",
            "/calibration/svt/adcstatus"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");
    }
    
    public void setSvtHitBank(String bstHitBank) {
        this.svtHitBank = bstHitBank;
    }
    public String getSvtHitBank() {
        return this.svtHitBank;
    }
}
