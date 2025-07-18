package org.jlab.service.eb;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.clas.detector.DetectorHeader;
import org.jlab.rec.eb.EBScalers;
import org.jlab.rec.eb.EBCCDBEnum;
import org.jlab.rec.eb.EBCCDBConstants;

public class EBio {
    
    // read header bank information 
    public static DetectorHeader readHeader(DataEvent event, EBScalers ebs, EBCCDBConstants ccdb) {
        
        DetectorHeader dHeader = new DetectorHeader();
       
        if(event.hasBank("RUN::config")==true){
            DataBank bank = event.getBank("RUN::config");
            dHeader.setRun(bank.getInt("run", 0));
            dHeader.setEvent(bank.getInt("event", 0));
            dHeader.setTrigger(bank.getLong("trigger", 0));
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
}