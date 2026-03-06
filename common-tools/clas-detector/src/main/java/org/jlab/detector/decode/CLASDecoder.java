package org.jlab.detector.decode;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.base.DetectorDescriptor;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.utils.RCDBProvider.RCDBManager;
import org.jlab.detector.decode.DetectorDataDgtz.HelicityDecoderData;
import org.jlab.detector.helicity.HelicityBit;
import org.jlab.detector.pulse.ModeAHDC;

import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.io.hipo.HipoDataSync;

import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;

import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.system.ClasUtilsFile;

/**
 *
 * @author gavalian
 */
public class CLASDecoder {
       
    // truncate EVIO waveforms longer than this:
    final static int MAX_BANK_WF_LENGTH = 30;

    protected DetectorEventDecoder detectorDecoder = null;
    protected SchemaFactory           schemaFactory = new SchemaFactory();
    private CodaEventDecoder          codaDecoder = null;
    private List<DetectorDataDgtz>       dataList = new ArrayList<>();
    private HipoDataSync                   writer = null;
    private HipoDataEvent               hipoEvent = null;
    private boolean              isRunNumberFixed = false;
    private int                  decoderDebugMode = 0;
    private ModeAHDC                ahdcExtractor = new ModeAHDC();
    private RCDBManager               rcdbManager = new RCDBManager();

    public CLASDecoder(boolean development){
        codaDecoder = new CodaEventDecoder();
        detectorDecoder = new DetectorEventDecoder(development);
        writer = new HipoDataSync();
        hipoEvent = (HipoDataEvent) writer.createEvent();
        String dir = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
        schemaFactory.initFromDirectory(dir);
    }

    public CLASDecoder(){
        codaDecoder = new CodaEventDecoder();
        detectorDecoder = new DetectorEventDecoder();
        writer = new HipoDataSync();
        hipoEvent = (HipoDataEvent) writer.createEvent();
        String dir = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
        schemaFactory.initFromDirectory(dir);
    }

    public SchemaFactory getSchemaFactory(){
        return schemaFactory;
    }

    public void setVariation(String variation) {
        detectorDecoder.setVariation(variation);
    }

    public void setTimestamp(String timestamp) {
        detectorDecoder.setTimestamp(timestamp);
    }

    public void setDebugMode(int mode){
        this.decoderDebugMode = mode;
    }

    public void setRunNumber(int run){
        if(this.isRunNumberFixed==false){
            this.detectorDecoder.setRunNumber(run);
        }
    }

    public void setRunNumber(int run, boolean fixed){
        this.isRunNumberFixed = fixed;
        this.detectorDecoder.setRunNumber(run);
        System.out.println(" SETTING RUN NUMBER TO " + run + " FIXED = " + this.isRunNumberFixed);
    }

    public CodaEventDecoder getCodaEventDecoder() {
        return codaDecoder;
    }

    /**
     * return list of digitized ADC values from internal list
     * @param type detector type
     * @return
     */
    public List<DetectorDataDgtz>  getEntriesADC(DetectorType type){
        return this.getEntriesADC(type, dataList);
    }
    /**
     * returns ADC entries from decoded data for given detector TYPE
     * @param type detector type
     * @param entries digitized data list
     * @return list of ADC's for detector type
     */
    public List<DetectorDataDgtz>  getEntriesADC(DetectorType type,
            List<DetectorDataDgtz> entries){
        List<DetectorDataDgtz>  adc = new ArrayList<>();
        for(DetectorDataDgtz entry : entries){
            if(entry.getDescriptor().getType()==type){
                if(entry.getADCSize()>0&&entry.getTDCSize()==0){
                    adc.add(entry);
                }
            }
        }

        return adc;
    }

    public List<DetectorDataDgtz>  getEntriesTDC(DetectorType type){
        return getEntriesTDC(type,dataList);
    }

    /**
     * returns TDC entries from decoded data for given detector type
     * @param type detector type
     * @param entries digitized data list
     * @return list of ADC's for detector type
     */
    public List<DetectorDataDgtz>  getEntriesTDC(DetectorType type,
            List<DetectorDataDgtz> entries){
        List<DetectorDataDgtz>  tdc = new ArrayList<>();
        for(DetectorDataDgtz entry : entries){
            if(entry.getDescriptor().getType()==type){
                if(entry.getTDCSize()>0&&entry.getADCSize()==0){
                    tdc.add(entry);
                }
            }
        }
        return tdc;
    }

    public List<DetectorDataDgtz>  getEntriesVTP(DetectorType type){
        return getEntriesVTP(type,dataList);
    }
    /**
     * returns VTP entries from decoded data for given detector type
     * @param type detector type
     * @param entries digitized data list
     * @return list of VTP's for detector type
     */
    public List<DetectorDataDgtz>  getEntriesVTP(DetectorType type,
        List<DetectorDataDgtz> entries){
        List<DetectorDataDgtz>  vtp = new ArrayList<>();
        for(DetectorDataDgtz entry : entries){
            if(entry.getDescriptor().getType()==type){
                if(entry.getVTPSize()>0){
                    vtp.add(entry);
                }
            }
        }
        return vtp;
    }

    public List<DetectorDataDgtz>  getEntriesSCALER(DetectorType type){
        return getEntriesSCALER(type,dataList);
    }
    /**
     * returns VTP entries from decoded data for given detector type
     * @param type detector type
     * @param entries digitized data list
     * @return list of VTP's for detector type
     */
    public List<DetectorDataDgtz>  getEntriesSCALER(DetectorType type,
        List<DetectorDataDgtz> entries){
        List<DetectorDataDgtz>  scaler = new ArrayList<>();
        for(DetectorDataDgtz entry : entries){
            if(entry.getDescriptor().getType()==type){
                if(entry.getSCALERSize()>0){
                    scaler.add(entry);
                }
            }
        }
        return scaler;
    }

    public void extractPulses(Event event) {
        ahdcExtractor.update(30, null, event, schemaFactory, "AHDC::wf", "AHDC::adc");
    }

    public Bank getDataBankWF(String name, DetectorType type) {
        List<DetectorDataDgtz> a = this.getEntriesADC(type);
        if (a.isEmpty()) return null;
        Bank b = new Bank(schemaFactory.getSchema(name), a.size());
        for (int i=0; i<a.size(); ++i) {
            b.putByte( 0, i, (byte) a.get(i).getDescriptor().getSector());
            b.putByte( 1, i, (byte) a.get(i).getDescriptor().getLayer());
            b.putShort(2, i, (short) a.get(i).getDescriptor().getComponent());
            b.putByte( 3, i, (byte) a.get(i).getDescriptor().getOrder());
            b.putLong( 4, i, a.get(i).getADCData(0).getTimeStamp());
            b.putInt("time", i, (int)a.get(i).getADCData(0).getTime());
            DetectorDataDgtz.ADCData xxx = a.get(i).getADCData(0);
            for (int j=0; j<xxx.getPulseSize() && j<MAX_BANK_WF_LENGTH; ++j)
                b.putShort(j+5, i, xxx.getPulseValue(j));
        }
        return b;
    }

    public Bank getDataBankADC(String name, DetectorType type){

        List<DetectorDataDgtz> adcDGTZ = this.getEntriesADC(type);
        if (adcDGTZ.isEmpty()) return null;

        Bank adcBANK = new Bank(schemaFactory.getSchema(name), adcDGTZ.size());

        for(int i = 0; i < adcDGTZ.size(); i++){
            adcBANK.putByte( 0, i, (byte) adcDGTZ.get(i).getDescriptor().getSector());
            adcBANK.putByte( 1, i, (byte) adcDGTZ.get(i).getDescriptor().getLayer());
            adcBANK.putShort(2, i, (short) adcDGTZ.get(i).getDescriptor().getComponent());
            adcBANK.putByte( 3, i, (byte) adcDGTZ.get(i).getDescriptor().getOrder());
            adcBANK.putInt(  4, i, adcDGTZ.get(i).getADCData(0).getADC());
            if (type == DetectorType.BMT || type == DetectorType.FMT || type == DetectorType.FTTRK) {
            	adcBANK.putInt( 4, i, adcDGTZ.get(i).getADCData(0).getHeight());
            	adcBANK.putInt( 7, i, adcDGTZ.get(i).getADCData(0).getIntegral());
            	adcBANK.putLong(8, i, adcDGTZ.get(i).getADCData(0).getTimeStamp());
            }
            if(type == DetectorType.BAND) {
                adcBANK.putInt(  5, i, adcDGTZ.get(i).getADCData(0).getHeight());
                adcBANK.putFloat(6, i, (float) adcDGTZ.get(i).getADCData(0).getTime());
                adcBANK.putShort(7, i, (short) adcDGTZ.get(i).getADCData(0).getPedestal());
            }
            else {
                adcBANK.putFloat(5, i, (float) adcDGTZ.get(i).getADCData(0).getTime());
                adcBANK.putShort(6, i, (short) adcDGTZ.get(i).getADCData(0).getPedestal());
            }
            if(type == DetectorType.BST) adcBANK.putLong(7, i, adcDGTZ.get(i).getADCData(0).getTimeStamp());
         }
        return adcBANK;
    }


    public Bank getDataBankTDC(String name, DetectorType type){

        List<DetectorDataDgtz> tdcDGTZ = this.getEntriesTDC(type);
        if (tdcDGTZ.isEmpty()) return null;
        Bank tdcBANK = new Bank(schemaFactory.getSchema(name), tdcDGTZ.size());

        for(int i = 0; i < tdcDGTZ.size(); i++){
            tdcBANK.putByte( 0, i, (byte) tdcDGTZ.get(i).getDescriptor().getSector());
            tdcBANK.putByte( 1, i, (byte) tdcDGTZ.get(i).getDescriptor().getLayer());
            tdcBANK.putShort(2, i, (short) tdcDGTZ.get(i).getDescriptor().getComponent());
            tdcBANK.putByte( 3, i, (byte) (tdcDGTZ.get(i).getDescriptor().getOrder()+tdcDGTZ.get(i).getTDCData(0).getType().getTypeId()));
            tdcBANK.putInt(  4, i, tdcDGTZ.get(i).getTDCData(0).getTime());
            if(type == DetectorType.DC) 
                tdcBANK.putShort(5, i, (short) tdcDGTZ.get(i).getTDCData(0).getToT());
        }
        return tdcBANK;
    }

    public Bank getDataBankTDCPetiroc(String name, DetectorType type){

        List<DetectorDataDgtz> tdcDGTZ = this.getEntriesTDC(type);
        if (tdcDGTZ.isEmpty()) return null;
        Bank tdcBANK = new Bank(schemaFactory.getSchema(name), tdcDGTZ.size());

        for(int i = 0; i < tdcDGTZ.size(); i++){
            tdcBANK.putByte( 0, i, (byte) tdcDGTZ.get(i).getDescriptor().getSector());
            tdcBANK.putByte( 1, i, (byte) tdcDGTZ.get(i).getDescriptor().getLayer());
            tdcBANK.putShort(2, i, (short) tdcDGTZ.get(i).getDescriptor().getComponent());
            tdcBANK.putByte( 3, i, (byte) tdcDGTZ.get(i).getDescriptor().getOrder());
            tdcBANK.putInt(  4, i, tdcDGTZ.get(i).getTDCData(0).getTime());
            tdcBANK.putInt(  5, i, tdcDGTZ.get(i).getTDCData(0).getToT());
            tdcBANK.putLong( 6, i, tdcDGTZ.get(i).getTDCData(0).getTimeStamp());
            tdcBANK.putInt(  7, i, tdcDGTZ.get(i).getTrigger());
        }
        return tdcBANK;
    }


    public Bank getDataBankTimeStamp(String name, DetectorType type) {

        List<DetectorDataDgtz> tdcDGTZ = this.getEntriesTDC(type);
        if (tdcDGTZ.isEmpty()) return null;
        Map<Integer, DetectorDataDgtz> tsMap = new LinkedHashMap<>();
        for(DetectorDataDgtz tdc : tdcDGTZ) {
            DetectorDescriptor desc = tdc.getDescriptor();
            int hash = ((desc.getCrate()<<8)&0xFF00) | (desc.getSlot()&0x00FF);
            if(tsMap.containsKey(hash)) {
                if(tsMap.get(hash).getTimeStamp() != tdc.getTimeStamp()) 
                    System.out.println("WARNING: inconsistent timestamp for DCRB crate/slot " 
                                       + desc.getCrate() + "/" + desc.getSlot());
            }
            else {
                tsMap.put(hash, tdc);
            }
        }
        
        Bank tsBANK = new Bank(schemaFactory.getSchema(name), tsMap.size());

        int i=0;
        for(DetectorDataDgtz tdc : tsMap.values()) {
            tsBANK.putByte(0, i, (byte) tdc.getDescriptor().getCrate());
            tsBANK.putByte(1, i, (byte) tdc.getDescriptor().getSlot());
            tsBANK.putLong(2, i, tdc.getTimeStamp());
            i++;
        }
        return tsBANK;
    }
    
    public Bank getDataBankUndecodedADC(String name, DetectorType type){
        List<DetectorDataDgtz> adcDGTZ = this.getEntriesADC(type);
        if (adcDGTZ.isEmpty()) return null;
        Bank adcBANK = new Bank(schemaFactory.getSchema(name), adcDGTZ.size());

        for(int i = 0; i < adcDGTZ.size(); i++){
            adcBANK.putByte( 0, i, (byte) adcDGTZ.get(i).getDescriptor().getCrate());
            adcBANK.putByte( 1, i, (byte) adcDGTZ.get(i).getDescriptor().getSlot());
            adcBANK.putShort(2, i, (short) adcDGTZ.get(i).getDescriptor().getChannel());
            adcBANK.putInt(  4, i, adcDGTZ.get(i).getADCData(0).getADC());
            adcBANK.putFloat(5, i, (float) adcDGTZ.get(i).getADCData(0).getTime());
            adcBANK.putShort(6, i, (short) adcDGTZ.get(i).getADCData(0).getPedestal());
        }
        return adcBANK;
    }

    public Bank getDataBankUndecodedTDC(String name, DetectorType type){

        List<DetectorDataDgtz> tdcDGTZ = this.getEntriesTDC(type);
        if (tdcDGTZ.isEmpty()) return null;

        Bank tdcBANK = new Bank(schemaFactory.getSchema(name), tdcDGTZ.size());

        for(int i = 0; i < tdcDGTZ.size(); i++){
            tdcBANK.putByte( 0, i, (byte) tdcDGTZ.get(i).getDescriptor().getCrate());
            tdcBANK.putByte( 1, i, (byte) tdcDGTZ.get(i).getDescriptor().getSlot());
            tdcBANK.putShort(2, i, (short) tdcDGTZ.get(i).getDescriptor().getChannel());
            tdcBANK.putInt(  4, i, tdcDGTZ.get(i).getTDCData(0).getTime());
        }
        return tdcBANK;
    }

    public Bank getDataBankUndecodedVTP(String name, DetectorType type){

        List<DetectorDataDgtz> vtpDGTZ = this.getEntriesVTP(type);
        if (vtpDGTZ.isEmpty()) return null;

        Bank vtpBANK = new Bank(schemaFactory.getSchema(name), vtpDGTZ.size());

        for(int i = 0; i < vtpDGTZ.size(); i++){
            vtpBANK.putByte(0, i, (byte) vtpDGTZ.get(i).getDescriptor().getCrate());
            vtpBANK.putInt( 1, i, vtpDGTZ.get(i).getVTPData(0).getWord());
        }
        return vtpBANK;
    }

    public Bank getDataBankUndecodedSCALER(String name, DetectorType type){

        List<DetectorDataDgtz> scalerDGTZ = this.getEntriesSCALER(type);
        if (scalerDGTZ.isEmpty()) return null;

        Bank scalerBANK = new Bank(schemaFactory.getSchema(name), scalerDGTZ.size());

        for(int i = 0; i < scalerDGTZ.size(); i++){
            scalerBANK.putByte( 0, i, (byte) scalerDGTZ.get(i).getDescriptor().getCrate());
            scalerBANK.putByte( 1, i, (byte) scalerDGTZ.get(i).getDescriptor().getSlot());
            scalerBANK.putShort(2, i, (short) scalerDGTZ.get(i).getDescriptor().getChannel());
            scalerBANK.putByte( 3, i, (byte) scalerDGTZ.get(i).getSCALERData(0).getHelicity());
            scalerBANK.putByte( 4, i, (byte) scalerDGTZ.get(i).getSCALERData(0).getQuartet());
            scalerBANK.putLong( 5, i, scalerDGTZ.get(i).getSCALERData(0).getValue());
        }
        return scalerBANK;
    }


    public long getTriggerPhase() {
        long timestamp    = this.codaDecoder.getTimeStamp();
        int  phase_offset = 1;
        return ((timestamp%6)+phase_offset)%6; // TI derived phase correction due to TDC and FADC clock differences
    }

    public Bank createHeaderBank( int nrun, int nevent, Double torus, Double solenoid){

        Bank bank = new Bank(schemaFactory.getSchema("RUN::config"), 1);

        int    localRun = this.codaDecoder.getRunNumber();
        int  localEvent = this.codaDecoder.getEventNumber();
        int   localTime = this.codaDecoder.getUnixTime();
        long  timeStamp = this.codaDecoder.getTimeStamp();
        long triggerBits = this.codaDecoder.getTriggerBits();

        if(nrun>0){
            localRun = nrun;
            localEvent = nevent;
        }

        bank.putInt( 0, 0, localRun);
        bank.putInt( 1, 0, localEvent);
        bank.putInt( 2, 0, localTime);
        bank.putLong(3, 0, triggerBits);
        bank.putLong(4, 0, timeStamp);

        if (torus != null) {
            bank.putFloat(7, 0, torus.floatValue());
        }
        else if (rcdbManager.getTorusScale(localRun) == null) {
            if (localRun > 100) throw new RuntimeException("Error retrieving torus scale from RCDB.");
        }
        else { 
            bank.putFloat(7, 0, rcdbManager.getTorusScale(localRun).floatValue());
        }
        if (solenoid != null) {
            bank.putFloat(8, 0, solenoid.floatValue());
        }
        else if (rcdbManager.getSolenoidScale(localRun) == null) {
            if (localRun > 100) throw new RuntimeException("Error retrieving solenoid scale from RCDB.");
        }
        else { 
            bank.putFloat(8, 0, rcdbManager.getSolenoidScale(localRun).floatValue());
        }
        
        return bank;
    }

    public Bank createOnlineHelicityBank() {
        if (this.codaDecoder.getHelicityLevel3()==HelicityBit.DNE.value()) return null;
        Bank bank = new Bank(schemaFactory.getSchema("HEL::online"), 1);
        byte  helicityL3 = this.codaDecoder.getHelicityLevel3();
        IndexedTable hwpTable = this.detectorDecoder.scalerManager.
                getConstants(this.detectorDecoder.getRunNumber(),"/runcontrol/hwp");
        bank.putByte(0,0, helicityL3);
        bank.putByte(1,0,(byte)(helicityL3*hwpTable.getIntValue("hwp",0,0,0)));
        return bank;
    }

    public Bank createTriggerBank(){
        Bank bank = new Bank(schemaFactory.getSchema("RUN::trigger"), this.codaDecoder.getTriggerWords().size());
        for(int i=0; i<this.codaDecoder.getTriggerWords().size(); i++) {
            bank.putInt(0, i, i+1);
            bank.putInt(1, i, this.codaDecoder.getTriggerWords().get(i));
        }
        return bank;
    }

    public Bank createEpicsBank(){
        if (this.codaDecoder.getEpicsData().isEmpty()==true) return null;
        String json = this.codaDecoder.getEpicsData().toString();
        Bank bank = new Bank(schemaFactory.getSchema("RAW::epics"), json.length());
        for (int ii=0; ii<json.length(); ii++) {
            bank.putByte(0,ii,(byte)json.charAt(ii));
        }
        return bank;
    }

    /**
     * Create the RUN::scaler and HEL::scaler banks
     * Requires:
     *   - RAW::scaler
     *   - fcup/slm/hel/dsc calibrations from CCDB
     *   - event unix time from RUN::config and run start time from RCDB,
     *     or a good clock frequency from CCDB
     * @param event
     * @return 
     */
    public List<Bank> createReconScalerBanks(Event event){
        return DaqScalers.createBanks(detectorDecoder.getRunNumber(),
                schemaFactory, event, detectorDecoder.scalerManager);
    }

    public Bank createBonusBank(){
        List<DetectorDataDgtz> bonusData = this.getEntriesADC(DetectorType.RTPC);
        int totalSize = 0;
        for(int i = 0; i < bonusData.size(); i++){
            short[]  pulse = bonusData.get(i).getADCData(0).getPulseArray();
            totalSize += pulse.length;
        }
       
        if (bonusData.isEmpty()) return null;

        Bank bonusBank = new Bank(schemaFactory.getSchema("RTPC::adc"), totalSize);
        int currentRow = 0;
        for(int i = 0; i < bonusData.size(); i++){
            
            DetectorDataDgtz bonus = bonusData.get(i);
            
            short[] pulses = bonus.getADCData(0).getPulseArray();
            long timestamp = bonus.getADCData(0).getTimeStamp();
            double    time = bonus.getADCData(0).getTime();
            double   coeff = time*120.0;
            
            double   offset1 = 0.0;
            double   offset2 =  (double) (8*(timestamp%8));
            
            for(int k = 0; k < pulses.length; k++){
                
                double pulseTime = coeff + offset1 + offset2 + k*120.0;
                
                bonusBank.putByte( 0, currentRow, (byte) bonus.getDescriptor().getSector());
                bonusBank.putByte( 1, currentRow, (byte) bonus.getDescriptor().getLayer());
                bonusBank.putShort(2, currentRow, (short) bonus.getDescriptor().getComponent());
                bonusBank.putByte( 3, currentRow, (byte) bonus.getDescriptor().getOrder());
                bonusBank.putInt(  4, currentRow, pulses[k]);
                bonusBank.putFloat(5, currentRow, (float) pulseTime);
                bonusBank.putShort(6, currentRow, (short) 0);
                currentRow++;
            }
        }
        return bonusBank;
    }

    public Bank createHelicityDecoderBank(EvioDataEvent event) {
        HelicityDecoderData data = this.codaDecoder.getDataEntries_HelicityDecoder(event);
        if(data!=null) {
            Bank bank = new Bank(schemaFactory.getSchema("HEL::decoder"), 1);
            int i=0;
            bank.putByte(i++, 0, data.getHelicityState().getHelicity().value());
            bank.putByte(i++, 0, data.getHelicityState().getPairSync().value());
            bank.putByte(i++, 0, data.getHelicityState().getPatternSync().value());
            bank.putByte(i++, 0, data.getTSettle().value());
            bank.putByte(i++, 0, data.getHelicityPattern().value());
            bank.putByte(i++, 0, data.getPolarity());
            bank.putByte(i++, 0, data.getPatternPhaseCount());
            bank.putLong(i++, 0, data.getTimestamp());
            bank.putInt(i++,  0, data.getHelicitySeed());
            bank.putInt(i++,  0, data.getNTStableRisingEdge());
            bank.putInt(i++,  0, data.getNTStableFallingEdge());
            bank.putInt(i++,  0, data.getNPattern());
            bank.putInt(i++,  0, data.getNPair());
            bank.putInt(i++,  0, data.getTStableStart());
            bank.putInt(i++,  0, data.getTStableEnd());
            bank.putInt(i++,  0, data.getTStableTime());
            bank.putInt(i++,  0, data.getTSettleTime());
            bank.putInt(i++,  0, data.getPatternWindows());
            bank.putInt(i++,  0, data.getPairWindows());
            bank.putInt(i++,  0, data.getHelicityWindows());
            bank.putInt(i++,  0, data.getHelicityPatternWindows());
            return bank;
        }
        else 
            return null;
    }

    public static Event createTaggedEvent(Event e, Bank runConfig, Bank... banks) {
        Event t = new Event();
        for (Bank b : banks) {
            e.read(b);
            if (b.getRows() > 0) t.write(b);
        }
        if (!t.isEmpty()) {
            e.read(runConfig);
            t.write(runConfig);
        }
        return t;
    }

    public static Event createTaggedEvent(SchemaFactory sf, Event e, String... banks) {
        Bank[] b = new Bank[banks.length];
        for (int i=0; i<banks.length; ++i) {
            b[i] = new Bank(sf.getSchema(banks[i]));
        }
        return createTaggedEvent(e, new Bank(sf.getSchema("RUN::config")), b);
    }

    public Event createTaggedEvent(Event e, String... banks) {
        return createTaggedEvent(schemaFactory, e, banks);
    }

    public static class Config {
        public static final String[] wfBankNames = new String[]{"AHDC::wf"};
        public static final DetectorType[] wfBankTypes = new DetectorType[]{DetectorType.AHDC};
        public static final String[] adcBankNames = new String[]{
            "FTOF::adc","ECAL::adc","FTCAL::adc",
            "FTHODO::adc", "FTTRK::adc",
            "HTCC::adc","BST::adc","CTOF::adc",
            "CND::adc","LTCC::adc","BMT::adc",
            "FMT::adc","HEL::adc","RF::adc",
            "BAND::adc","RASTER::adc"};
        public static final DetectorType[]  adcBankTypes = new DetectorType[]{
            DetectorType.FTOF,DetectorType.ECAL,DetectorType.FTCAL,
            DetectorType.FTHODO,DetectorType.FTTRK,
            DetectorType.HTCC,DetectorType.BST,DetectorType.CTOF,
            DetectorType.CND,DetectorType.LTCC,DetectorType.BMT,
            DetectorType.FMT,DetectorType.HEL,DetectorType.RF,
            DetectorType.BAND, DetectorType.RASTER};
        public static final String[] tdcBankNames = new String[]{
            "FTOF::tdc","ECAL::tdc","DC::tot",
            "HTCC::tdc","LTCC::tdc","CTOF::tdc",
            "CND::tdc","RF::tdc","RICH::tdc",
            "BAND::tdc"};
        public static final DetectorType[] tdcBankTypes = new DetectorType[]{
            DetectorType.FTOF,DetectorType.ECAL,
            DetectorType.DC,DetectorType.HTCC,DetectorType.LTCC,
            DetectorType.CTOF,DetectorType.CND,DetectorType.RF,
            DetectorType.RICH,DetectorType.BAND};
    }
    
    public Event getDataEvent(){

        Event event = new Event();

        for(int i = 0; i < Config.adcBankTypes.length; i++){
            Bank adcBank = getDataBankADC(Config.adcBankNames[i],Config.adcBankTypes[i]);
            if(adcBank!=null){
                if(adcBank.getRows()>0){
                    event.write(adcBank);
                }
            }
        }

        for(int i = 0; i < Config.wfBankTypes.length; i++){
            Bank wfBank = getDataBankWF(Config.wfBankNames[i],Config.wfBankTypes[i]);
            if(wfBank!=null && wfBank.getRows()>0){
                event.write(wfBank);
            }
        }

        for(int i = 0; i < Config.tdcBankTypes.length; i++){
            Bank tdcBank = getDataBankTDC(Config.tdcBankNames[i],Config.tdcBankTypes[i]);
            if(tdcBank!=null){
                if(tdcBank.getRows()>0){
                    event.write(tdcBank);
                }
            }
        }

        try {
            Bank tdcBank = getDataBankTDCPetiroc("ATOF::tdc",DetectorType.ATOF);
            if(tdcBank!=null){
                if(tdcBank.getRows()>0){
                    event.write(tdcBank);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        try {
            Bank tsBank = getDataBankTimeStamp("DC::jitter", DetectorType.DC);
            if(tsBank != null) {
                if(tsBank.getRows()>0) {
                    event.write(tsBank);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        /**
         * Adding un-decoded banks to the event
         */
        try {
            Bank adcBankUD = this.getDataBankUndecodedADC("RAW::adc", DetectorType.UNDEFINED);
            if(adcBankUD!=null){
                if(adcBankUD.getRows()>0){
                    event.write(adcBankUD);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        try {
            Bank tdcBankUD = this.getDataBankUndecodedTDC("RAW::tdc", DetectorType.UNDEFINED);
            if(tdcBankUD!=null){
                if(tdcBankUD.getRows()>0){
                    event.write(tdcBankUD);
                }
            } else {

            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        try {
            Bank vtpBankUD = this.getDataBankUndecodedVTP("RAW::vtp", DetectorType.UNDEFINED);
            if(vtpBankUD!=null){
                if(vtpBankUD.getRows()>0){
                    event.write(vtpBankUD);
                }
            } else {

            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        try {
            Bank scalerBankUD = this.getDataBankUndecodedSCALER("RAW::scaler", DetectorType.UNDEFINED);
            if(scalerBankUD!=null){
                if(scalerBankUD.getRows()>0){
                    event.write(scalerBankUD);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        try {
            Bank bonusBank = this.createBonusBank();
            if(bonusBank!=null){
                if(bonusBank.getRows()>0){
                    event.write(bonusBank);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        return event;
    }

    public void initEvent(DataEvent event){

        if(event instanceof EvioDataEvent){
            EvioDataEvent evioEvent = (EvioDataEvent) event;
            if(evioEvent.getHandler().getStructure()!=null){
                try {

                    dataList = codaDecoder.getDataEntries( (EvioDataEvent) event);
                    
                    List<FADCData> fadcPacked = codaDecoder.getADCEntries((EvioDataEvent) event);

                    if (fadcPacked != null) {
                        //-----------------------------------------------------------------------------
                        // This part reads the BITPACKED FADC data from tag=57638 Format (cmcms)
                        // Then unpacks into Detector Digigitized data, and appends to existing buffer
                        // Modified on 9/5/2018
                        //-----------------------------------------------------------------------------
                        List<DetectorDataDgtz> fadcUnpacked = FADCData.convert(fadcPacked);
                        dataList.addAll(fadcUnpacked);
                    }

                    if(this.decoderDebugMode>0){
                        System.out.println("\n>>>>>>>>> RAW decoded data");
                        for(DetectorDataDgtz data : dataList){
                            System.out.println(data);
                        }
                    }

                    int runNumberCoda = codaDecoder.getRunNumber();
                    this.setRunNumber(runNumberCoda);
                   
                    detectorDecoder.translate(dataList);
                    detectorDecoder.fitPulses(dataList);
                    detectorDecoder.filterTDCs(dataList);

                    if(this.decoderDebugMode>0){
                        System.out.println("\n>>>>>>>>> TRANSLATED data");
                        for(DetectorDataDgtz data : dataList){
                            System.out.println(data);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Event getDecodedEvent(EvioDataEvent rawEvent, int run, int counter, Double torus, Double solenoid) {

        this.initEvent(rawEvent);
        Event decodedEvent = this.getDataEvent();

        Bank header = this.createHeaderBank(run, counter, torus, solenoid);
        if(header!=null) decodedEvent.write(header);

        Bank trigger = this.createTriggerBank();
        if(trigger!=null) decodedEvent.write(trigger);

        Bank onlineHelicity = this.createOnlineHelicityBank();
        if(onlineHelicity!=null) decodedEvent.write(onlineHelicity);

        Bank decodedHelicity = this.createHelicityDecoderBank(rawEvent);
        if (decodedHelicity!=null) decodedEvent.write(decodedHelicity);

        this.extractPulses(decodedEvent);

        Bank epics = createEpicsBank();
        if (epics != null) decodedEvent.write(epics);

        for (Bank b : createReconScalerBanks(decodedEvent))
            decodedEvent.write(b);

        return decodedEvent;
    }
}
