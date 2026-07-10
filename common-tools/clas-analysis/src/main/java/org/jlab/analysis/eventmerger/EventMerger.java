package org.jlab.analysis.eventmerger;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.detector.banks.RawBank;
import org.jlab.detector.banks.RawBank.OrderType;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

/**
 * Tool for merging of signal and background events
 *      
 *     Usage : bg-merger -i [signal event file] -o [merged file]  [input1] [input2] ....
 *
 *  Options :
 *       -d : list of detectors, for example "DC,FTOF,HTCC" or "ALL" for all available detectors. Use DC1, DC2 or DC3 to select the DC region (default = ALL)
 *       -l : preserve initial hit order (for compatibility with truth matching, 0-false, 1-true (default = 1)
 *       -n : maximum number of events to process (default = -1)
 *       -r : reuse background events: 0-false, 1-true (default = 1)
 *       -s : suppress double TDC hits on the same component, 0-no suppression, 1-suppression (default = 1)
 *       -t : list of hit OrderTypes to be saved (default = NOMINAL,BGADDED_NOMINAL,BGREMOVED,BGREMOVED_BG,DECREMOVED,DECREMOVED_BG)
 *       -x : background scale factor (default = 1)
 *
 * @author ziegler
 * @author devita
 * 
 * FIXME: event tags are not preserved
 */

public class EventMerger {
   
    private boolean suppressDoubleHits = true;
    private boolean preserveHitOrder = true;
    private EventMergerConstants constants = new EventMergerConstants();
    
    private Map<DetectorType,List<Integer>> detectors;
    private OrderType[] orders;
    
    private SchemaFactory schemaFactory = new SchemaFactory();
    private final Map<DetectorType,List<String>> ADCs = new HashMap<>();                                         
    private final Map<DetectorType,List<String>> TDCs = new HashMap<>();
    
    private List<String> bgFileNames;
    private boolean reuseBgEvents = false;
    private int bgScale = 1;
    private int bgFileIndex = 0;
    private HipoDataSource bgReader;
    
    public EventMerger() {
        detectors = this.getDetectors(DetectorType.DC.getName(), DetectorType.FTOF.getName());
        orders = this.getOrders(OrderType.NOMINAL.name(),OrderType.BGADDED_NOMINAL.name(),OrderType.BGREMOVED.name(),OrderType.BGREMOVED_BG.name());
        printConfiguration();
    }

    public EventMerger(String[] dets, String[] types, boolean dhits, boolean ohits) {
        suppressDoubleHits = dhits;
        preserveHitOrder = ohits;
        detectors = this.getDetectors(dets);
        orders = this.getOrders(types);
        printConfiguration();
    }
    
    public EventMerger(String[] dets, SchemaFactory schema, String[] types, boolean dhits, boolean ohits) {
        suppressDoubleHits = dhits;
        preserveHitOrder = ohits;
        detectors = this.getDetectors(schema, dets);
        orders = this.getOrders(types);
        printConfiguration();
    }
    
    
    private Map<DetectorType,List<Integer>> getDetectors(String... dets) {
        SchemaFactory schema = new SchemaFactory();
        schema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
        return this.getDetectors(schema, dets);
    }

    private Map<DetectorType,List<Integer>> getDetectors(SchemaFactory schema, String... dets) {
        schemaFactory = schema;
        for(String det : EventMergerConstants.ADCDETECTORS) {
            for(String type : EventMergerConstants.ADCBANKTYPES) {
                String bank = DetectorType.getType(det) + "::" + type;
                if(schemaFactory.hasSchema(bank)) {
                    if(!ADCs.containsKey(DetectorType.getType(det)))
                        ADCs.put(DetectorType.getType(det), new ArrayList<>());
                    ADCs.get(DetectorType.getType(det)).add(bank);
                }
            }
        }
        for(String det : EventMergerConstants.TDCDETECTORS) {
            for(String type : EventMergerConstants.TDCBANKTYPES) {
                String bank = DetectorType.getType(det) + "::" + type;
                if(schemaFactory.hasSchema(bank)) {
                    if(!TDCs.containsKey(DetectorType.getType(det)))
                        TDCs.put(DetectorType.getType(det), new ArrayList<>());
                    TDCs.get(DetectorType.getType(det)).add(bank);
                }
            }
        }
        Map<DetectorType,List<Integer>> all = new HashMap<>();
        if(dets.length==1 && dets[0].equals("ALL")) {
            for(DetectorType t : ADCs.keySet()) {
                all.put(t, null);
            }
            for(DetectorType d : TDCs.keySet()) {
                if(!all.containsKey(d)) all.put(d, null);
            }
        }
        else {
            for(String d : dets) {
                String[] dn = d.split("(?<=\\D)(?=\\d)");
                DetectorType type = dn.length>0 ? DetectorType.getType(dn[0]) : DetectorType.UNDEFINED;
                if(type == DetectorType.UNDEFINED) {
                    throw new IllegalArgumentException("Unknown detector type " + type);
                }
                else if(dn.length==2 && type==DetectorType.DC && dn[1].matches("[1-3]+")) {
                    int region = Integer.parseInt(dn[1]);
                    if(!all.containsKey(DetectorType.DC))
                        all.put(DetectorType.DC, new ArrayList<>());
                    for(int il=0; il<12; il++) {
                        int layer = (region-1)*12+il+1;
                        if(!all.get(DetectorType.DC).contains(layer))
                            all.get(DetectorType.DC).add(layer);
                    }
                }
                else {    
                    all.put(type, null);
                }
            }
            for(DetectorType type : all.keySet()) {
                if(all.get(type)!=null)
                    Collections.sort(all.get(type));
            }
        }
        return all;
    }
    
    private OrderType[] getOrders(String... type) {
        try {
            return RawBank.createFilterGroup(type);
        } catch (NoSuchFieldException ex) {
            Logger.getLogger(EventMerger.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(EventMerger.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(EventMerger.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public boolean setBgFiles(List<String> filenames, int scale, boolean reuse) {
        bgFileNames = new ArrayList<>();
        for (String filename : filenames) {
            File f = new File(filename);
            if (!f.exists() || !f.isFile() || !f.canRead()) {
                Logger.getLogger(EventMerger.class.getName()).log(Level.SEVERE,"Background filename {0} invalid.",filename);
                return false;
            }
            Logger.getLogger(EventMerger.class.getName()).log(Level.INFO,"Background files: reading {0}",filename);
            bgFileNames.add(filename);
        }        
        if(scale>0) bgScale = scale;
        reuseBgEvents = reuse;
        return true;
    } 
    
    private boolean openNextFile() {
        if(bgFileIndex == bgFileNames.size()) {
            if (reuseBgEvents) {
                Logger.getLogger(EventMerger.class.getName()).info("Reopening previously used background file");
                bgFileIndex = 0;
            }
            else {
                Logger.getLogger(EventMerger.class.getName()).info("Ran out of background events");
                return false;
            }
        }
        bgReader = new HipoDataSource();
        bgReader.open(bgFileNames.get(bgFileIndex));
        bgFileIndex++;
        return true;
    }

    synchronized public List<DataEvent> getBackgroundEvents(int n) {
        List<DataEvent> events = new ArrayList<>();
        for(int i=0; i<n; i++) {
            if (bgReader==null || !bgReader.hasEvent()) {
                if(!openNextFile())
                    return null;
            }
            events.add(bgReader.getNextEvent());
        }
        return events;
    }
    
    private void printConfiguration() {
        System.out.println();
        System.out.println("Double hits suppression flag set to " + suppressDoubleHits);
        System.out.println("Preserve hit list order flag set to " + preserveHitOrder);
        this.printDetectors();
        this.printOrders();
    }

    private void printDetectors() {
        System.out.print("\nMerging activated for detectors: ");
        for(DetectorType det : detectors.keySet()) {
            System.out.print(det.getName());
            if(detectors.get(det)!=null) {
                System.out.print("(layers: ");
                for(int il=0; il<detectors.get(det).size(); il++) {
                    int layer = detectors.get(det).get(il);
                    if(il<detectors.get(det).size()-1)
                        System.out.print(layer + ",");
                    else
                        System.out.print(layer + ") ");
                }
            }
            else
                System.out.print(" ");
        }
        System.out.print("\n\treading banks: ");
        List<String> banks = new ArrayList<>();
        for(DetectorType det : detectors.keySet()) {
            if(ADCs.get(det)!=null)
                banks.addAll(ADCs.get(det));
            if(TDCs.get(det)!=null)
                banks.addAll(TDCs.get(det));
        }
        if(!banks.isEmpty()) {
            for(int ib=0; ib<banks.size(); ib++) {
                String bank = banks.get(ib);
                System.out.print(bank);
                if(ib<banks.size()-1)
                    System.out.print(", ");
            }
        }
        else
            System.out.print("none");
        System.out.print("\n\twriting banks: ");
        banks.clear();
        for(DetectorType det : detectors.keySet()) {
            if(ADCs.get(det)!=null)
                banks.add(ADCs.get(det).get(0));
            if(TDCs.get(det)!=null)
                banks.add(TDCs.get(det).get(0));
        }
        if(!banks.isEmpty()) {
            for(int ib=0; ib<banks.size(); ib++) {
                String bank = banks.get(ib);
                System.out.print(bank);
                if(ib<banks.size()-1)
                    System.out.print(", ");
            }
        }
        else
            System.out.print("none");
        System.out.println();
    }
    
    private void printOrders() {
        System.out.print("\nSaving hits for the following categories: ");
        for(OrderType order : orders) System.out.print(order.name()+ " ");
        System.out.println("\n");
    }
    
    private void mergeEvents(DataEvent event, List<DataEvent> bgs) {
        
        if(!event.hasBank("RUN::config"))
            return;
        if(bgs.isEmpty() || bgs.size()%2!=0)
            return;
        for(DataEvent bg : bgs)
            if(!bg.hasBank("RUN::config"))
                return;
        
        if(event.hasBank("DC::doca")) event.removeBank("DC::doca");
        
        int nbg = bgs.size()/2;
        List<DataEvent> bg1 = bgs.subList(0, nbg);
        List<DataEvent> bg2 = bgs.subList(nbg, 2*nbg);        
        ADCTDCMerger merger = new ADCTDCMerger(constants, event, bg1, bg2);
        merger.setSuppressDoubleHits(suppressDoubleHits);
        merger.setPreserveHitOrder(preserveHitOrder);
        merger.setSelectedOrders(orders);
        
        for(DetectorType det : detectors.keySet()) {
            
            List<DataBank> banks = new ArrayList<>();
            List<String>   names = new ArrayList<>();
            
            List<Integer> layers = detectors.get(det);
            
            if(ADCs.containsKey(det)) {
                for(String name : ADCs.get(det))
                    names.add(name);
                banks.add(merger.mergeADCs(det, layers, ADCs.get(det))); 
            }
            if(TDCs.containsKey(det)) {
                for(String name : TDCs.get(det))
                    names.add(name);
                banks.add(merger.mergeTDCs(det, layers, TDCs.get(det)));
            }
            if(banks.isEmpty())
                System.out.println("Unknown detector:" + det);
            else {
                event.removeBanks(names.toArray(String[]::new));
                event.appendBanks(banks.toArray(DataBank[]::new));
            }
        }
    }
    
    /**
     * Append merged banks to hipo event
     * 
     * @param event
     * @return 
     */
    public boolean mergeEvents(DataEvent event) {
           
        return this.mergeEvents(event, bgScale);
    }

    
    private boolean mergeEvents(DataEvent event, int scale) {
           
        List<DataEvent> eventsBg = this.getBackgroundEvents(2*scale);
                
        if(eventsBg==null) return false;
                
        this.mergeEvents(event,eventsBg);
        return true;
    }

    public static void main(String[] args)  {

        OptionParser parser = new OptionParser("bg-merger");
        parser.addRequired("-o"    ,"merged file");
        parser.addRequired("-i"    ,"signal event file");
        parser.setRequiresInputList(true);
        parser.addOption("-n"    ,"-1", "maximum number of events to process");
        parser.addOption("-d"    ,"ALL", "list of detectors, for example \"DC,FTOF,HTCC\" or \"ALL\" for all available detectors. Use DC1, DC2 or DC3 to select the DC region");
        parser.addOption("-r"    ,"1", "reuse background events: 0-false, 1-true");
        parser.addOption("-s"    ,"1", "suppress double TDC hits on the same component, 0-no suppression, 1-suppression");
        parser.addOption("-l"    ,"1", "preserve initial hit order (for compatibility with truth matching, 0-false, 1-true");
        parser.addOption("-t"    ,"NOMINAL,BGADDED_NOMINAL,BGREMOVED,BGREMOVED_BG,DECREMOVED,DECREMOVED_BG", "list of hit OrderTypes to be saved");
        parser.addOption("-x"    ,"1", "background scale factor");
        parser.parse(args);
        
        if(parser.hasOption("-i") && parser.hasOption("-o")){

            String dataFile   = parser.getOption("-i").stringValue();
            String outputFile = parser.getOption("-o").stringValue();
            List<String> bgFiles = parser.getInputList();
            
            int     maxEvents   = parser.getOption("-n").intValue();
            String  detectors   = parser.getOption("-d").stringValue();
            String  ordertypes  = parser.getOption("-t").stringValue();
            boolean doubleHits  = (parser.getOption("-s").intValue()==1);
            int     bgScale     = parser.getOption("-x").intValue();
            boolean reuseBG     = (parser.getOption("-r").intValue()==1);
            boolean hitOrder    = (parser.getOption("-l").intValue()==1);
            
            
            // Reader for signal events
            HipoDataSource readerData = new HipoDataSource();
            readerData.open(dataFile);
            
            //Writer
            HipoDataSync writer = readerData.createWriter();
            writer.setCompressionType(2);
            writer.open(outputFile);
            
            EventMerger merger = new EventMerger(detectors.split(","),
                                                 readerData.getReader().getSchemaFactory(),
                                                 ordertypes.split(","),
                                                 doubleHits,hitOrder);
            if(!merger.setBgFiles(bgFiles, bgScale, reuseBG))
                System.exit(1);
                
            int counter = 0;

            ProgressPrintout  progress = new ProgressPrintout();
            while (readerData.hasEvent()) {

                counter++;

                //System.out.println("************************************************************* ");
                DataEvent eventData = readerData.getNextEvent();
                
                if(merger.mergeEvents(eventData))
                    writer.writeEvent(eventData);
                else
                    maxEvents = counter;
                
                progress.updateStatus();
                if(maxEvents>0){
                    if(counter>=maxEvents) break;
                }
            }
            progress.showStatus();
            writer.close();
        }

    }

}
