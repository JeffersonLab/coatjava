package org.jlab.analysis.eventmerger;
import java.util.List;
import org.jlab.detector.epics.EpicsSequence;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.*;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.jnp.utils.json.JsonObject;
import org.jlab.logging.DefaultLogger;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;


/**
 * Random trigger filtering tool: filters hipo event according to trigger bit 
 * and beam current to create random-trigger files for background merging
 *  
 * Usage : trigger-filter -b [trigger bit mask (e.g. 0x0000008000000000 to select the FC trigger] -o [output file]
 * 
 * Options :
 *  -c : minimum beam current (default = -1)
 *  -e : name of required bank, e.g. DC::tdc (default = )
 *  -n : maximum number of events to process (default = -1)
 *  -r : minimum number of rows in required bank (default = -1)
 *  -s : source of beam current information (scalers or Epics PV name) (default = scalers)
 *  -v : vetoed trigger bit mask (e.g. 0xFFFFFF7FFFFFFFFF to veto all but the FC trigger (default = 0x0)
 * 
 * Event is filtered if trigger word overlaps with the trigger mask and doesn't
 * overlap with the veto mask. The beam current condition is based by default on 
 * DSC2 scaler readouts from RUN::scaler or, alternatively, Epics readouts from 
 * RAW::epics. In the absence of (or in addition to) the current threshold, a 
 * threshold on raw hit multiplicity can be applied selecting bank and minimum 
 * number of rows
 * 
 * @author devita
 */

public class RandomTriggerFilter {
    
    FilterTrigger triggerFilter = null;
    FilterFcup  fcupFilter = null;
    FilterBank  bankFilter = null;
    
    public RandomTriggerFilter(long bits, long veto, double current){
        triggerFilter = new FilterTrigger(bits, veto);
        fcupFilter  = new FilterFcup(current);
        bankFilter  = new FilterBank("", -1);
    }

    public RandomTriggerFilter(long bits, long veto, String bankName, int nRows){
        triggerFilter = new FilterTrigger(bits, veto);
        fcupFilter  = new FilterFcup(-1);
        bankFilter  = new FilterBank(bankName, nRows);
    }

    public RandomTriggerFilter(long bits, long veto, double current, String currentSource, String bankName, int nRows){
        triggerFilter = new FilterTrigger(bits, veto);
        fcupFilter  = new FilterFcup(current, currentSource);
        bankFilter  = new FilterBank(bankName, nRows);
    }

    private FilterTrigger getTriggerFilter() {
        return triggerFilter;
    }

    private FilterFcup getFcupFilter() {
        return fcupFilter;
    }

    private FilterBank getBankFilter() {
        return bankFilter;
    }

    private void init(HipoReader reader) {
        triggerFilter.init(reader);
        fcupFilter.init(reader);
        bankFilter.init(reader);
    }
    
    public boolean processEvent(Event event) {
        if(triggerFilter.processEvent(event)
           && fcupFilter.processEvent(event)
           && bankFilter.processEvent(event)) {
            return true;
        }
        else {
            return false;
        }
    }
    
    /**
     * Create Json object with filter settings
     * @param selectedBits
     * @param vetoedBits
     * @param minCurrent
     * @param currentSource
     * @param bankName
     * @param nRows
     * @return
     */
    public JsonObject settingsToJson(long selectedBits, long vetoedBits, double minCurrent, String currentSource, String bankName, int nRows){
        
        JsonObject filterData = new JsonObject();
        filterData.add("trigger-mask", Long.toHexString(selectedBits));
        filterData.add("veto-mask", Long.toHexString(vetoedBits));
        filterData.add("current-threshold", minCurrent); 
        filterData.add("current-source", currentSource); 
        filterData.add("bank-name", bankName); 
        filterData.add("bank-size", nRows); 
        JsonObject json = new JsonObject();
        json.add("filter", filterData);
        return json;
    }
    
    /**
     * Create hipo bank with Json string saved as byte array
     * @param writer
     * @param json
     * @return
     */
    public Bank createFilterBank(HipoWriterSorted writer, JsonObject json){
        
        if(writer.getSchemaFactory().hasSchema("RUN::filter")==false) return null;
        String jsonString = json.toString();
        //create bank
        Bank bank = new Bank(writer.getSchemaFactory().getSchema("RUN::filter"), jsonString.length());
        for (int ii=0; ii<jsonString.length(); ii++) {
            bank.putByte("json",ii,(byte)jsonString.charAt(ii));
        }
        return bank;
    }

    public static long readTriggerMask(String mask) {
        if(mask.startsWith("0x")==true){
            mask = mask.substring(2);
        }
        return Long.parseLong(mask.trim(),16);        
    }
    
    public static void main(String[] args){

        DefaultLogger.debug();

        OptionParser parser = new OptionParser("trigger-filter");
        parser.addRequired("-o"    ,"output file");
        parser.addRequired("-b"    ,"trigger bit mask (e.g. 0x0000008000000000 to select the FC trigger");
        parser.setRequiresInputList(false);
        parser.addOption("-v"    ,  "0x0", "vetoed trigger bit mask (e.g. 0xFFFFFF7FFFFFFFFF to veto all but the FC trigger");
        parser.addOption("-c"    ,   "-1", "minimum beam current");
        parser.addOption("-s"    , "DSC2", "source of beam current information (DSC2 scaler or Epics PV name)");
        parser.addOption("-e"    ,     "", "name of required bank, e.g. DC::tdc");
        parser.addOption("-r"    ,   "-1", "minimum number of rows in required bank");
        parser.addOption("-n"    ,   "-1", "maximum number of events to process");
        parser.parse(args);

        List<String> inputList = parser.getInputList();

        if(parser.hasOption("-o")==true&&parser.hasOption("-b")==true){

            String outputFile    = parser.getOption("-o").stringValue();
            long selectedBits    = RandomTriggerFilter.readTriggerMask(parser.getOption("-b").stringValue());            
            long vetoedBits      = RandomTriggerFilter.readTriggerMask(parser.getOption("-v").stringValue());            
            double minCurrent    = parser.getOption("-c").doubleValue();
            String currentSource = parser.getOption("-s").stringValue();
            String bankName      = parser.getOption("-e").stringValue();
            int nRows            = parser.getOption("-r").intValue();            

            int maxEvents        = parser.getOption("-n").intValue();

            if(inputList.isEmpty()==true){
                parser.printUsage();
                System.out.println("\n >>>> error : no input file is specified....\n");
                System.exit(0);
            }
            
            if(selectedBits==0L) {
                parser.printUsage();
                System.out.println("\n >>>> error : invalid trigger bit mask....\n");
                System.exit(0);
            }
            
            int counter = 0;
            int filtered = 0;
            
            DaqScalersSequence chargeSeq = DaqScalersSequence.readSequence(inputList);
            EpicsSequence epicsSeq = EpicsSequence.readSequence(inputList);

            //Writer
            HipoWriterSorted writer = new HipoWriterSorted();
            writer.getSchemaFactory().initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
            writer.setCompressionType(2);
            writer.open(outputFile);

            RandomTriggerFilter filter = new RandomTriggerFilter(selectedBits, vetoedBits, minCurrent, currentSource, bankName, nRows);
            filter.getFcupFilter().setScalerSequence(chargeSeq);
            filter.getFcupFilter().setEpicsSequence(epicsSeq);
            
            ProgressPrintout  progress = new ProgressPrintout();
            for(String inputFile : inputList){

                // write tag-1 events 
                SortedWriterUtils utils = new SortedWriterUtils();
//                utils.writeTag(writer, utils.SCALERTAG, inputFile);
                
                // Reader
                HipoReader reader = new HipoReader();
                reader.setTags(0);
                reader.open(inputFile);
                filter.init(reader);

                // create tag 1 event with trigger filter information
                JsonObject json = filter.settingsToJson(selectedBits, vetoedBits, minCurrent, currentSource, bankName, nRows);
                // write tag-1 event
                Event  tagEvent = new Event();
                tagEvent.write(filter.createFilterBank(writer, json));
                tagEvent.setEventTag(utils.CONFIGTAG);
                writer.addEvent(tagEvent,tagEvent.getEventTag());
                System.out.println("\nAdding tag-1 bank with filter settings...");
                System.out.println(json);

                while (reader.hasNext()) {
                    
                    Event event = new Event();
                    reader.nextEvent(event);
            
                    counter++;
                    
                    if(filter.processEvent(event)) {
                        writer.addEvent(event, event.getEventTag());
                        progress.setAsInteger("filtered", ++filtered);
                    }
                    progress.updateStatus();
                    if(maxEvents>0){
                        if(counter>maxEvents) break;
                    }
                }
                progress.showStatus();
            }
            writer.close();
            
            filter.getFcupFilter().showStats();
            filter.getBankFilter().showStats();
        }
    }
}
