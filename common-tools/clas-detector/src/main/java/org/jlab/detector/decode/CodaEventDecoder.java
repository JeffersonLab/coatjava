package org.jlab.detector.decode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.coda.jevio.ByteDataTransformer;
import org.jlab.coda.jevio.CompositeData;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EvioException;
import org.jlab.coda.jevio.EvioNode;
import org.jlab.detector.decode.DetectorDataDgtz.ADCData;
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
    private List<Integer> triggerWords = new ArrayList<>();
    JsonObject  epicsData = new JsonObject();

    private final long timeStampTolerance = 0L;
    private int tiMaster = -1; 
            
    public CodaEventDecoder(){

    }
    /**
     * returns detector digitized data entries from the event.
     * all branches are analyzed and different types of digitized data
     * is created for each type of ADC and TDC data.
     * @param event
     * @return
     */
    public List<DetectorDataDgtz> getDataEntries(EvioDataEvent event){
        
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

        List<DetectorDataDgtz>  rawEntries = new ArrayList<DetectorDataDgtz>();
        List<EvioTreeBranch> branches = this.getEventBranches(event);
        this.setTimeStamp(event);
        for(EvioTreeBranch branch : branches){
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
        this.getDataEntries_HelicityDecoder(event);


        return rawEntries;
    }

    public JsonObject getEpicsData(){
        return this.epicsData;
    }

    public List<Integer> getTriggerWords(){
        return this.triggerWords;
    }

    private void printByteBuffer(ByteBuffer buffer, int max, int columns){
        int n = max;
        if(buffer.capacity()<max) n = buffer.capacity();
        StringBuilder str = new StringBuilder();
        for(int i = 0 ; i < n; i++){
            str.append(String.format("%02X ", buffer.get(i)));
            if( (i+1)%columns==0 ) str.append("\n");
        }
        System.out.println(str.toString());
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

    public void setTimeStamp(EvioDataEvent event) {

        long ts = -1;

        List<DetectorDataDgtz> tiEntries = this.getDataEntries_TI(event);
                
        if(tiEntries.size()==1) {
            ts = tiEntries.get(0).getTimeStamp();
        }
        else if(tiEntries.size()>1) {
            // check sychronization
            boolean tiSync=true;
            int  i0 = -1;
            // set reference timestamp from first entry which is not the tiMaster
            for(int i=0; i<tiEntries.size(); i++) {
                if(tiEntries.get(i).getDescriptor().getCrate()!=this.tiMaster) {
                    i0 = i;
                    break;
                }   
            }
            for(int i=0; i<tiEntries.size(); i++) {
                long deltaTS = this.timeStampTolerance;       
                if(tiEntries.get(i).getDescriptor().getCrate()==this.tiMaster) deltaTS = deltaTS + 1;  // add 1 click tolerance for tiMaster
                if(Math.abs(tiEntries.get(i).getTimeStamp()-tiEntries.get(i0).getTimeStamp())>deltaTS) {
                    tiSync=false;
                    if(this.timeStampErrors<100) {
                        System.err.println("WARNING: mismatch in TI time stamps: crate " 
                                        + tiEntries.get(i).getDescriptor().getCrate() + " reports " 
                                        + tiEntries.get(i).getTimeStamp() + " instead of the " + ts
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

    public long getTriggerBits() {
        return triggerBits;
    }

    public void setTriggerBits(long triggerBits) {
        this.triggerBits = triggerBits;
    }

    public List<FADCData> getADCEntries(EvioDataEvent event){
        List<FADCData>  entries = new ArrayList<>();
        List<EvioTreeBranch> branches = this.getEventBranches(event);
        for(EvioTreeBranch branch : branches){
            List<FADCData>  list = this.getADCEntries(event,branch.getTag());
            if(list != null){
                entries.addAll(list);
            }
        }
        return entries;
    }

    public List<FADCData> getADCEntries(EvioDataEvent event, int crate){
        List<FADCData>  entries = new ArrayList<>();

        List<EvioTreeBranch> branches = this.getEventBranches(event);
        EvioTreeBranch cbranch = this.getEventBranch(branches, crate);

        if(cbranch == null ) return null;

        for(EvioNode node : cbranch.getNodes()){
            if(node.getTag()==57638){
                return this.getDataEntries_57638(crate, node, event);
            }
        }

        return entries;
    }

    public List<FADCData> getADCEntries(EvioDataEvent event, int crate, int tagid){

        List<FADCData>  adc = new ArrayList<>();
        List<EvioTreeBranch> branches = this.getEventBranches(event);

        EvioTreeBranch cbranch = this.getEventBranch(branches, crate);
        if(cbranch == null ) return null;

        for(EvioNode node : cbranch.getNodes()){
           if(node.getTag()==tagid){
                //  This is regular integrated pulse mode, used for FTOF
                // FTCAL and EC/PCAL
                return this.getADCEntries_Tag(crate, node, event,tagid);
            }
        }
        return adc;
    }

    /**
     * returns list of decoded data in the event for given crate.
     * @param event
     * @param crate
     * @return
     */
    public List<DetectorDataDgtz> getDataEntries(EvioDataEvent event, int crate){

        List<EvioTreeBranch> branches = this.getEventBranches(event);
        List<DetectorDataDgtz>   bankEntries = new ArrayList<>();

        EvioTreeBranch cbranch = this.getEventBranch(branches, crate);
        if(cbranch == null ) return null;

        for (EvioNode node : cbranch.getNodes()) {
            if (node.getTag() == 57615) {
                //  This is regular integrated pulse mode, used for FTOF
                // FTCAL and EC/PCAL
                this.tiMaster = crate;
                this.readHeaderBank(crate, node, event);
            }
        }
        for(EvioNode node : cbranch.getNodes()){

            if(node.getTag()==57617){
                //  This is regular integrated pulse mode, used for FTOF
                // FTCAL and EC/PCAL
                return this.getDataEntries_57617(crate, node, event);
            }
            else if(node.getTag()==57602){
                //  This is regular integrated pulse mode, used for FTOF
                // FTCAL and EC/PCAL
                return this.getDataEntries_57602(crate, node, event);
            }
            else if(node.getTag()==57601){
                //  This is regular integrated pulse mode, used for FTOF
                // FTCAL and EC/PCAL
                return this.getDataEntries_57601(crate, node, event);
            }
            else if(node.getTag()==57627){
                //  This is regular integrated pulse mode, used for MM
                return this.getDataEntries_57627(crate, node, event);
            }
            else if(node.getTag()==57640){
                //  This is bit-packed pulse mode, used for MM
                return this.getDataEntries_57640(crate, node, event);
            }
            else if(node.getTag()==57622){
                //  This is regular integrated pulse mode, used for FTOF
                // FTCAL and EC/PCAL
                return this.getDataEntries_57622(crate, node, event);
            }
            else if(node.getTag()==57636){
                //  RICH TDC data
                return this.getDataEntries_57636(crate, node, event);
            } else if(node.getTag()==57641){
                //  RTPC  data decoding
                return this.getDataEntries_57641(crate, node, event);
            }
        }
        return bankEntries;
    }

    /**
     * Returns an array of the branches in the event.
     * @param event
     * @return
     */
    public List<EvioTreeBranch>  getEventBranches(EvioDataEvent event){
        ArrayList<EvioTreeBranch>  branches = new ArrayList<>();
        try {

            List<EvioNode>  eventNodes = event.getStructureHandler().getNodes();
            if(eventNodes==null){
                return branches;
            }

            for(EvioNode node : eventNodes){
                EvioTreeBranch eBranch = new EvioTreeBranch(node.getTag(),node.getNum());
                List<EvioNode>  childNodes = node.getChildNodes();
                if(childNodes!=null){
                    for(EvioNode child : childNodes){
                        eBranch.addNode(child);
                    }
                    branches.add(eBranch);
                }
            }

        } catch (EvioException ex) {
            Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return branches;
    }
    /**
     * returns branch with with given tag
     * @param branches
     * @param tag
     * @return
     */
    public EvioTreeBranch  getEventBranch(List<EvioTreeBranch> branches, int tag){
        for(EvioTreeBranch branch : branches){
            if(branch.getTag()==tag) return branch;
        }
        return null;
    }

    public void readHeaderBank(Integer crate, EvioNode node, EvioDataEvent event){

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

    /**
     * SVT decoding
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public ArrayList<DetectorDataDgtz>  getDataEntries_57617(Integer crate, EvioNode node, EvioDataEvent event){

        ArrayList<DetectorDataDgtz>  rawdata = new ArrayList<>();

        if(node.getTag()==57617){
            try {
                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());
                List<Object>   cdataitems = compData.getItems();
                int  totalSize = cdataitems.size();
                int  position  = 0;
                while( (position + 4) < totalSize){
                    Byte    slot = (Byte)     cdataitems.get(position);
                    //Integer trig = (Integer)  cdataitems.get(position+1);
                    Long    time = (Long)     cdataitems.get(position+2);
                    Integer nchannels = (Integer) cdataitems.get(position+3);
                    int counter  = 0;
                    position = position + 4;
                    while(counter<nchannels){
                        Byte   half    = (Byte) cdataitems.get(position);
                        Byte   channel = (Byte) cdataitems.get(position+1);
                        Byte   tdcbyte = (Byte) cdataitems.get(position+2);
                        Short  tdc     = DataUtils.getShortFromByte(tdcbyte);
                        Byte   adcbyte = (Byte)  cdataitems.get(position+3);

                        // regular FSSR data entry
                        int halfWord = DataUtils.getIntFromByte(half);
                        int   chipID = DataUtils.getInteger(halfWord, 0, 2);
                        int   halfID = DataUtils.getInteger(halfWord, 3, 3);
                        int   adc    = adcbyte;
                        //Integer channelKey = ((half<<8) | (channel & 0xff));

                        // TDC data entry
                        if(half == -128) {
                            halfWord   = DataUtils.getIntFromByte(channel);
                            halfID     = DataUtils.getInteger(halfWord, 2, 2);
                            chipID     = DataUtils.getInteger(halfWord, 0, 1) + 1;
                            channel    = 0;
                            //channelKey = 0;
                            tdc = (short) ((adcbyte<<8) | (tdcbyte & 0xff));
                            adc = -1;
                        }

                        int channelID = halfID*10000 + chipID*1000 + channel;
                        position += 4;
                        counter++;
                        DetectorDataDgtz entry = new DetectorDataDgtz(crate,slot,channelID);
                        ADCData adcData = new ADCData();
                        adcData.setIntegral(adc);
                        adcData.setPedestal( (short) 0);
                        adcData.setADC(0,0);
                        adcData.setTime(tdc);
                        adcData.setTimeStamp(time);
                        entry.addADC(adcData);
                        rawdata.add(entry);
                    }
                }

            } catch (EvioException ex) {
                //Logger.getLogger(EvioRawDataSource.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IndexOutOfBoundsException ex){
                //System.out.println("[ERROR] ----> ERROR DECODING COMPOSITE DATA FOR ONE EVENT");
            }
        }
        return rawdata;
    }

    public List<FADCData>  getADCEntries_Tag(Integer crate, EvioNode node, EvioDataEvent event, int tagid){
        List<FADCData>  entries = new ArrayList<>();
        if(node.getTag()==tagid){
            try {

                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());

                List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                if(cdatatypes.get(3) != DataType.NVALUE){
                    System.err.println("[EvioRawDataSource] ** error ** corrupted "
                    + " bank. tag = " + node.getTag() + " num = " + node.getNum());
                    return null;
                }

                int position = 0;

                while(position<cdatatypes.size()-4){
                    Byte    slot = (Byte)     cdataitems.get(position+0);
                    //Integer trig = (Integer)  cdataitems.get(position+1);
                    //Long    time = (Long)     cdataitems.get(position+2);

                    Integer nchannels = (Integer) cdataitems.get(position+3);
                    position += 4;
                    int counter  = 0;
                    while(counter<nchannels){
                        Byte channel   = (Byte) cdataitems.get(position);
                        Integer length = (Integer) cdataitems.get(position+1);
                        FADCData   bank = new FADCData(crate,slot.intValue(),channel.intValue());
                        short[] shortbuffer = new short[length];
                        for(int loop = 0; loop < length; loop++){
                            Short sample    = (Short) cdataitems.get(position+2+loop);
                            shortbuffer[loop] = sample;
                        }
                        bank.setBuffer(shortbuffer);
                        entries.add(bank);
                        position += 2+length;
                        counter++;
                    }
                }
                return entries;

            } catch (EvioException ex) {
                ByteBuffer     compBuffer = node.getByteData(true);
                System.out.println("Exception in CRATE = " + crate + "  RUN = " + this.runNumber
                + "  EVENT = " + this.eventNumber + " LENGTH = " + compBuffer.array().length);
                this.printByteBuffer(compBuffer, 120, 20);
            }
        }
        return entries;
    }

    /*
    * 	<dictEntry name="FADC250 Window Raw Data (mode 1 packed)" tag="0xe126" num="0" type="composite">
    * <description format="c,m(c,ms)">
    *  c 	"slot number"
    * m	"number of channels fired"
    * c	"channel number"
    * m	"number of shorts in packed array"
    * s	"packed fadc data"
    * </description>
    * </dictEntry>
    */
    public void decodeComposite(ByteBuffer buffer, int offset, List<DataType> ctypes, List<Object> citems){
        int position = offset;
        int length   = buffer.capacity();
        try {
            while(position<(length-3)){
                Short slot = (short) (0x00FF&(buffer.get(position)));
                position++;
                citems.add(slot);
                ctypes.add(DataType.SHORT16);
                Short counter =  (short) (0x00FF&(buffer.get(position)));
                citems.add(counter);
                ctypes.add(DataType.NVALUE);
                position++;

                for(int i = 0; i < counter; i++){
                    Short channel = (short) (0x00FF&(buffer.get(position)));
                    position++;
                    citems.add(channel);
                    ctypes.add(DataType.SHORT16);
                    Short ndata = (short) (0x00FF&(buffer.get(position)));
                    position++;
                    citems.add(ndata);
                    ctypes.add(DataType.NVALUE);
                    for(int b = 0; b < ndata; b++){
                        Short data = buffer.getShort(position);
                        position+=2;
                        citems.add(data);
                        ctypes.add(DataType.SHORT16);
                    }
                }
            }
        } catch (Exception e){
            System.out.println("Exception : Length = " + length + "  position = " + position);
        }
    }

    public List<FADCData>  getDataEntries_57638(Integer crate, EvioNode node, EvioDataEvent event){
        List<FADCData>  entries = new ArrayList<>();
        if(node.getTag()==57638){
            ByteBuffer     compBuffer = node.getByteData(true);
            List<DataType> cdatatypes = new ArrayList<>();
            List<Object>   cdataitems = new ArrayList<>();
            this.decodeComposite(compBuffer, 24, cdatatypes, cdataitems);
            
            int position = 0;
            
            while(position<cdatatypes.size()-3){
                Short       slot = (Short)       cdataitems.get(position+0);
                Short  nchannels =  (Short) cdataitems.get(position+1);
                
                position += 2;
                int     counter = 0;
                while(counter<nchannels){
                    Short   channel = (Short) cdataitems.get(position);
                    Short   length  = (Short) cdataitems.get(position+1);
                    position +=2;
                    short[] shortbuffer = new short[length];
                    for(int loop = 0; loop < length; loop++){
                        Short sample    = (Short) cdataitems.get(position+loop);
                        shortbuffer[loop] = sample;
                    }
                    position+=length;
                    counter++;
                    FADCData data = new FADCData(crate,slot,channel);
                    data.setBuffer(shortbuffer);
                    if(length>18) entries.add(data);
                }
            }
        }
        return entries;
    }
    
    /**
     * decoding bank in Mode 1 - full ADC pulse.
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public List<DetectorDataDgtz>  getDataEntries_57601(Integer crate, EvioNode node, EvioDataEvent event){
        
        ArrayList<DetectorDataDgtz>  entries = new ArrayList<>();
        
        if(node.getTag()==57601){
            try {
                
                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());
                
                List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                if(cdatatypes.get(3) != DataType.NVALUE){
                    System.err.println("[EvioRawDataSource] ** error ** corrupted "
                    + " bank. tag = " + node.getTag() + " num = " + node.getNum());
                    return null;
                }

                int position = 0;

                while(position<cdatatypes.size()-4){
                    Byte    slot = (Byte)     cdataitems.get(position+0);
                    //Integer trig = (Integer)  cdataitems.get(position+1);
                    Long    time = (Long)     cdataitems.get(position+2);

                    Integer nchannels = (Integer) cdataitems.get(position+3);
                    position += 4;
                    int counter  = 0;
                    while(counter<nchannels){
                        Byte channel   = (Byte) cdataitems.get(position);
                        Integer length = (Integer) cdataitems.get(position+1);
                        DetectorDataDgtz bank = new DetectorDataDgtz(crate,slot.intValue(),channel.intValue());

                        short[] shortbuffer = new short[length];
                        for(int loop = 0; loop < length; loop++){
                            Short sample    = (Short) cdataitems.get(position+2+loop);
                            shortbuffer[loop] = sample;
                        }

                        bank.addPulse(shortbuffer);
                        bank.setTimeStamp(time);
                        entries.add(bank);
                        position += 2+length;
                        counter++;
                    }
                }
                return entries;

            } catch (EvioException ex) {
                ByteBuffer     compBuffer = node.getByteData(true);
                System.out.println("Exception in CRATE = " + crate + "  RUN = " + this.runNumber
                + "  EVENT = " + this.eventNumber + " LENGTH = " + compBuffer.array().length);
                this.printByteBuffer(compBuffer, 120, 20);
//                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return entries;
    }

    public List<DetectorDataDgtz>  getDataEntries_57627(Integer crate, EvioNode node, EvioDataEvent event){

        ArrayList<DetectorDataDgtz>  entries = new ArrayList<>();

        if(node.getTag()==57627){
            try {

                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());

                List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                if(cdatatypes.get(3) != DataType.NVALUE){
                    System.err.println("[EvioRawDataSource] ** error ** corrupted "
                    + " bank. tag = " + node.getTag() + " num = " + node.getNum());
                    return null;
                }

                int position = 0;

                while(position<cdatatypes.size()-4){
                    Byte    slot = (Byte)     cdataitems.get(position+0);
                    //Integer trig = (Integer)  cdataitems.get(position+1);
                    Long    time = (Long)     cdataitems.get(position+2);

                    Integer nchannels = (Integer) cdataitems.get(position+3);
                    position += 4;
                    int counter  = 0;
                    while(counter<nchannels){

                        Short channel   = (Short) cdataitems.get(position);
                        Integer length = (Integer) cdataitems.get(position+1);
                        DetectorDataDgtz bank = new DetectorDataDgtz(crate,slot.intValue(),channel.intValue());

                        short[] shortbuffer = new short[length];
                        for(int loop = 0; loop < length; loop++){
                            Short sample    = (Short) cdataitems.get(position+2+loop);
                            shortbuffer[loop] = sample;
                        }
                        //Added pulse fitting for MMs
                        ADCData adcData = new ADCData();
                        adcData.setTimeStamp(time);
                        adcData.setPulse(shortbuffer);
                        bank.addADC(adcData);
                        entries.add(bank);
                        position += 2+length;
                        counter++;
                    }
                }
                return entries;

            } catch (EvioException ex) {
                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return entries;
    }

    /**
     * Decoding MicroMegas Packed Data
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public List<DetectorDataDgtz>  getDataEntries_57640(Integer crate, EvioNode node, EvioDataEvent event){
        // Micromegas packed data
        // ----------------------

        ArrayList<DetectorDataDgtz>  entries = new ArrayList<>();
        if(node.getTag()==57640){
            try {
                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());

                List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                int jdata = 0;  // item counter
                for( int i = 0 ; i < cdatatypes.size();  ) { // loop over data types

                	Byte CRATE      =  (Byte)cdataitems.get( jdata++ ); i++;
                	Integer EV_ID   = (Integer)cdataitems.get( jdata++ ); i++;
                	Long TIMESTAMP  =  (Long)cdataitems.get( jdata++ ); i++;
                	Short nChannels =  (Short)cdataitems.get( jdata++ ); i++;

                	for( int ch=0; ch<nChannels; ch++ ) {

                    	Short CHANNEL = (Short)cdataitems.get( jdata++ ); i++;
                    	int nBytes = (Byte)cdataitems.get( jdata++ ); i++;

                    	DetectorDataDgtz bank = new DetectorDataDgtz(crate,CRATE.intValue(),CHANNEL.intValue());

                    	int nSamples = nBytes*8/12;
                    	short[] samples = new short[ nSamples ];

                    	int s = 0;
                    	for( int b=0;b<nBytes;b++ ) {
                    		short data = (short)((byte)cdataitems.get( jdata++ )&0xFF);

                    		s = (int)Math.floor( b * 8./12. );
                    		if( b%3 != 1) {
                    			samples[s] += (short)data;
                    		}
                    		else {
                    			samples[s] += (data&0x000F)<<8;
                    			if( s+1 < nSamples ) samples[s+1] += ((data&0x00F0)>>4)<<8;
                    		}

                    	}
                    	i++;

                      ADCData adcData = new ADCData();
                      adcData.setTimeStamp(TIMESTAMP);
                      adcData.setPulse(samples);
                      bank.addADC(adcData);
                      entries.add(bank);
                	} // end loop on channels
                } // end loop on data types
                return entries;

            } catch (EvioException ex) {
                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return entries;
    }

        /**
     * Decoding MicroMegas Packed Data
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public List<DetectorDataDgtz>  getDataEntries_57641(Integer crate, EvioNode node, EvioDataEvent event){
        // Micromegas packed data
        // ----------------------

        ArrayList<DetectorDataDgtz>  entries = new ArrayList<>();
        if(node.getTag()==57641){
            try {
                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());

                List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                int jdata = 0;  // item counter
                for( int i = 0 ; i < cdatatypes.size();  ) { // loop over data types

                	Byte SLOT       =  (Byte)cdataitems.get( jdata++ ); i++;
                	Integer EV_ID   =  (Integer)cdataitems.get( jdata++ ); i++;
                	Long TIMESTAMP  =  (Long)cdataitems.get( jdata++ ); i++;
                	Short nChannels =  (Short)cdataitems.get( jdata++ ); i++;

                	for( int ch=0; ch<nChannels; ch++ ) {
                    	Short CHANNEL = (Short)cdataitems.get( jdata++ ); i++;


                        int nPulses = (Byte)cdataitems.get( jdata++ ); i++;
                        for(int np = 0; np < nPulses; np++){

                            int firstChannel = (Byte) cdataitems.get( jdata++ ); i++;

                            int nBytes = (Byte)cdataitems.get( jdata++ ); i++;

                            DetectorDataDgtz bank = new DetectorDataDgtz(crate,SLOT.intValue(),CHANNEL.intValue());

                            int nSamples = nBytes*8/12;
                            short[] samples = new short[ nSamples ];

                            int s = 0;
                            for( int b=0;b<nBytes;b++ ) {
                                short data = (short)((byte)cdataitems.get( jdata++ )&0xFF);

                                s = (int)Math.floor( b * 8./12. );
                                if( b%3 != 1) {
                                    samples[s] += (short)data;
                                }
                                else {
                                    samples[s] += (data&0x000F)<<8;
                                    if( s+1 < nSamples ) samples[s+1] += ((data&0x00F0)>>4)<<8;
                                }
                            }
                            i++;

                            ADCData adcData = new ADCData();
                            adcData.setTimeStamp(TIMESTAMP);
                            adcData.setPulse(samples);
                            adcData.setTime(firstChannel);
                            bank.addADC(adcData);
                            
                            entries.add(bank);
                        }
                    } // end loop on channels
                } // end loop on data types
                return entries;

            } catch (EvioException ex) {
                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return entries;
    }

    /**
     * Decoding MODE 7 data. for given crate.
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public List<DetectorDataDgtz>  getDataEntries_57602(Integer crate, EvioNode node, EvioDataEvent event){
        List<DetectorDataDgtz>  entries = new ArrayList<>();
        if(node.getTag()==57602){
            try {
                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());

                List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                if(cdatatypes.get(3) != DataType.NVALUE){
                    System.err.println("[EvioRawDataSource] ** error ** corrupted "
                    + " bank. tag = " + node.getTag() + " num = " + node.getNum());
                    return null;
                }

                int position = 0;
                while((position+4)<cdatatypes.size()){

                    Byte    slot = (Byte)     cdataitems.get(position+0);
                    //Integer trig = (Integer)  cdataitems.get(position+1);
                    Long    time = (Long)     cdataitems.get(position+2);

                    Integer nchannels = (Integer) cdataitems.get(position+3);
                    position += 4;
                    int counter  = 0;
                    while(counter<nchannels){
                        Byte channel   = (Byte) cdataitems.get(position);
                        Integer length = (Integer) cdataitems.get(position+1);

                        position += 2;
                        for(int loop = 0; loop < length; loop++){
                            Short tdc    = (Short) cdataitems.get(position);
                            Integer adc  = (Integer) cdataitems.get(position+1);
                            Short pmin   = (Short) cdataitems.get(position+2);
                            Short pmax   = (Short) cdataitems.get(position+3);
                            DetectorDataDgtz  entry = new DetectorDataDgtz(crate,slot,channel);
                            ADCData   adcData = new ADCData();
                            adcData.setIntegral(adc).setTimeWord(tdc).setPedestal(pmin).setHeight(pmax);
                            entry.addADC(adcData);
                            entry.setTimeStamp(time);
                            entries.add(entry);
                            position+=4;
                        }
                        counter++;
                    }
                }
                return entries;
            } catch (EvioException ex) {
                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return entries;
    }

    /**
     * Bank TAG=57622 used for DC (Drift Chambers) TDC values.
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public List<DetectorDataDgtz>  getDataEntries_57622(Integer crate, EvioNode node, EvioDataEvent event){
        List<DetectorDataDgtz>  entries = new ArrayList<>();
        if(node.getTag()==57622){
            try {
                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());
                //List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                int  totalSize = cdataitems.size();
                int  position  = 0;
                while( (position + 4) < totalSize){
                    Byte    slot = (Byte)     cdataitems.get(position);
                    //Integer trig = (Integer)  cdataitems.get(position+1);
                    Long    time = (Long)     cdataitems.get(position+2);
                    Integer nchannels = (Integer) cdataitems.get(position+3);
                    int counter  = 0;
                    position = position + 4;
                    while(counter<nchannels){
                        Byte   channel    = (Byte) cdataitems.get(position);
                        Short  tdc     = (Short) cdataitems.get(position+1);
                        position += 2;
                        counter++;
                        DetectorDataDgtz   entry = new DetectorDataDgtz(crate,slot,channel);
                        entry.addTDC(new TDCData(tdc));
                        entry.setTimeStamp(time);
                        entries.add(entry);
                    }
                }
            } catch (EvioException ex) {
                //Logger.getLogger(EvioRawDataSource.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IndexOutOfBoundsException ex){
                //System.out.println("[ERROR] ----> ERROR DECODING COMPOSITE DATA FOR ONE EVENT");
            }

        }
        return entries;
    }

    /**
     * Bank TAG=57636 used for RICH TDC values
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public List<DetectorDataDgtz>  getDataEntries_57636(Integer crate, EvioNode node, EvioDataEvent event){

        ArrayList<DetectorDataDgtz>  entries = new ArrayList<>();

        if(node.getTag()==57636){
            try {

                ByteBuffer     compBuffer = node.getByteData(true);
                CompositeData  compData = new CompositeData(compBuffer.array(),event.getByteOrder());

                List<DataType> cdatatypes = compData.getTypes();
                List<Object>   cdataitems = compData.getItems();

                if(cdatatypes.get(3) != DataType.NVALUE){
                    System.err.println("[EvioRawDataSource] ** error ** corrupted "
                    + " bank. tag = " + node.getTag() + " num = " + node.getNum());
                    return null;
                }

                int position = 0;
                while(position<cdatatypes.size()-4){
                    Byte    slot = (Byte)     cdataitems.get(position+0);
                    //Integer trig = (Integer)  cdataitems.get(position+1);
                    //Long    time = (Long)     cdataitems.get(position+2);

                    Integer nchannels = (Integer) cdataitems.get(position+3);
                    position += 4;
                    int counter  = 0;

                    while(counter<nchannels){
                        Integer fiber = ((Byte) cdataitems.get(position))&0xFF;
                        Integer channel = ((Byte) cdataitems.get(position+1))&0xFF;
                        Short rawtdc = (Short) cdataitems.get(position+2);
                        int edge = (rawtdc>>15)&0x1;
                        int tdc = rawtdc&0x7FFF;

                        DetectorDataDgtz bank = new DetectorDataDgtz(crate,slot.intValue(),2*(fiber*192+channel)+edge);
                        bank.addTDC(new TDCData(tdc));

                        entries.add(bank);
                        position += 3;
                        counter++;
                    }
                }

                return entries;
            } catch (EvioException ex) {
                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return entries;
    }

    public void getDataEntries_EPICS(EvioDataEvent event){
        epicsData = new JsonObject();
        List<EvioTreeBranch> branches = this.getEventBranches(event);
        for(EvioTreeBranch branch : branches){
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
        List<EvioTreeBranch> branches = this.getEventBranches(event);
        for(EvioTreeBranch branch : branches){
            for(EvioNode node : branch.getNodes()){
                if(node.getTag()==57651) {
                    
                    long[] longData = ByteDataTransformer.toLongArray(node.getStructureBuffer(true));
                    int[]  intData  = ByteDataTransformer.toIntArray(node.getStructureBuffer(true));

//                    // When there are multiple HelicityDecoder banks in an event, there is a BLKHDR work in the data,
//                    // and when there is one HelicityDecoder bank in an event, it is not there. So we need to
//                    // detect where the trigger time word is.
                    int i_data_offset = 2;
                    while(i_data_offset<intData.length && (intData[i_data_offset] >> 27) != 0x13) i_data_offset++;  // find the trigger time word.
                    if(i_data_offset%2 == 1){
                        System.err.println("ERROR:  HelicityDecoder data is corrupted. Trigger word not found.");
                        return null;
                    }
                    long  timeStamp = longData[(int)(i_data_offset/2)]&0x0000ffffffffffffL;
                    i_data_offset+=2; // Next word should be "DECODER DATA", with 0x18 in the top 5 bits.
                    if((intData[i_data_offset] >> 27) != 0x18){
                        System.err.println("ERROR:  HelicityDecoder data is corrupted. DECODER BANK not found.");
                        return null;
                    }
                    int num_data_words = intData[i_data_offset]&0x07ffffff;
                    if(num_data_words < 14){
                        System.err.println("ERROR:  HelicityDecoder data is corrupted. Not enough data words.");
                        return null;
                    }
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

    public List<DetectorDataDgtz> getDataEntries_Scalers(EvioDataEvent event){

        List<DetectorDataDgtz> scalerEntries = new ArrayList<>();
        List<EvioTreeBranch> branches = this.getEventBranches(event);
        for(EvioTreeBranch branch : branches){
            int  crate = branch.getTag();
            for(EvioNode node : branch.getNodes()){
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

    public List<DetectorDataDgtz> getDataEntries_VTP(EvioDataEvent event){

        List<DetectorDataDgtz> vtpEntries = new ArrayList<>();
        List<EvioTreeBranch> branches = this.getEventBranches(event);
        for(EvioTreeBranch branch : branches){
            int  crate = branch.getTag();
            for(EvioNode node : branch.getNodes()){
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
    public List<DetectorDataDgtz>  getDataEntries_TDC(EvioDataEvent event){

        List<DetectorDataDgtz> tdcEntries = new ArrayList<>();
        List<EvioTreeBranch> branches = this.getEventBranches(event);

        for(EvioTreeBranch branch : branches){
            int  crate = branch.getTag();
            EvioTreeBranch cbranch = this.getEventBranch(branches, branch.getTag());
            for(EvioNode node : cbranch.getNodes()){
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


    /**
     * decoding bank that contains TI time stamp.
     * @param event
     * @return
     */
    public List<DetectorDataDgtz>  getDataEntries_TI(EvioDataEvent event){

        List<DetectorDataDgtz> tiEntries = new ArrayList<>();
        List<EvioTreeBranch> branches = this.getEventBranches(event);
        for(EvioTreeBranch branch : branches){
            int  crate = branch.getTag();
            EvioTreeBranch cbranch = this.getEventBranch(branches, branch.getTag());
            for(EvioNode node : cbranch.getNodes()){
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
