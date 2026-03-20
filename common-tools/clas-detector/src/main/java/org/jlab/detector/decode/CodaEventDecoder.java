package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import org.jlab.coda.jevio.ByteDataTransformer;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EvioNode;
import org.jlab.detector.decode.DetectorDataDgtz.HelicityDecoderData;
import org.jlab.detector.decode.DetectorDataDgtz.SCALERData;
import org.jlab.detector.decode.DetectorDataDgtz.TDCData;
import org.jlab.detector.decode.DetectorDataDgtz.VTPData;
import org.jlab.detector.helicity.HelicityBit;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.evio.EvioTreeBranch;
import org.jlab.utils.data.DataUtils;
import org.jlab.jnp.utils.json.JsonObject;

/**
 *
 * @author gavalian
 */
public class CodaEventDecoder {

    private int   runNumber = 0;
    private int eventNumber = 0;
    private int    unixTime = 0;
    private long  timeStamp = 0L;
    private int timeStampErrors = 0;
    private long    triggerBits = 0;
    private byte helicityLevel3 = HelicityBit.UDF.value();
    private final List<Integer> triggerWords = new ArrayList<>();
    JsonObject  epicsData = new JsonObject();
    TreeMap<Integer,EvioTreeBranch> branchMap = null;

    private int tiMaster = -1; 

    // FIXME:  move this to CCDB, e.g., meanwhile cannot reuse ROC id 
    private static final List<Integer> PCIE_ROCS = Arrays.asList(new Integer[]{78});

    public CodaEventDecoder(){}

    public JsonObject getEpicsData(){
        return this.epicsData;
    }

    public List<Integer> getTriggerWords(){
        return this.triggerWords;
    }

    public int getRunNumber(){
        return this.runNumber;
    }

    public int getEventNumber(){
        return this.eventNumber;
    }

    public int getUnixTime(){
        return this.unixTime;
    }

    public long getTimeStamp() {
        return this.timeStamp;
    }

    public byte getHelicityLevel3() {
        return this.helicityLevel3;
    }

    public long getTriggerBits() {
        return triggerBits;
    }

    public void setTriggerBits(long triggerBits) {
        this.triggerBits = triggerBits;
    }

    /**
     * Load map by crate. 
     *
     * @param event 
     */
    private void cacheBranches(EvioDataEvent event) {
        branchMap = new TreeMap<>();
        for (EvioTreeBranch branch : CodaDecoders.getEventBranches(event))
            if (!branchMap.containsKey(branch.getTag()))
                branchMap.put(branch.getTag(), branch);
    }

    /**
     * returns detector digitized data entries from the event.
     * all branches are analyzed and different types of digitized data
     * is created for each type of ADC and TDC data.
     * @param event
     * @return
     */
    public List<DetectorDataDgtz> getDataEntries(EvioDataEvent event){

        cacheBranches(event);
        
        //int event_size = event.getHandler().getStructure().getByteBuffer().array().length;
        // This had been inserted to accommodate large EVIO events that
        // were unreadable in JEVIO versions prior to 6.2:
        //if(event_size>600*1024){
        //    System.out.println("error: >>>> EVENT SIZE EXCEEDS 600 kB");
        //    return new ArrayList<DetectorDataDgtz>();
        //}
        
        // zero out the trigger bits, but let the others properties inherit
        // from the previous event, in the case where there's no HEAD bank:
        this.setTriggerBits(0);
        List<DetectorDataDgtz>  rawEntries = new ArrayList<>();
        this.setTimeStamp(event);
        for(EvioTreeBranch branch : branchMap.values()){
            List<DetectorDataDgtz>  list = this.getDataEntries(event,branch.getTag());
            if(list != null){
                rawEntries.addAll(list);
            }
        }
        List<DetectorDataDgtz>  tdcEntries = this.getDataEntries_TDC(event);
        rawEntries.addAll(tdcEntries);
        List<DetectorDataDgtz>  vtpEntries = this.getDataEntries_VTP(event);
        rawEntries.addAll(vtpEntries);
        List<DetectorDataDgtz>  scalerEntries = this.getDataEntries_Scalers(event);
        rawEntries.addAll(scalerEntries);

        this.getDataEntries_EPICS(event);

        return rawEntries;
    }

    /**
     * returns list of decoded data in the event for given crate.
     * @param event
     * @param crate
     * @return
     */
    private List<DetectorDataDgtz> getDataEntries(EvioDataEvent event, int crate){
        List<DetectorDataDgtz>   bankEntries = new ArrayList<>();
        EvioTreeBranch cbranch = branchMap.getOrDefault(crate, null);
        if(cbranch == null ) return null;
        for (EvioNode node : cbranch.getNodes()) {
            if (node.getTag() == 57615) {
                this.tiMaster = crate;
                this.readHeaderBank(crate, node, event);
            }
        }
        for(EvioNode node : cbranch.getNodes()){
            switch (node.getTag()) {
                case 57617:
                    //  This is regular integrated pulse mode, used for FTOF/FTCAL/ECAL
                    return CodaDecoders.getDataEntries_57617(crate, node, event);
                case 57603:
                    //  This is regular integrated pulse mode, used for streaming
                    return CodaDecoders.getDataEntries_57603(crate, node, event);
                case 57602:
                    //  This is regular integrated pulse mode, used for FTOF/FTCAL/ECAL
                    return CodaDecoders.getDataEntries_57602(crate, node, event);
                case 57601:
                    //  This is regular integrated pulse mode, used for FTOF/FTCAL/ECAL
                    return CodaDecoders.getDataEntries_57601(crate, node, event);
                case 57627:
                    //  This is regular integrated pulse mode, used for MM
                    return CodaDecoders.getDataEntries_57627(crate, node, event);
                case 57640:
                    //  This is bit-packed pulse mode, used for MM
                    return CodaDecoders.getDataEntries_57640(crate, node, event);
                case 57622:
                    //  This is regular DCRB bank with TDCs only
                    return CodaDecoders.getDataEntries_57622(crate, node, event);
                case 57648:
                    //  This is DCRB bank with TDCs and widths
                    return CodaDecoders.getDataEntries_57648(crate, node, event);
                case 57636:
                    //  RICH TDC data
                    return CodaDecoders.getDataEntries_57636(crate, node, event);
                case 57657:
                    //  ATOF Petiroc TDC data
                    return CodaDecoders.getDataEntries_57657(crate, node, event);
                case 57641:
                    //  RTPC  data decoding
                    return CodaDecoders.getDataEntries_57641(crate, node, event);
                default:
                    break;
            }
        }
        return bankEntries;
    }

    private void setTimeStamp(EvioDataEvent event) {

        long ts = -1;

        List<DetectorDataDgtz> tiEntries = this.getDataEntries_TI(event);
                
        if(tiEntries.size()==1) {
            ts = tiEntries.get(0).getTimeStamp();
        }
        else if(tiEntries.size()>1) {
            // check sychronization
            boolean tiSync=true;
            int  i0 = -1;
            // set reference timestamp from first entry which is not the tiMaster nor PCIE:
            for(int i=0; i<tiEntries.size(); i++) {
                if(tiEntries.get(i).getDescriptor().getCrate() != this.tiMaster) {
                    if (!PCIE_ROCS.contains(tiEntries.get(i).getDescriptor().getCrate())) {
                        i0 = i;
                        break;
                    }
                }   
            }
            for(int i=0; i<tiEntries.size(); i++) {
                long deltaTS = 0;
                long offsetT = 0;
                // Allow/require 5-click offset for PCIE ROCs:
                if( PCIE_ROCS.contains(tiEntries.get(i).getDescriptor().getCrate() )) offsetT = 5;
                // Add 1-click tolerance for "TI master" (FIXME:  this should be an offset too(?)):
                if(tiEntries.get(i).getDescriptor().getCrate()==this.tiMaster) deltaTS = deltaTS + 1;
                if(Math.abs(tiEntries.get(i).getTimeStamp()-offsetT-tiEntries.get(i0).getTimeStamp())>deltaTS) {
                    tiSync=false;
                    if(this.timeStampErrors<100) {
                        System.err.println("WARNING: mismatch in TI time stamps: crate " 
                                        + tiEntries.get(i).getDescriptor().getCrate() + " reports " 
                                        + tiEntries.get(i).getTimeStamp() + " instead of the " + tiEntries.get(i0).getTimeStamp()
                                        + " from crate " + tiEntries.get(i0).getDescriptor().getCrate());
                    }
                    else if(this.timeStampErrors==100) {
                        System.err.println("WARNING: reached the maximum number of timeStamp errors (100), supressing future warnings.");
                    }
                    this.timeStampErrors++;
                }
            }
            if(tiSync) ts = tiEntries.get(i0).getTimeStamp();
        }
        this.timeStamp = ts ;
    }

    private void readHeaderBank(Integer crate, EvioNode node, EvioDataEvent event){

        if(node.getDataTypeObj()==DataType.INT32||node.getDataTypeObj()==DataType.UINT32){
            try {
                int[] intData = ByteDataTransformer.toIntArray(node.getStructureBuffer(true));
                this.runNumber = intData[3];
                this.eventNumber = intData[4];
                if(intData[5]!=0) this.unixTime  = intData[5];
                this.helicityLevel3=HelicityBit.DNE.value();
                if(intData.length>7) {
                    if ( (intData[7] & 0x1) == 0) {
                        this.helicityLevel3=HelicityBit.UDF.value();
                    }
                    else if ((intData[7]>>1 & 0x1) == 0) {
                        this.helicityLevel3=HelicityBit.MINUS.value();
                    }
                    else {
                        this.helicityLevel3=HelicityBit.PLUS.value();
                    }
                }
            } catch (Exception e) {
                this.runNumber = 10;
                this.eventNumber = 1;
            }
        } else {
            System.out.println("[error] can not read header bank");
        }
    }

    private void getDataEntries_EPICS(EvioDataEvent event){
        epicsData = new JsonObject();
        for(EvioTreeBranch branch : branchMap.values()){
            for(EvioNode node : branch.getNodes()){
                if(node.getTag()==57620) {
                    byte[] stringData =  ByteDataTransformer.toByteArray(node.getStructureBuffer(true));
                    String cdata = new String(stringData);
                    String[] vars = cdata.trim().split("\n");
                    for (String var : vars) {
                        String[] fields=var.trim().replaceAll("  "," ").split(" ");
                        if (fields.length != 2) continue;
                        String key = fields[1].trim();
                        String sval = fields[0].trim();
                        try {
                            float fval = Float.parseFloat(sval);
                            epicsData.add(key,fval);
                        }
                        catch (NumberFormatException e) {
                            System.err.println("WARNING:  Ignoring EPICS Bank row:  "+var);
                        }
                    }
                }
            }
        }
    }

    public HelicityDecoderData getDataEntries_HelicityDecoder(EvioDataEvent event){
        HelicityDecoderData data = null;
        for(EvioTreeBranch branch : branchMap.values()){
            for(EvioNode node : branch.getNodes()){
                if(node.getTag()==57651) {
                    
                    int[]  intData  = ByteDataTransformer.toIntArray(node.getStructureBuffer(true));

                    // When there are multiple HelicityDecoder banks in an event, there is a BLKHDR work in the data,
                    // and when there is one HelicityDecoder bank in an event, it is not there. So we need to
                    // detect where the trigger time word is.
                    int i_data_offset = 2;
                    while(i_data_offset<intData.length){
                        // The following idiotic construction is needed because Java doesn't have unsigned ints,
                        // and a right shift on a negative int results in a negative number.
                        int int_test_value = (int) (( ((long)intData[i_data_offset]) & 0x00000000ffffffffL ) >> 27);
                        if(int_test_value == 0x13) break;
                        i_data_offset++;
                    } // find the trigger time word.
                    if(i_data_offset>=intData.length){
                        System.err.println("ERROR:  HelicityDecoder EVIO data is corrupted. Trigger time word not found.");
                        return null;
                    }
                    long  timeStamp = (intData[i_data_offset]&0x00ffffff) + (((long)(intData[i_data_offset+1]&0x00ffffffL))<<24);
                    i_data_offset+=2; // Next word should be "DECODER DATA", with 0x18 in the top 5 bits.
                    try {
                        if(((int) (( ((long)intData[i_data_offset]) & 0x00000000ffffffffL ) >> 27)) != 0x18){
                            System.err.println("ERROR:  HelicityDecoder EVIO data is corrupted.");
                            return null;
                        }
                    }
                    catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("ERROR:  HelicityDecoder EVIO data looks like v2 firmware(?), ignoring it.");
                        return null;
                    }
                    try {
                        int num_data_words = intData[i_data_offset]&0x07ffffff;
                        if(num_data_words < 14){
                            System.err.println("ERROR:  HelicityDecoder EVIO data is corrupted. Not enough data words.");
                            return null;
                        }
                    }
                    catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("ERROR:  HelicityDecoder EVIO data looks like v2 firmware(?), ignoring it.");
                        return null;
                    }
                    i_data_offset ++; // Point to the first word in the data block.
                    int tsettle  = DataUtils.getInteger(intData[i_data_offset+9], 0, 0) > 0 ? 1 : -1;
                    int pattern  = DataUtils.getInteger(intData[i_data_offset+9], 1, 1) > 0 ? 1 : -1;
                    int pair     = DataUtils.getInteger(intData[i_data_offset+9], 2, 2) > 0 ? 1 : -1;
                    int helicity = DataUtils.getInteger(intData[i_data_offset+9], 3, 3) > 0 ? 1 : -1;
                    int start    = DataUtils.getInteger(intData[i_data_offset+9], 4, 4) > 0 ? 1 : -1;
                    int polarity = DataUtils.getInteger(intData[i_data_offset+9], 5, 5) > 0 ? 1 : -1;
                    int count    = DataUtils.getInteger(intData[i_data_offset+9], 8, 11);
                    data = new HelicityDecoderData((byte) helicity, (byte) pair, (byte) pattern);
                    data.setTimestamp(timeStamp);
                    data.setHelicitySeed(intData[i_data_offset]);
                    data.setNTStableRisingEdge(intData[i_data_offset+1]);
                    data.setNTStableFallingEdge(intData[i_data_offset+2]);
                    data.setNPattern(intData[i_data_offset+3]);
                    data.setNPair(intData[i_data_offset+4]);
                    data.setTStableStart(intData[i_data_offset+5]);
                    data.setTStableEnd(intData[i_data_offset+6]);
                    data.setTStableTime(intData[i_data_offset+7]);
                    data.setTSettleTime(intData[i_data_offset+8]);
                    data.setTSettle((byte) tsettle);
                    data.setHelicityPattern((byte) start);
                    data.setPolarity((byte) polarity);
                    data.setPatternPhaseCount((byte) count);
                    data.setPatternWindows(intData[i_data_offset+10]);
                    data.setPairWindows(intData[i_data_offset+11]);
                    data.setHelicityWindows(intData[i_data_offset+12]);
                    data.setHelicityPatternWindows(intData[i_data_offset+13]);
                }
            }
        }
        return data;
    }

    private List<DetectorDataDgtz> getDataEntries_Scalers(EvioDataEvent event){

        List<DetectorDataDgtz> scalerEntries = new ArrayList<>();
        for(int crate : branchMap.keySet()) {
            for(EvioNode node : branchMap.get(crate).getNodes()){
                if(node.getTag()==57637 || node.getTag()==57621){
                    int num = node.getNum();
                    int[] intData =  ByteDataTransformer.toIntArray(node.getStructureBuffer(true));
                    for(int loop = 2; loop < intData.length; loop++){
                        int  dataEntry = intData[loop];
                        // Struck Scaler:
                        if(node.getTag()==57637) {
                            int helicity = DataUtils.getInteger(dataEntry, 31, 31);
                            int quartet  = DataUtils.getInteger(dataEntry, 30, 30);
                            int interval = DataUtils.getInteger(dataEntry, 29, 29);
                            int id       = DataUtils.getInteger(dataEntry, 24, 28);
                            long value   = DataUtils.getLongFromInt(DataUtils.getInteger(dataEntry,  0, 23));
                            if(id < 3) {
                                DetectorDataDgtz entry = new DetectorDataDgtz(crate,num,id+32*interval);
                                SCALERData scaler = new SCALERData();
                                scaler.setHelicity((byte) helicity);
                                scaler.setQuartet((byte) quartet);
                                scaler.setValue(value);
                                entry.addSCALER(scaler);
                                scalerEntries.add(entry);
                            }
                        }
                        // DSC2 Scaler:
                        // FIXME:  There's serious channel number mangling here
                        // and inherited in org.jlab.detector.scalers.Dsc2Scaler,
                        // all scaler words should be decoded but aren't, and the
                        // preserved slot number is an arbitrary number from Sergey
                        // and the same for all DSC2s in the crate, instead of being
                        // parsed from the header or assigned manually based on
                        // the data length.
                        else if(node.getTag()==57621 && loop>=5) {

                            final int dataWordIndex = loop-5;
                            final int nChannels = 16;
                            final int type = dataWordIndex / nChannels;

                            // "type" is TRG-/TDC-gated/TRG-/TDC-ungated = 0/1/2/3 
                            if (type < 4) {
                                final int channel = dataWordIndex % nChannels;
                                // The first two channels are the Faraday Cup and SLM.
                                // The third channel is a 1 MHz input clock, which we
                                // now ignore in favor of the scaler's internal clock below.
                                if (channel<2) {
                                    DetectorDataDgtz entry = new DetectorDataDgtz(crate,num,dataWordIndex);
                                    SCALERData scaler = new SCALERData();
                                    scaler.setValue(DataUtils.getLongFromInt(dataEntry));
                                    entry.addSCALER(scaler);
                                    scalerEntries.add(entry);
                                }
                            }

                            // the trailing words contain the scaler's internal
                            // reference clock:
                            else {
                                if (dataWordIndex == 64 || dataWordIndex == 65) {
                                    // Define the mangled, magic channels numbers that were
                                    // previously assigned above to the gated/ungated clock:
                                    final int channel = dataWordIndex == 64 ? 18 : 50;
                                    DetectorDataDgtz entry = new DetectorDataDgtz(crate,num,channel);
                                    SCALERData scaler = new SCALERData();
                                    scaler.setValue(DataUtils.getLongFromInt(dataEntry));
                                    entry.addSCALER(scaler);
                                    scalerEntries.add(entry);
                                }
                            }
                        }
                    }
                }
            }
        }
        return scalerEntries;
    }

    /**
     * decoding bank that contains TI time stamp.
     * @param event
     * @return
     */
    private List<DetectorDataDgtz>  getDataEntries_TI(EvioDataEvent event){

        List<DetectorDataDgtz> tiEntries = new ArrayList<>();
        for(int crate : branchMap.keySet()) {
            for(EvioNode node : branchMap.get(crate).getNodes()){
                if(node.getTag()==57610){
                    long[] longData = ByteDataTransformer.toLongArray(node.getStructureBuffer(true));
                    int[]  intData  = ByteDataTransformer.toIntArray(node.getStructureBuffer(true));
                    long     tStamp = longData[2]&0x0000ffffffffffffL;

		    // Below is endian swap if needed
		    //long    ntStamp = (((long)(intData[5]&0x0000ffffL))<<32) | (intData[4]&0xffffffffL);
		    //System.out.println(longData[2]+" "+tStamp+" "+crate+" "+node.getDataLength());

                    DetectorDataDgtz entry = new DetectorDataDgtz(crate,0,0);
                    entry.setTimeStamp(tStamp);
                    if(node.getDataLength()==4) tiEntries.add(entry);
                    else if(node.getDataLength()==5) { // trigger supervisor crate
                        this.setTriggerBits(intData[6]);
                    }
                    else if(node.getDataLength()==6) { // New format Dec 1 2017 (run 1701)
                        this.setTriggerBits(intData[6]<<16|intData[7]);
                    }
                    else if(node.getDataLength()==7) { // New format Dec 1 2017 (run 1701)
                        long word = (( (long) intData[7])<<32) | (intData[6]&0xffffffffL);
                        this.setTriggerBits(word);
                        this.triggerWords.clear();
                        for(int i=6; i<=8; i++) {
                            this.triggerWords.add(intData[i]);
                        }
                    }
                }
            }
        }

        return tiEntries;
    }

    private List<DetectorDataDgtz> getDataEntries_VTP(EvioDataEvent event){
        List<DetectorDataDgtz> vtpEntries = new ArrayList<>();
        for(int crate : branchMap.keySet()) {
            for(EvioNode node : branchMap.get(crate).getNodes()){
                if(node.getTag()==57634){
                    int[] intData =  ByteDataTransformer.toIntArray(node.getStructureBuffer(true));
                    for(int loop = 0; loop < intData.length; loop++){
                        int  dataEntry = intData[loop];
                        DetectorDataDgtz   entry = new DetectorDataDgtz(crate,0,0);
                        entry.addVTP(new VTPData(dataEntry));
                        vtpEntries.add(entry);
                    }
                }
            }
        }
        return vtpEntries;
    }

    /**
     * reads the TDC values from the bank with tag = 57607, decodes
     * them and returns a list of digitized detector object.
     * @param event
     * @return
     */
    private List<DetectorDataDgtz>  getDataEntries_TDC(EvioDataEvent event){
        List<DetectorDataDgtz> tdcEntries = new ArrayList<>();
        for(int crate : branchMap.keySet()) {
            for(EvioNode node : branchMap.get(crate).getNodes()){
                if(node.getTag()==57607){
                    int[] intData = ByteDataTransformer.toIntArray(node.getStructureBuffer(true));
                    for(int loop = 2; loop < intData.length; loop++){
                        int  dataEntry = intData[loop];
                        int  slot      = DataUtils.getInteger(dataEntry, 27, 31 );
                        int  chan      = DataUtils.getInteger(dataEntry, 19, 25);
                        int  value     = DataUtils.getInteger(dataEntry,  0, 18);
                        DetectorDataDgtz   entry = new DetectorDataDgtz(crate,slot,chan);
                        entry.addTDC(new TDCData(value));
                        tdcEntries.add(entry);
                    }
                }
            }
        }
        return tdcEntries;
    }

    public List<FADCData> getADCEntries(EvioDataEvent event){
        List<FADCData>  entries = new ArrayList<>();
        for(EvioTreeBranch branch : branchMap.values()){
            List<FADCData>  list = this.getADCEntries(event,branch.getTag());
            if (list != null) entries.addAll(list);
        }
        return entries;
    }

    private List<FADCData> getADCEntries(EvioDataEvent event, int crate){
        List<FADCData>  entries = new ArrayList<>();
        EvioTreeBranch cbranch = branchMap.getOrDefault(crate, null);
        if(cbranch == null ) return null;
        for(EvioNode node : cbranch.getNodes()){
            if(node.getTag()==57638){
                return CodaDecoders.getDataEntries_57638(crate, node, event);
            }
        }
        return entries;
    }

    public static void main(String[] args){
        EvioSource reader = new EvioSource();
        reader.open("/Users/devita/clas_004013.evio.1000");
        CodaEventDecoder decoder = new CodaEventDecoder();
        DetectorEventDecoder detectorDecoder = new DetectorEventDecoder();

        int maxEvents = 5000;
        int icounter  = 0;

        while(reader.hasEvent()==true&&icounter<maxEvents){

            EvioDataEvent event = (EvioDataEvent) reader.getNextEvent();
            List<DetectorDataDgtz>  dataSet = decoder.getDataEntries(event);
            detectorDecoder.translate(dataSet);
            detectorDecoder.fitPulses(dataSet);
            icounter++;
        }
        System.out.println("Done...");
    }
}
