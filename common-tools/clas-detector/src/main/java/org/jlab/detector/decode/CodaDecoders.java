package org.jlab.detector.decode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.coda.jevio.CompositeData;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EvioCompactStructureHandler;
import org.jlab.coda.jevio.EvioException;
import org.jlab.coda.jevio.EvioNode;
import org.jlab.detector.decode.DetectorDataDgtz.ADCData;
import org.jlab.detector.decode.DetectorDataDgtz.TDCData;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioTreeBranch;
import org.jlab.utils.data.DataUtils;

/**
 * All static methods from CodaEventDecoder.
 * 
 * @author baltzell
 */
public class CodaDecoders {
    
    /**
     * Returns an array of the branches in the event.
     * @param event
     * @return
     */
    public static List<EvioTreeBranch> getEventBranches(EvioDataEvent event){
        ArrayList<EvioTreeBranch>  branches = new ArrayList<>();
        try {
            EvioCompactStructureHandler handler = event.getStructureHandler();
            if (handler == null) {
                return branches;
            }
            List<EvioNode> eventNodes = handler.getNodes();
            if (eventNodes==null) {
                return branches;
            }
            for (EvioNode node : eventNodes){
                EvioTreeBranch eBranch = new EvioTreeBranch(node.getTag(),node.getNum());
                List<EvioNode>  childNodes = node.getChildNodes();
                if (childNodes!=null){
                    for (EvioNode child : childNodes){
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

    public static void printByteBuffer(ByteBuffer buffer, int max, int columns){
        int n = max;
        if(buffer.capacity()<max) n = buffer.capacity();
        StringBuilder str = new StringBuilder();
        for(int i = 0 ; i < n; i++){
            str.append(String.format("%02X ", buffer.get(i)));
            if( (i+1)%columns==0 ) str.append("\n");
        }
        System.out.println(str.toString());
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
    public static void decodeComposite(ByteBuffer buffer, int offset, List<DataType> ctypes, List<Object> citems){
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

    /**
     * SVT decoding
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public static ArrayList<DetectorDataDgtz> getDataEntries_57617(Integer crate, EvioNode node, EvioDataEvent event){

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

    /**
     * Bank TAG=57657 used for ATOF PETIROC TDC values
     * @param crate
     * @param node
     * @param event
     * @return
     *
     * <dictEntry name="PETIROC Composite Data" tag="0xe139" num="0" type="composite">
     *           <description format="c,i,l,N(c,i,i)">
     *                       c   "slot number"
     *                       i   "trigger number"
     *                       l   "time stamp"
     *                       N   "number of channels fired"
     *                       c   "channel number"
     *                       i   "tdc value"
     *                       i   "width value"
     *        </description>
     * </dictEntry>
     */
    public static List<DetectorDataDgtz> getDataEntries_57657(Integer crate, EvioNode node, EvioDataEvent event) {

        ArrayList<DetectorDataDgtz>  entries = new ArrayList<>();

        if(node.getTag()==57657){
            try {
                //System.err.println("Decoding ATOF PETIROC event!");
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
                    Byte    slot       = (Byte)     cdataitems.get(position+0);
                    Integer trig_num   = (Integer)  cdataitems.get(position+1);
                    Long    time_stamp = (Long)    cdataitems.get(position+2);
                    Integer nchannels  = (Integer) cdataitems.get(position+3);
                    int     counter    = 0;

                    position += 4; // slot, trig,time,nchannels
                                   //
                    while(counter<nchannels){
                        Byte channel = (Byte) cdataitems.get(position+0);
                        Integer tdc = (Integer) cdataitems.get(position+1);
                        // width over threshold
                        Integer tot = (Integer) cdataitems.get(position+2);

                        DetectorDataDgtz bank = new DetectorDataDgtz(
                            crate, slot.intValue(), channel.intValue());
                        // the "bank" has a timestamp.
                        // the tdc also can have a timestamp.
                        // the tdc is added tot he "bank"
                        // the "bank" is added to the "entries" (array of DetectorDataDgtz)
                        // "entries" List<DetectorDataDgtz>  -> "bank" DetectorDataDgtz  -> "tdc" TDCData
                        // there is a redundancy in timestamp: the same value is stored in TDCData and the DetectorDataDgz
                        //
                        bank.setTimeStamp(time_stamp);
                        bank.setTrigger(trig_num);;
                        TDCData tdc_data = new TDCData(tdc, tot);
                        tdc_data.setTimeStamp(time_stamp).setOrder(counter);
                        bank.addTDC(tdc_data);
                        entries.add(bank);
                        position += 3; // channel,tdc,tot
                        counter++;
                        //System.err.println("event: " + bank.toString());
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
     * Bank TAG=57636 used for RICH TDC values
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public static List<DetectorDataDgtz> getDataEntries_57636(Integer crate, EvioNode node, EvioDataEvent event){

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

    /**
     * Bank TAG=57648 used for DC (Drift Chambers) TDC and ToT values.
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public static List<DetectorDataDgtz> getDataEntries_57648(Integer crate, EvioNode node, EvioDataEvent event){
        List<DetectorDataDgtz>  entries = new ArrayList<>();
        if(node.getTag()==57648){
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
                        Byte   channel = (Byte) cdataitems.get(position);
                        Short  tdc     = (Short) cdataitems.get(position+1);
                        Short  tot     = (Short) cdataitems.get(position+2);
                        position += 3;
                        counter++;
                        DetectorDataDgtz   entry = new DetectorDataDgtz(crate,slot,channel);
                        entry.addTDC(new TDCData(tdc, tot));
                        entry.setTimeStamp(time);
                        entries.add(entry);
                    }
                }
            } catch (EvioException ex) {
                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IndexOutOfBoundsException ex){
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
    public static List<DetectorDataDgtz> getDataEntries_57622(Integer crate, EvioNode node, EvioDataEvent event){
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
                Logger.getLogger(CodaEventDecoder.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IndexOutOfBoundsException ex){
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
//      <dictEntry name="FADC250 Pulse Integral Data (mode 3)" tag="0xe103" num="0" type="composite">
//            <description format="c,i,l,N(c,N(s,i))">
//                  c     "slot number" (8bit)
//                  i     "trigger number" (32bit)
//                  l     "time stamp" (64bit)
//                  N     "number of channels fired" (32bit)
//                  c     "channel number" (8bit)
//                  N     "number of pulses" (32bit)
//                  s     "tdc value" (16bit)
//                  i     "adc value" (32bit)
//            </description>
//      </dictEntry>
    public static List<DetectorDataDgtz> getDataEntries_57603(Integer crate, EvioNode node, EvioDataEvent event){
        List<DetectorDataDgtz>  entries = new ArrayList<>();
        if(node.getTag()==57603){
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
                            DetectorDataDgtz  entry = new DetectorDataDgtz(crate,slot,channel);
                            ADCData   adcData = new ADCData();
                            adcData.setIntegral(adc).setTimeWord(tdc);
                            entry.addADC(adcData);
                            entry.setTimeStamp(time);
                            entries.add(entry);
                            position+=2;
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
     * Decoding MODE 7 data. for given crate.
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public static List<DetectorDataDgtz> getDataEntries_57602(Integer crate, EvioNode node, EvioDataEvent event){
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
     * Decoding MicroMegas Packed Data
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public static List<DetectorDataDgtz> getDataEntries_57641(Integer crate, EvioNode node, EvioDataEvent event){
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

                            int s;
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
     * Decoding MicroMegas Packed Data
     * @param crate
     * @param node
     * @param event
     * @return
     */
    public static List<DetectorDataDgtz> getDataEntries_57640(Integer crate, EvioNode node, EvioDataEvent event){
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

                    	int s;
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

    public static List<DetectorDataDgtz> getDataEntries_57627(Integer crate, EvioNode node, EvioDataEvent event){

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
     * FADC250, Mode-1, bitpacked
     * @param crate
     * @param node
     * @param event
     * @return 
     */
    public static List<FADCData> getDataEntries_57638(Integer crate, EvioNode node, EvioDataEvent event){
        List<FADCData>  entries = new ArrayList<>();
        if(node.getTag()==57638){
            ByteBuffer     compBuffer = node.getByteData(true);
            List<DataType> cdatatypes = new ArrayList<>();
            List<Object>   cdataitems = new ArrayList<>();
            decodeComposite(compBuffer, 24, cdatatypes, cdataitems);
            
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
    public static List<DetectorDataDgtz> getDataEntries_57601(Integer crate, EvioNode node, EvioDataEvent event){
        
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
                ByteBuffer compBuffer = node.getByteData(true);
                System.out.println("Exception in CRATE = " + crate + " LENGTH = " + compBuffer.array().length);
                printByteBuffer(compBuffer, 120, 20);
            }
        }
        return entries;
    }

    public static List<FADCData> getADCEntries_Tag(Integer crate, EvioNode node, EvioDataEvent event, int tagid){
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
                System.out.println("Exception in CRATE = " + crate + " LENGTH = " + compBuffer.array().length);
                printByteBuffer(compBuffer, 120, 20);
            }
        }
        return entries;
    }

}
