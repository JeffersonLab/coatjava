package org.jlab.service.swaps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jlab.detector.swaps.SwapManager;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataBank;

/**
 *
 * Initializes SwapManager singleton based on two CCDB timestamps to allow
 * fixing cable swaps in decoded HIPO files.  Optionally modifies ADC/TDC banks.
 * 
 * @author baltzell
 */
public class SwapEngine extends ReconstructionEngine {

    private SwapManager swapman = null;

    public SwapEngine() {
        super("SwapEngine","baltzell","1.0");
    }

    private void updateBank(int run,DataBank bank) {
        for (int irow=0; irow<bank.rows(); irow++) {
            final int[] slco = swapman.get(run,bank,irow);
            bank.setByte("sector",irow,(byte)slco[0]);
            bank.setByte("layer",irow,(byte)slco[1]);
            bank.setShort("component",irow,(short)slco[2]);
            // Restore the original order decade to the new unswapped, true order:
            final byte decade = (byte) (bank.getByte("order", irow)/10);
            final byte order = (byte) (10*decade + (byte)slco[3]);
            bank.setByte("order",irow, order);
        }
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        DataBank bank = event.getBank("RUN::config");
        final int run = bank.getInt("run",0);
        for (String detectorName : this.swapman.getDetectors()) {
            for (String bankName : this.swapman.getBanks(detectorName)) {
                bank = event.getBank(bankName);
                event.removeBank(bankName);
                this.updateBank(run,bank);
                event.appendBank(bank);
            }
        }
        return true;
    }

    @Override
    public boolean init() {

        // get timestamp for old translation tables:
        String previousTimestamp = this.getEngineConfigString("previousTimestamp");
        if (previousTimestamp == null) {
            System.err.println("["+this.getName()+"] --> Missing previousTimestamp in YAML.");
            return false;
        }
        
        // get timestamp for new translation tables:
        String currentTimestamp = this.getEngineConfigString("timestamp");

        // select detector list, initialize bank and translation table names:
        List<String> dets = new ArrayList<>();
        if (this.getEngineConfigString("detectors") == null) {
            System.out.println("["+this.getName()+"] --> No detectors specified in YAML, assuming all.");
        }
        else {
            dets.addAll(Arrays.asList(this.getEngineConfigString("detectors").split(",")));
        }

        System.out.println("["+this.getName()+"] --> Setting current variation : "+SwapManager.DEF_CURRENT_CCDB_VARIATION);
        System.out.println("["+this.getName()+"] --> Setting previous variation : "+SwapManager.DEF_PREVIOUS_CCDB_VARIATION);
        System.out.println("["+this.getName()+"] --> Setting current timestamp : "+currentTimestamp);
        System.out.println("["+this.getName()+"] --> Setting previous timestamp : "+previousTimestamp);
        System.out.println("["+this.getName()+"] --> Setting detectors : "+this.getEngineConfigString("detectors"));
        System.out.println("["+this.getName()+"] --> Modifying ADC/TDC banks!");

        this.swapman = SwapManager.getInstance();
        this.swapman.initialize(dets,previousTimestamp,currentTimestamp);
       
        System.out.println("["+this.getName()+"] --> swaps are ready....");
        return true;
    }

}
