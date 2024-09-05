package org.jlab.analysis.postprocess;

import java.sql.Time;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.RCDBConstants;
import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.helicity.HelicitySequenceManager;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.logging.DefaultLogger;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

/**
 * Rebuild RUN::scaler from RAW::scaler
 * 
 * @author baltzell
 */
public class RebuildScalers {

    static final String CCDB_FCUP_TABLE="/runcontrol/fcup";
    static final String CCDB_SLM_TABLE="/runcontrol/slm";
    static final String CCDB_HEL_TABLE="/runcontrol/helicity";
    static final String CCDB_DSC_TABLE="/daq/config/scalers/dsc1";
    
    public static void main(String[] args) {

        DefaultLogger.debug();

        OptionParser parser = new OptionParser("rebuildscaler");
        parser.addRequired("-o","output.hipo");
        parser.parse(args);
        List<String> inputList = parser.getInputList();
        if(inputList.isEmpty()==true){
            parser.printUsage();
            System.err.println("\n >>>> error : no input file is specified....\n");
            System.exit(1);
        }
        
        HelicitySequenceManager helSeq = new HelicitySequenceManager(8,inputList);
        
        HipoWriterSorted writer = new HipoWriterSorted();
        writer.getSchemaFactory().initFromDirectory(ClasUtilsFile.getResourceDir("COATJAVA", "etc/bankdefs/hipo4"));
        writer.setCompressionType(2);
        writer.open(parser.getOption("-o").stringValue());
			
        Event event = new Event();
        Bank rawScalerBank = new Bank(writer.getSchemaFactory().getSchema("RAW::scaler"));
        Bank runScalerBank = new Bank(writer.getSchemaFactory().getSchema("RUN::scaler"));
        Bank helScalerBank = new Bank(writer.getSchemaFactory().getSchema("HEL::scaler"));
        Bank runConfigBank = new Bank(writer.getSchemaFactory().getSchema("RUN::config"));
            
        ConstantsManager conman = new ConstantsManager();
        conman.init(Arrays.asList(new String[]{CCDB_FCUP_TABLE,CCDB_SLM_TABLE,CCDB_HEL_TABLE,CCDB_DSC_TABLE}));
        
        for (String filename : inputList) {

            HipoReader reader = new HipoReader();
            reader.open(filename);

            RCDBConstants rcdb = null;
            IndexedTable ccdb_fcup = null;
            IndexedTable ccdb_slm = null;
            IndexedTable ccdb_hel = null;
            IndexedTable ccdb_dsc = null;

            while (reader.hasNext()) {

                // read the event and necessary banks:
                reader.nextEvent(event);
                event.read(runConfigBank);
                event.read(runScalerBank);
                event.read(helScalerBank);
                event.read(rawScalerBank);

                // this is the bank we're here to rebuild:
                event.remove(runScalerBank.getSchema());
                event.remove(helScalerBank.getSchema());

                // get CCDB/RCDB constants:
                if (runConfigBank.getInt("run",0) >= 100) {
                    ccdb_fcup = conman.getConstants(runConfigBank.getInt("run",0),CCDB_FCUP_TABLE);
                    ccdb_slm = conman.getConstants(runConfigBank.getInt("run",0),CCDB_SLM_TABLE);
                    ccdb_hel = conman.getConstants(runConfigBank.getInt("run",0),CCDB_HEL_TABLE);
                    ccdb_dsc = conman.getConstants(runConfigBank.getInt("run",0),CCDB_DSC_TABLE);
                    rcdb = conman.getRcdbConstants(runConfigBank.getInt("run",0));
                }

                // now rebuild the RUN::scaler bank: 
                if (rcdb!=null && ccdb_fcup !=null && rawScalerBank.getRows()>0) {
       
                    DaqScalers ds;
                    if (ccdb_dsc.getIntValue("frequency", 0,0,0) < 2e5) {
                        ds = DaqScalers.create(rawScalerBank, ccdb_fcup, ccdb_slm, ccdb_hel, ccdb_dsc);
                    }
                    else {
                        // Inputs for calculation run duration in seconds, since for
                        // some run periods the DSC2 clock rolls over during a run.
                        Time rst = rcdb.getTime("run_start_time");
                        Date uet = new Date(runConfigBank.getInt("unixtime",0)*1000L);
                        ds = DaqScalers.create(rawScalerBank, ccdb_fcup, ccdb_slm, ccdb_hel, rst, uet);
                    }

                    runScalerBank = ds.createRunBank(writer.getSchemaFactory());
                    helScalerBank = ds.createHelicityBank(writer.getSchemaFactory());
                   
                    Util.assignScalerHelicity(event, helScalerBank, helSeq);

                    // put modified HEL/RUN::scaler back in the event:
                    event.write(runScalerBank);
                    event.write(helScalerBank);
                }

                writer.addEvent(event, event.getEventTag());
            }
            reader.close();
        }
        writer.close();
    }

}
