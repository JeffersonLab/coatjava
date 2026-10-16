package org.jlab.service.eb;

import java.util.ArrayList;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.clas.detector.DetectorHeader;
import org.jlab.clas.detector.DetectorParticle;
import org.jlab.rec.eb.EBScalers;
import org.jlab.rec.eb.EBCCDBEnum;
import org.jlab.rec.eb.EBCCDBConstants;

/**
 *
 * @author gavalian
 */
public class EBio {
    
    public static int  TRACKS_HB = 1;
    public static int  TRACKS_TB = 2;
    
    // read header bank information 
    public static DetectorHeader readHeader(DataEvent event, EBScalers ebs, EBCCDBConstants ccdb) {
        
        DetectorHeader dHeader = new DetectorHeader();
       
        if(event.hasBank("RUN::config")==true){
            DataBank bank = event.getBank("RUN::config");
            dHeader.setRun(bank.getInt("run", 0));
            dHeader.setEvent(bank.getInt("event", 0));
            dHeader.setTrigger(bank.getLong("trigger", 0));
            dHeader.setTorus(bank.getFloat("torus", 0));
            dHeader.setSolenoid(bank.getFloat("solenoid", 0));
        }

        // helicity:
        if(ccdb.getInteger(EBCCDBEnum.HELICITY_delay)==0 && event.hasBank("HEL::adc")) {
            final int helComponent=1;
            final int helHalf=2000;
            DataBank bank = event.getBank("HEL::adc");
            for (int ii=0; ii<bank.rows(); ii++) {
                if (bank.getInt("component",ii)==helComponent) {
                    byte helicity=-1;
                    if (bank.getInt("ped",ii)>helHalf) helicity=1;
                    dHeader.setHelicityRaw(helicity);
                    dHeader.setHelicity((byte)(helicity*ccdb.getInteger(EBCCDBEnum.HWP_position)));
                    break;
                }
            }
        }

        // scaler data for beam charge and livetime:
        //EBScalers.Reading ebsr = ebs.readScalers(event,ccdb);
        //dHeader.setBeamChargeGated((float)ebsr.getBeamCharge());
        //dHeader.setLiveTime((float)ebsr.getLiveTime());

        return dHeader;
    }
    
    /**
     * Read tracks from tracking.
     * @param event
     * @param type
     * @return 
     */
    public static List<DetectorParticle>  readTracks(DataEvent event, int type){
        String bankName = "HitBasedTrkg::HBTracks";
        switch (type){
            case 1 : bankName =  "HitBasedTrkg::HBTracks"; break;
            case 2 : bankName = "TimeBasedTrkg::TBTracks"; break;
            default: break;
        }
        List<DetectorParticle> dpList = new ArrayList<>();
    
        if(event.hasBank(bankName)==true){
            EvioDataBank bank = (EvioDataBank) event.getBank(bankName);
            
            int nrows = bank.rows();
            
            for(int i = 0; i < nrows; i++){
                
                DetectorParticle p = new DetectorParticle();
                
                p.vector().setXYZ(
                        bank.getDouble("p0_x",i),
                        bank.getDouble("p0_y",i),
                        bank.getDouble("p0_z",i));
                
                p.vertex().setXYZ(
                        bank.getDouble("Vtx0_x",i),
                        bank.getDouble("Vtx0_y",i),
                        bank.getDouble("Vtx0_z",i));
                
                p.setCharge(bank.getInt("q", i));
                dpList.add(p);
            }
        }
        return dpList;
    }
    
    public static List<DetectorParticle>  readCentralTracks(DataEvent event){
        List<DetectorParticle> dpList = new ArrayList<>();
        if(event.hasBank("CVTRec::Tracks")==true){
            EvioDataBank bank = (EvioDataBank) event.getBank("CVTRec::Tracks");
            int nrows = bank.rows();
            for(int i = 0; i < nrows; i++){
                double pt = bank.getDouble("pt", i);
                double phi0 = bank.getDouble("phi0", i);
                double tandip = bank.getDouble("tandip", i);
                double z0 = bank.getDouble("z0", i);
                double d0 = bank.getDouble("d0", i);
                double xb = bank.getDouble("xb", i);
                double yb = bank.getDouble("yb", i);
                
                DetectorParticle part = new DetectorParticle();
                double pz = pt*tandip;
                double py = pt*Math.sin(phi0);
                double px = pt*Math.cos(phi0);
                
                double vx = -d0*Math.sin(phi0)+xb;
                double vy = d0*Math.cos(phi0)+yb;
                
                part.vector().setXYZ(px, py, pz);
                part.vertex().setXYZ(vx, vy, z0);
                part.setCharge(bank.getInt("q", i));
                dpList.add(part);
            }
        }
        return dpList;
    }
    
}

