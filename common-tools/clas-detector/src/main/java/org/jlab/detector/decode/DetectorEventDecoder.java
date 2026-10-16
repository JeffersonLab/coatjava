package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jlab.detector.banks.RawBank.OrderType;
import org.jlab.detector.base.DetectorDescriptor;
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

    private final HashSet<DetectorType> filterTypes = new HashSet<>();
    private final HashSet<DetectorType> micromegaTypes = new HashSet<>();

    private final HashMap<DetectorType,String> transTableNames = new HashMap<>();
    private final HashMap<DetectorType,String> fitterTableNames = new HashMap<>();

    private final ExtendedFADCFitter extendedFitter = new ExtendedFADCFitter();
    private final MVTFitter mvtFitter = new MVTFitter();
    
    private HashMap<DetectorType,IndexedTable> fitterTables = new HashMap<>();
    private TranslationTable translator = new TranslationTable();

    private int runNumber = 10;

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
            fitterTables = new HashMap<>();
            for (DetectorType t : transTableNames.keySet())
                translator.add(t, translationManager.getConstants(run, transTableNames.get(t)));
            for (DetectorType t: fitterTableNames.keySet())
                fitterTables.put(t, fitterManager.getConstants(run, fitterTableNames.get(t)));
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

    public final void initDecoderDev() {

        filterTypes.add(DetectorType.DC);

        transTableNames.put(DetectorType.HTCC, "/daq/tt/clasdev/htcc");
        transTableNames.put(DetectorType.BST, "/daq/tt/clasdev/svt");
        transTableNames.put(DetectorType.RTPC, "/daq/tt/clasdev/rtpc");

        fitterTableNames.put(DetectorType.HTCC, "/daq/fadc/clasdev/htcc");

        translationManager.init(transTableNames.values().stream().collect(Collectors.toList()));
        fitterManager.init(fitterTableNames.values().stream().collect(Collectors.toList()));
        scalerManager.init("/runcontrol/slm","/runcontrol/hwp","/runcontrol/helicity","/daq/config/scalers/dsc1");
    }

    public final void initDecoder() {
        initDecoder(true);
    }

    public final void initDecoder(boolean initializeManagers){

        filterTypes.add(DetectorType.DC); 

        micromegaTypes.add(DetectorType.BMT);
        micromegaTypes.add(DetectorType.FMT);
        micromegaTypes.add(DetectorType.FTTRK);    

        transTableNames.put(DetectorType.FTCAL,  "/daq/tt/ftcal");
        transTableNames.put(DetectorType.FTHODO, "/daq/tt/fthodo");
        transTableNames.put(DetectorType.FTTRK,  "/daq/tt/fttrk");
        transTableNames.put(DetectorType.LTCC,   "/daq/tt/ltcc");
        transTableNames.put(DetectorType.ECAL,   "/daq/tt/ec");
        transTableNames.put(DetectorType.FTOF,   "/daq/tt/ftof");
        transTableNames.put(DetectorType.HTCC,   "/daq/tt/htcc");
        transTableNames.put(DetectorType.DC,     "/daq/tt/dc");
        transTableNames.put(DetectorType.CTOF,   "/daq/tt/ctof");
        transTableNames.put(DetectorType.CND,    "/daq/tt/cnd");
        transTableNames.put(DetectorType.BST,    "/daq/tt/svt");
        transTableNames.put(DetectorType.RF,     "/daq/tt/rf");
        transTableNames.put(DetectorType.BMT,    "/daq/tt/bmt");
        transTableNames.put(DetectorType.FMT,    "/daq/tt/fmt");
        transTableNames.put(DetectorType.RICH,   "/daq/tt/rich2");
        transTableNames.put(DetectorType.HEL,    "/daq/tt/hel");
        transTableNames.put(DetectorType.BAND,   "/daq/tt/band");
        transTableNames.put(DetectorType.RTPC,   "/daq/tt/rtpc");
        transTableNames.put(DetectorType.RASTER, "/daq/tt/raster");
        transTableNames.put(DetectorType.ATOF,   "/daq/tt/atof");
        transTableNames.put(DetectorType.AHDC,   "/daq/tt/ahdc");

        fitterTableNames.put(DetectorType.FTCAL,  "/daq/fadc/ftcal");
        fitterTableNames.put(DetectorType.FTHODO, "/daq/fadc/fthodo");
        fitterTableNames.put(DetectorType.FTTRK,  "/daq/config/fttrk");
        fitterTableNames.put(DetectorType.FTOF,   "/daq/fadc/ftof");
        fitterTableNames.put(DetectorType.LTCC,   "/daq/fadc/ltcc");
        fitterTableNames.put(DetectorType.ECAL,   "/daq/fadc/ec");
        fitterTableNames.put(DetectorType.HTCC,   "/daq/fadc/htcc");
        fitterTableNames.put(DetectorType.CTOF,   "/daq/fadc/ctof");
        fitterTableNames.put(DetectorType.CND,    "/daq/fadc/cnd");
        fitterTableNames.put(DetectorType.BMT,    "/daq/config/bmt");
        fitterTableNames.put(DetectorType.FMT,    "/daq/config/fmt");
        fitterTableNames.put(DetectorType.HEL,    "/daq/fadc/hel");
        fitterTableNames.put(DetectorType.RF,     "/daq/fadc/rf");
        fitterTableNames.put(DetectorType.BAND,   "/daq/fadc/band");
        fitterTableNames.put(DetectorType.RASTER, "/daq/fadc/raster");
        fitterTableNames.put(DetectorType.AHDC,   "/daq/config/ahdc");
        
        if (initializeManagers) {
            translationManager.init(transTableNames.values().stream().collect(Collectors.toList()));
            fitterManager.init(fitterTableNames.values().stream().collect(Collectors.toList()));
            scalerManager.init("/runcontrol/fcup","/runcontrol/slm","/runcontrol/hwp","/runcontrol/helicity","/daq/config/scalers/dsc1");
            checkTables();
        }
    }

    public void checkTables() {
        List<String> tables = (List)transTableNames.values().stream().collect(Collectors.toList());
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

    private void fitPulses(DetectorDataDgtz data, IndexedTable cfg) {
        final DetectorDescriptor dd = data.getDescriptor();
        final long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(dd.getCrate(), dd.getSlot(), dd.getChannel());
        // Guard re-added (was removed in PR #1323): skip channels with no FADC config entry,
        // otherwise getIntValueByHash returns null and NPEs (e.g. LTCC crate 19 / slot 18 / chan 150).
        if (!cfg.hasEntryByHash(hash)) return;
        final int nsa = cfg.getIntValueByHash("nsa", hash);
        final int nsb = cfg.getIntValueByHash("nsb", hash);
        final int tet = cfg.getIntValueByHash("tet", hash);
        final int ped = dd.getType() == DetectorType.RF ? cfg.getIntValueByHash("pedestal", hash) : 0;
        final int nadc = data.getADCSize();
        for (int i = 0; i < nadc; i++) {
            ADCData adc = data.getADCData(i);
            if(adc.getPulseSize()>0){
                try {
                    extendedFitter.fit(nsa, nsb, tet, ped, adc.getPulseArray());
                } catch (Exception e) {
                    System.err.println(">>>> error : fitting pulse "+dd.getCrate()+
                        " / "+dd.getSlot()+" / "+dd.getChannel());
                }
                adc.setIntegral(extendedFitter.adc + extendedFitter.ped*(nsa+nsb));
                adc.setHeight((short) this.extendedFitter.pulsePeakValue);
                adc.setTimeWord(this.extendedFitter.t0);
                adc.setPedestal((short) this.extendedFitter.ped);
            }
            data.getADCData(i).setADC(nsa, nsb);
        }
    }

    private void fitMicromegaPulses(DetectorDataDgtz data, IndexedTable cfg) {
        final short adcOffset = (short) cfg.getDoubleValueByHash("adc_offset", 0L);
        final double fineTimeStampResolution = (byte) cfg.getDoubleValueByHash("dream_clock", 0L);
        final double samplingTime = (byte) cfg.getDoubleValueByHash("sampling_time", 0L);
        final int sparseSample = cfg.getIntValueByHash("sparse", 0L);
        ADCData adc = data.getADCData(0);
        mvtFitter.fit(adcOffset, fineTimeStampResolution, samplingTime, adc.getPulseArray(), adc.getTimeStamp(), sparseSample);
        adc.setHeight((short) (mvtFitter.adcMax));
        adc.setTime((int) (mvtFitter.timeMax));
        adc.setIntegral((int) (mvtFitter.integral));
        adc.setTimeStamp(mvtFitter.timestamp);
    }

    public void fitPulses(List<DetectorDataDgtz>  detectorData){
        for (DetectorDataDgtz data : detectorData) {
            if (data.getADCSize() == 0) continue;
            final DetectorType type = data.getDescriptor().getType();
            final IndexedTable daqTable = fitterTables.getOrDefault(type,null);
            if (daqTable != null) {
                if (micromegaTypes.contains(type))
                    fitMicromegaPulses(data, daqTable);
                else
                    fitPulses(data, daqTable);
            }
        }
    }

    public void filterTDCs(List<DetectorDataDgtz>  detectorData){
        int maxMultiplicity = 1;
        for(DetectorType type : filterTypes){
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
