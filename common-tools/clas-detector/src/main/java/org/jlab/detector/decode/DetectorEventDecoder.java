package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    List<String> tablesTrans  = null;
    List<String> tablesFitter = null;

    List<DetectorType> keysTrans    = null;
    List<DetectorType> keysFitter   = null;
    List<DetectorType> keysFilter   = null;

    private int runNumber = 10;

    private ExtendedFADCFitter extendedFitter = new ExtendedFADCFitter();
    private MVTFitter mvtFitter = new MVTFitter();

    private TranslationTable translator = new TranslationTable();
    
    public DetectorEventDecoder(boolean development){
        if(development==true) this.initDecoderDev();
        else this.initDecoder();
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
            for (int i=0; i<keysTrans.size(); i++)
                translator.add(keysTrans.get(i), translationManager.getConstants(run, tablesTrans.get(i)));
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

    public final void initDecoderDev(){
        keysTrans = Arrays.asList(new DetectorType[]{ DetectorType.HTCC,DetectorType.BST,DetectorType.RTPC} );
        tablesTrans = Arrays.asList(new String[]{ "/daq/tt/clasdev/htcc","/daq/tt/clasdev/svt","/daq/tt/clasdev/rtpc" });
        keysFitter   = Arrays.asList(new DetectorType[]{DetectorType.HTCC});
        tablesFitter = Arrays.asList(new String[]{"/daq/fadc/clasdev/htcc"});
        translationManager.init(tablesTrans);
        fitterManager.init(tablesFitter);
        scalerManager.init(Arrays.asList(new String[]{"/runcontrol/fcup","/runcontrol/slm","/runcontrol/hwp",
                                                      "/runcontrol/helicity","/daq/config/scalers/dsc1"}));
    }

    public final void initDecoder(){

        // Detector translation table
        keysTrans = Arrays.asList(new DetectorType[]{DetectorType.FTCAL,DetectorType.FTHODO,DetectorType.FTTRK,DetectorType.LTCC,DetectorType.ECAL,DetectorType.FTOF,
                                               DetectorType.HTCC,DetectorType.DC,DetectorType.CTOF,DetectorType.CND,DetectorType.BST,DetectorType.RF,DetectorType.BMT,DetectorType.FMT,
                                               DetectorType.RICH,DetectorType.HEL,DetectorType.BAND,DetectorType.RTPC,
                                               DetectorType.RASTER,DetectorType.ATOF,DetectorType.AHDC
        });
        tablesTrans = Arrays.asList(new String[]{
            "/daq/tt/ftcal","/daq/tt/fthodo","/daq/tt/fttrk","/daq/tt/ltcc",
            "/daq/tt/ec","/daq/tt/ftof","/daq/tt/htcc","/daq/tt/dc","/daq/tt/ctof","/daq/tt/cnd","/daq/tt/svt",
            "/daq/tt/rf","/daq/tt/bmt","/daq/tt/fmt","/daq/tt/rich2","/daq/tt/hel","/daq/tt/band","/daq/tt/rtpc",
            "/daq/tt/raster","/daq/tt/atof","/daq/tt/ahdc"
        });
        translationManager.init(tablesTrans);
        
        // ADC waveform fitter translation table
        keysFitter   = Arrays.asList(new DetectorType[]{DetectorType.FTCAL,DetectorType.FTHODO,DetectorType.FTTRK,DetectorType.FTOF,DetectorType.LTCC,
                                                  DetectorType.ECAL,DetectorType.HTCC,DetectorType.CTOF,DetectorType.CND,DetectorType.BMT,
                                                  DetectorType.FMT,DetectorType.HEL,DetectorType.RF,DetectorType.BAND,DetectorType.RASTER,
                                                  DetectorType.AHDC});
        tablesFitter = Arrays.asList(new String[]{
            "/daq/fadc/ftcal","/daq/fadc/fthodo","/daq/config/fttrk","/daq/fadc/ftof","/daq/fadc/ltcc",
            "/daq/fadc/ec", "/daq/fadc/htcc","/daq/fadc/ctof","/daq/fadc/cnd","/daq/config/bmt",
            "/daq/config/fmt","/daq/fadc/hel","/daq/fadc/rf","/daq/fadc/band","/daq/fadc/raster",
            "/daq/config/ahdc"
        });
        fitterManager.init(tablesFitter);

        // Data filter list
        keysFilter   = Arrays.asList(new DetectorType[]{DetectorType.DC});

        scalerManager.init(Arrays.asList(new String[]{"/runcontrol/fcup","/runcontrol/slm","/runcontrol/hwp",
                                                      "/runcontrol/helicity","/daq/config/scalers/dsc1"}));
        
        checkTables();
    }

    public void checkTables() {
        for (int i=0; i<tablesTrans.size(); i++) {
            IndexedTable t = translationManager.getConstants(runNumber, tablesTrans.get(i));
            for (int j=0; j<i; j++)
                t.conflicts(translationManager.getConstants(runNumber, tablesTrans.get(j)));
        }
    }
    
    /**
     * applies translation table to the digitized data to translate
     * crate,slot channel to sector layer component.
     * @param detectorData
     */
    public void translate(List<DetectorDataDgtz> detectorData){

        for (DetectorDataDgtz d : detectorData) {

            // Get the hardware indexing for this detector data object:
            long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(
                d.getDescriptor().getCrate(), d.getDescriptor().getSlot(), d.getDescriptor().getChannel());

            if (translator.hasEntryByHash(hash)) {
                
                // The tanslated detector indexing:
                List<Integer> idx = translator.getIntegersByHash(hash);

                // Set the translated detector indexing:
                d.getDescriptor().setSectorLayerComponentOrderType(idx.get(0), idx.get(1), idx.get(2), idx.get(3), idx.get(4));
                for (int i=0; i<d.getADCSize(); i++) d.getADCData(i).setOrder(idx.get(3));
                for (int i=0; i<d.getTDCSize(); i++) d.getTDCData(i).setOrder(idx.get(3));
            }
        }
    }

    public void fitPulses(List<DetectorDataDgtz>  detectorData){

        // preload CCDB tables once:
        ArrayList<IndexedTable> tables = new ArrayList<>();
        for (String name : tablesFitter) {
            tables.add(fitterManager.getConstants(runNumber, name));
        }

        for(DetectorDataDgtz data : detectorData){
            
            int crate    = data.getDescriptor().getCrate();
            int slot     = data.getDescriptor().getSlot();
            int channel  = data.getDescriptor().getChannel();
            long hash    = IndexedTable.DEFAULT_GENERATOR.hashCode(crate,slot,channel);
            long hash0   = IndexedTable.DEFAULT_GENERATOR.hashCode(0,0,0);
            
            for (int j=0; j<keysFitter.size(); ++j) {
                IndexedTable daq = tables.get(j);
                DetectorType type = keysFitter.get(j);
                //custom MM fitter
            	if( ( type == DetectorType.BMT && data.getDescriptor().getType() == DetectorType.BMT )
                 || ( type == DetectorType.FMT && data.getDescriptor().getType() == DetectorType.FMT )
                 || ( type == DetectorType.FTTRK && data.getDescriptor().getType() == DetectorType.FTTRK ) ){
                    short adcOffset = (short) daq.getDoubleValueByHash("adc_offset", hash0);
                    double fineTimeStampResolution = (byte) daq.getDoubleValueByHash("dream_clock", hash0);
                    double samplingTime = (byte) daq.getDoubleValueByHash("sampling_time", hash0);
                    int sparseSample = daq.getIntValueByHash("sparse", hash0);
                    if (data.getADCSize() > 0) {
                        ADCData adc = data.getADCData(0);
                        mvtFitter.fit(adcOffset, fineTimeStampResolution, samplingTime, adc.getPulseArray(), adc.getTimeStamp(), sparseSample);
                        adc.setHeight((short) (mvtFitter.adcMax));
                        adc.setTime((int) (mvtFitter.timeMax));
                        adc.setIntegral((int) (mvtFitter.integral));
                        adc.setTimeStamp(mvtFitter.timestamp);
                    }
                    break;
                } else if (daq.hasEntryByHash(hash) == true) {
                    int nsa = daq.getIntValueByHash("nsa", hash);
                    int nsb = daq.getIntValueByHash("nsb", hash);
                    int tet = daq.getIntValueByHash("tet", hash);
                    int ped = 0;
                    if (type == DetectorType.RF && data.getDescriptor().getType().getName().equals("RF")) {
                        ped = daq.getIntValueByHash("pedestal", hash);
                    }
                    if (data.getADCSize() > 0) {
                        for (int i = 0; i < data.getADCSize(); i++) {
                            ADCData adc = data.getADCData(i);
                            if (adc.getPulseSize() > 0) {
                                try {
                                    extendedFitter.fit(nsa, nsb, tet, ped, adc.getPulseArray());
                                } catch (Exception e) {
                                    System.out.println(">>>> error : fitting pulse "
                                        + crate + " / " + slot + " / " + channel);
                                }
                                int adc_corrected = extendedFitter.adc + extendedFitter.ped * (nsa + nsb);
                                adc.setHeight((short) this.extendedFitter.pulsePeakValue);
                                adc.setIntegral(adc_corrected);
                                adc.setTimeWord(this.extendedFitter.t0);
                                adc.setPedestal((short) this.extendedFitter.ped);
                            }
                            data.getADCData(i).setADC(nsa, nsb);
                        }
                    }
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
                    break;
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
