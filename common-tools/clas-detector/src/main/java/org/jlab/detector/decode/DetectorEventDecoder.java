package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jlab.detector.banks.RawBank.OrderType;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.DetectorDataDgtz.ADCData;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author gavalian
 */
public class DetectorEventDecoder {

    ConstantsManager translationManager = new ConstantsManager();
    ConstantsManager fitterManager      = new ConstantsManager();
    ConstantsManager scalerManager      = new ConstantsManager();

    HashSet<DetectorType> keysFilter;
    HashSet<DetectorType> keysMicromega;

    HashMap<DetectorType,String> tableTrans;
    HashMap<DetectorType,String> tableFitter;
    HashMap<DetectorType,IndexedTable> tablesFitter;

    private int runNumber = 10;

    private ExtendedFADCFitter extendedFitter = new ExtendedFADCFitter();
    private MVTFitter mvtFitter = new MVTFitter();

    private TranslationTable translator = new TranslationTable();

    public DetectorEventDecoder(boolean development){
        if(development==true){
            this.initDecoderDev();
        } else {
            this.initDecoder();
        }
    }

    public DetectorEventDecoder(){
        this.initDecoder();
    }

    public DetectorEventDecoder(DetectorEventDecoder d) {
        translationManager = d.translationManager;
        fitterManager = d.fitterManager;
        scalerManager = d.scalerManager;
        initDecoder(false);
    }

    public void setTimestamp(String timestamp) {
        translationManager.setTimeStamp(timestamp);
        fitterManager.setTimeStamp(timestamp);
        scalerManager.setTimeStamp(timestamp);
    }

    public void setVariation(String variation) {
        translationManager.setVariation(variation);
        fitterManager.setVariation(variation);
        scalerManager.setVariation(variation);
    }

    public void setRunNumber(int run){
        if (run != this.runNumber) {
            translator = new TranslationTable();
            tablesFitter = new HashMap<>();
            for (DetectorType t : tableTrans.keySet())
                translator.add(t, translationManager.getConstants(run, tableTrans.get(t)));
            for (DetectorType t: tableFitter.keySet())
                tablesFitter.put(t, fitterManager.getConstants(run, tableFitter.get(t)));
        }
        this.runNumber = run;
    }

    public int getRunNumber() {
        return this.runNumber;
    }

    public float getRcdbTorusScale() {
        return ((Double)this.scalerManager.getRcdbConstant(this.runNumber,"torus_scale").
                getValue()).floatValue();
    }

    public float getRcdbSolenoidScale() {
        return ((Double)this.scalerManager.getRcdbConstant(this.runNumber,"solenoid_scale").
                getValue()).floatValue();
    }

    public DetectorEventDecoder(){
        this.initDecoder();
    }

    public final void initDecoderDev() {

        keysFilter = new HashSet<>();
        keysMicromega= new HashSet<>();
        tableTrans = new HashMap<>();
        tableFitter = new HashMap<>();
        keysFilter.add(DetectorType.DC);

        tableTrans.put(DetectorType.HTCC, "/daq/tt/clasdev/htcc");
        tableTrans.put(DetectorType.BST, "/daq/tt/clasdev/svt");
        tableTrans.put(DetectorType.RTPC, "/daq/tt/clasdev/rtpc");
        translationManager.init(tableTrans.values().stream().collect(Collectors.toList()));

        tableFitter.put(DetectorType.HTCC, "/daq/fadc/clasdev/htcc");
        fitterManager.init(tableFitter.values().stream().collect(Collectors.toList()));
        
        scalerManager.init(Arrays.asList(new String[]{"/runcontrol/fcup","/runcontrol/slm","/runcontrol/hwp",
                                                      "/runcontrol/helicity","/daq/config/scalers/dsc1"}));
    }

    public final void initDecoder() {
        initDecoder(true);
    }

    public final void initDecoder(boolean initializeManagers){

        keysFilter = new HashSet<>();
        keysMicromega= new HashSet<>();
        tableTrans = new HashMap<>();
        tableFitter = new HashMap<>();
        keysFilter.add(DetectorType.DC); 

        keysMicromega.add(DetectorType.BMT);
        keysMicromega.add(DetectorType.FMT);
        keysMicromega.add(DetectorType.FTTRK);    

        tableTrans.put(DetectorType.FTCAL,  "/daq/tt/ftcal");
        tableTrans.put(DetectorType.FTHODO, "/daq/tt/fthodo");
        tableTrans.put(DetectorType.FTTRK,  "/daq/tt/fttrk");
        tableTrans.put(DetectorType.LTCC,   "/daq/tt/ltcc");
        tableTrans.put(DetectorType.ECAL,   "/daq/tt/ec");
        tableTrans.put(DetectorType.FTOF,   "/daq/tt/ftof");
        tableTrans.put(DetectorType.HTCC,   "/daq/tt/htcc");
        tableTrans.put(DetectorType.DC,     "/daq/tt/dc");
        tableTrans.put(DetectorType.CTOF,   "/daq/tt/ctof");
        tableTrans.put(DetectorType.CND,    "/daq/tt/cnd");
        tableTrans.put(DetectorType.BST,    "/daq/tt/svt");
        tableTrans.put(DetectorType.RF,     "/daq/tt/rf");
        tableTrans.put(DetectorType.BMT,    "/daq/tt/bmt");
        tableTrans.put(DetectorType.FMT,    "/daq/tt/fmt");
        tableTrans.put(DetectorType.RICH,   "/daq/tt/rich2");
        tableTrans.put(DetectorType.HEL,    "/daq/tt/hel");
        tableTrans.put(DetectorType.BAND,   "/daq/tt/band");
        tableTrans.put(DetectorType.RTPC,   "/daq/tt/rtpc");
        tableTrans.put(DetectorType.RASTER, "/daq/tt/raster");
        tableTrans.put(DetectorType.ATOF,   "/daq/tt/atof");
        tableTrans.put(DetectorType.AHDC,   "/daq/tt/ahdc");
        translationManager.init(tableTrans.values().stream().collect(Collectors.toList()));

        tableFitter.put(DetectorType.FTCAL,  "/daq/fadc/ftcal");
        tableFitter.put(DetectorType.FTHODO, "/daq/fadc/fthodo");
        tableFitter.put(DetectorType.FTTRK,  "/daq/fadc/fttrk");
        tableFitter.put(DetectorType.FTOF,   "/daq/fadc/ftof");
        tableFitter.put(DetectorType.LTCC,   "/daq/fadc/ltcc");
        tableFitter.put(DetectorType.ECAL,   "/daq/fadc/ec");
        tableFitter.put(DetectorType.HTCC,   "/daq/fadc/htcc");
        tableFitter.put(DetectorType.CTOF,   "/daq/fadc/ctof");
        tableFitter.put(DetectorType.CND,    "/daq/fadc/cnd");
        tableFitter.put(DetectorType.BMT,    "/daq/fadc/bmt");
        tableFitter.put(DetectorType.FMT,    "/daq/fadc/fmt");
        tableFitter.put(DetectorType.HEL,    "/daq/fadc/hel");
        tableFitter.put(DetectorType.RF,     "/daq/fadc/rf");
        tableFitter.put(DetectorType.BAND,   "/daq/fadc/band");
        tableFitter.put(DetectorType.RASTER, "/daq/fadc/raster");
        tableFitter.put(DetectorType.AHDC,   "/daq/fadc/ahdc");
        fitterManager.init(tableFitter.values().stream().collect(Collectors.toList()));
 
        scalerManager.init(Arrays.asList(new String[]{"/runcontrol/fcup",
            "/runcontrol/slm","/runcontrol/hwp","/runcontrol/helicity","/daq/config/scalers/dsc1"}));

        if (initializeManagers) {
            translationManager.init(tablesTrans);
            fitterManager.init(tablesFitter);
            scalerManager.init(Arrays.asList(new String[]{"/runcontrol/fcup","/runcontrol/slm","/runcontrol/hwp",
                                                          "/runcontrol/helicity","/daq/config/scalers/dsc1"}));
            checkTables();
        }

    }

    public void checkTables() {
        List<String> tables = (List)tableTrans.values().stream().collect(Collectors.toList());
        for (int i=0; i<tables.size(); i++) {
            IndexedTable t = translationManager.getConstants(runNumber, tables.get(i));
            for (int j=0; j<i; j++)
                t.conflicts(translationManager.getConstants(runNumber, tables.get(j)));
        }
    }

    /**
     * applies translation table to the digitized data to translate
     * crate,slot channel to sector layer component.
     * @param detectorData
     */
    public void translate(List<DetectorDataDgtz>  detectorData){

        for (DetectorDataDgtz d : detectorData) {

            // Get the hardware indexing for this detector data object:
            long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(d.getDescriptor().getCrate(),
                d.getDescriptor().getSlot(), d.getDescriptor().getChannel());

            if (translator.hasEntryByHash(hash)) {

                // The tanslated detector indexing:
                List<Integer> x = translator.getIntegersByHash(hash);

                // Set the translated detector indexing:
                d.getDescriptor().setSectorLayerComponentOrderType(x.get(0),x.get(1),x.get(2),x.get(3),x.get(4));
                for (int i=0; i<d.getADCSize(); i++) d.getADCData(i).setOrder(x.get(3));
                for (int i=0; i<d.getTDCSize(); i++) d.getTDCData(i).setOrder(x.get(3));
            }
        }
    }

    public void fitPulses(List<DetectorDataDgtz>  detectorData){

        // preload CCDB tables once:
        HashMap<DetectorType,IndexedTable> tables = new HashMap<>();
        for (Map.Entry<DetectorType, String> e : tableFitter.entrySet()) {
            tables.put(e.getKey(), fitterManager.getConstants(runNumber, e.getValue()));
        }

        for(DetectorDataDgtz data : detectorData){
            if (data.getADCSize() == 0) continue;
            int crate    = data.getDescriptor().getCrate();
            int slot     = data.getDescriptor().getSlot();
            int channel  = data.getDescriptor().getChannel();
            long hash    = IndexedTable.DEFAULT_GENERATOR.hashCode(crate,slot,channel);
            long hash0   = IndexedTable.DEFAULT_GENERATOR.hashCode(0,0,0);
            boolean ismm = keysMicromega.contains(data.getDescriptor().getType());

            for (DetectorType type : tableFitter.keySet()) {
                IndexedTable daq = tables.get(type);
                //custom MM fitter
                if (ismm && data.getDescriptor().getType() == type) {
                    short adcOffset = (short) daq.getDoubleValueByHash("adc_offset", hash0);
                    double fineTimeStampResolution = (byte) daq.getDoubleValueByHash("dream_clock", hash0);
                    double samplingTime = (byte) daq.getDoubleValueByHash("sampling_time", hash0);
                    int sparseSample = daq.getIntValueByHash("sparse", hash0);
                    ADCData adc = data.getADCData(0);
                    mvtFitter.fit(adcOffset, fineTimeStampResolution, samplingTime, adc.getPulseArray(), adc.getTimeStamp(), sparseSample);
                    adc.setHeight((short) (mvtFitter.adcMax));
                    adc.setTime((int) (mvtFitter.timeMax));
                    adc.setIntegral((int) (mvtFitter.integral));
                    adc.setTimeStamp(mvtFitter.timestamp);
                    // first one wins:
                    break;
                }
                else if(daq.hasEntryByHash(hash)==true){
                    int nsa = daq.getIntValueByHash("nsa", hash);
                    int nsb = daq.getIntValueByHash("nsb", hash);
                    int tet = daq.getIntValueByHash("tet", hash);
                    int ped = 0;
                    if(data.getDescriptor().getType() == DetectorType.RF && type == DetectorType.RF) {
                        ped = daq.getIntValueByHash("pedestal", hash);
                    }
                    for(int i = 0; i < data.getADCSize(); i++){
                        ADCData adc = data.getADCData(i);
                        if(adc.getPulseSize()>0){
                            try {
                                extendedFitter.fit(nsa, nsb, tet, ped, adc.getPulseArray());
                            } catch (Exception e) {
                                System.out.println(">>>> error : fitting pulse "
                                    +  crate + " / " + slot + " / " + channel);
                            }
                            int adc_corrected = extendedFitter.adc + extendedFitter.ped*(nsa+nsb);
                            adc.setHeight((short) this.extendedFitter.pulsePeakValue);
                            adc.setIntegral(adc_corrected);
                            adc.setTimeWord(this.extendedFitter.t0);
                            adc.setPedestal((short) this.extendedFitter.ped);
                        }
                        data.getADCData(i).setADC(nsa, nsb);
                    }
                    // first one wins:
                    break;
                }
            }
        }
    }

    public void filterTDCs(List<DetectorDataDgtz>  detectorData){
        int maxMultiplicity = 1;
        for(DetectorType type : keysFilter){
            Map<Integer,List<DetectorDataDgtz>> filteredData = new HashMap<>();
            for(DetectorDataDgtz data : detectorData){
                if(data.getDescriptor().getType() == type) {
                    int key = data.getDescriptor().getHashCode();
                    if(!filteredData.containsKey(key))
                        filteredData.put(key, new ArrayList<>());
                    filteredData.get(key).add(data);
                }
            }
            for(int key : filteredData.keySet()) {
                filteredData.get(key).sort(new TDCComparator());
                if(filteredData.get(key).size()>maxMultiplicity) 
                    for(int i=maxMultiplicity; i<filteredData.get(key).size(); i++)
                        filteredData.get(key).get(i).getTDCData(0).setType(OrderType.DECREMOVED);
            }
        }
    }
    
    class TDCComparator implements Comparator<DetectorDataDgtz> { 
  
        // override the compare() method 
        @Override
        public int compare(DetectorDataDgtz s1, DetectorDataDgtz s2) 
        {
            if(s1.getTDCSize()>0 && s2.getTDCSize()>0) {
                if (s1.getTDCData(0).getTime() < s2.getTDCData(0).getTime())
                    return -1;
                else if (s1.getTDCData(0).getTime() > s2.getTDCData(0).getTime())
                    return 1;
                else
                    return 0;
            }
            else if(s1.getTDCSize()>0)
                return 1;
            else if(s2.getTDCSize()>0)
                return -1;
            else
                return 0;
        } 
    }
}
