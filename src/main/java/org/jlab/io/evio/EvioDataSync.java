package org.jlab.io.evio;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EventBuilder;
import org.jlab.coda.jevio.EventWriter;
import org.jlab.coda.jevio.EvioBank;
import org.jlab.coda.jevio.EvioEvent;
import org.jlab.coda.jevio.EvioException;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataSync;

/**
 *
 * @author gavalian
 */
public final class EvioDataSync implements DataSync {

    private String evioOutputDirectory = null;
    private String evioOutputFile = null;
    private Integer evioCurrentFileNumber = 0;
    private Long maximumBytesToWrite = (long) 1932735283;
    private Long maximumRecordsToWrite = (long) 2000000;
    private Long currentBytesWritten = (long) 0;
    private Long currentRecordsWritten = (long) 0;
    private ByteOrder writerByteOrder = ByteOrder.LITTLE_ENDIAN;
    private EventWriter evioWriter = null;

    public EvioDataSync() {
    }

    public EvioDataSync(String filename) {
        this.open(filename);
    }

    public void setSplit(boolean flag) {
    }

    @Override
    public void open(String filename) {
        this.initFileNames(filename);
        this.openFileForWriting();
    }

    public void initFileNames(String filename) {
        Path filepath = Paths.get(filename);
        this.evioOutputFile = filepath.getFileName().toString();
        if (filepath.getParent() == null) {
            this.evioOutputDirectory = "";
        } else {
            this.evioOutputDirectory = filepath.getParent().toString();
        }
        System.out.println("[EvioDataSync] ---> " + this.evioOutputDirectory);
        System.out.println("[EvioDataSync] ---> " + this.evioOutputFile);
    }

    private void openFileForWriting() {
        StringBuilder str = new StringBuilder();
        if (this.evioOutputDirectory.length() > 2) {
            str.append(this.evioOutputDirectory);
            str.append("/");
        }
        str.append(this.evioOutputFile);
        String filename = str.toString();
        String dictionary = "<xmlDict>\n</xmlDict>\n";
        System.out.println(dictionary);
        this.currentBytesWritten = (long) 0;
        this.currentRecordsWritten = (long) 0;
        try {
            evioWriter = new EventWriter(new File(filename), dictionary, true);
        } catch (EvioException ex) {
            Logger.getLogger(EvioDataSync.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void writeEvent(DataEvent event) {

        if (this.currentBytesWritten > this.maximumBytesToWrite || this.currentRecordsWritten > this.maximumRecordsToWrite) {
            this.evioWriter.close();
            this.evioCurrentFileNumber++;
            this.openFileForWriting();
            System.out.println("open file # " + this.evioCurrentFileNumber);
        }

        try {
            ByteBuffer original = event.getEventBuffer();
            Long bufferSize = (long) original.capacity();
            this.currentBytesWritten += bufferSize;
            this.currentRecordsWritten++;
            ByteBuffer clone = ByteBuffer.allocate(original.capacity());
            clone.order(original.order());
            original.rewind();
            clone.put(original);
            original.rewind();
            clone.flip();
            evioWriter.writeEvent(clone);
        } catch (Exception e){
            System.out.println("Something went wrong with writing");   
        }
    }

    public void openWithDictionary(String filename, String dictionary) {
        try {
            evioWriter = new EventWriter(new File(filename),  null, true);
        } catch (EvioException ex) {
            Logger.getLogger(EvioDataSync.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void close() {
        evioWriter.close();
    }

    public EvioDataEvent createEvent(EvioDataDictionary dict) {
        try {
            EventBuilder builder = new EventBuilder(1, DataType.BANK, 0);
            EvioEvent event = builder.getEvent();
            EvioBank baseBank = new EvioBank(10, DataType.ALSOBANK, 0);
            builder.addChild(event, baseBank);
            ByteOrder byteOrder = writerByteOrder;
            int byteSize = event.getTotalBytes();
            ByteBuffer bb = ByteBuffer.allocate(byteSize);
            bb.order(byteOrder);
            event.write(bb);
            bb.flip();
            return new EvioDataEvent(bb, dict);
        } catch (EvioException ex) {
            Logger.getLogger(EvioDataSync.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public DataEvent createEvent() {
        try {
            EventBuilder builder = new EventBuilder(1, DataType.BANK, 0);
            EvioEvent event = builder.getEvent();
            EvioBank baseBank = new EvioBank(10, DataType.ALSOBANK, 0);
            builder.addChild(event, baseBank);
            ByteOrder byteOrder = writerByteOrder;
            int byteSize = event.getTotalBytes();
            ByteBuffer bb = ByteBuffer.allocate(byteSize);
            bb.order(byteOrder);
            event.write(bb);
            bb.flip();
            return new EvioDataEvent(bb, EvioFactory.getDictionary());
        } catch (EvioException ex) {
            Logger.getLogger(EvioDataSync.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
