package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlab.coda.jevio.ByteDataTransformer;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EvioNode;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.evio.EvioTreeBranch;

/**
 * A wrapper on EvioSource to read one EVIO file sorted by CODA event number.
 * 
 * WARNING:  This is strictly for reading sorted events sequentially.  Methods
 * other than the overridden "open", "hasEvent", "getNextEvent", and "reset"
 * may not work as intended.
 * 
 * FIXME:  Events with no HEAD bank or negative event number are unreachable.
 * 
 * @author baltzell
 */
public class EvioSortedSource extends EvioSource {

    // Tag of HEAD bank containing CODA event number:
    public static final int HEAD_BANK_TAG = 57615; // 0xe10f

    // Index of the CODA event number in the HEAD bank:
    public static final int HEAD_BANK_EVENT_INDEX = 4;

    // Ordered map of CODA event number to EVIO event index:
    private final Map<Integer,Integer> map = new TreeMap<>();

    // Ordered list of CODA event numbers (just for map lookup
    // by key index without creating a new object every time): 
    private final List<Integer> list = new ArrayList<>();

    // Current index in the map/list during sorted reading:
    private int index = 0;

    public EvioSortedSource() {
        super();
    }

    /**
     * Extracted from stuff in the standard decoder.
     * @param event
     * @return the CODA event number
     */
    private static int getCodaEventNumber(EvioDataEvent event){
        if (event != null && event.getHandler().getStructure() != null){
            List<EvioTreeBranch> branches = CodaEventDecoder.getEventBranches(event);
            for (EvioTreeBranch branch : branches){
                EvioTreeBranch cbranch = CodaEventDecoder.getEventBranch(branches, branch.getTag());
                for (EvioNode node : cbranch.getNodes()) {
                    if (node.getTag() == HEAD_BANK_TAG) {
                        if (node.getDataTypeObj()==DataType.INT32||node.getDataTypeObj()==DataType.UINT32) {
                            return ByteDataTransformer.toIntArray(node.getStructureBuffer(true))[HEAD_BANK_EVENT_INDEX];
                        }
                    }
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Read the file once and initialize the event ordering
     * @param filename 
     */
    private void load(String filename, int maxEvents) {
        this.map.clear();
        this.list.clear();
        super.open(filename);
        while (super.hasEvent()) {
            if (maxEvents>0 && this.map.keySet().size()>maxEvents) break;
            final int coda = getCodaEventNumber((EvioDataEvent)super.getNextEvent());
            final int evio = this.getCurrentIndex()-1;
            if (coda > 0) {
                map.put(coda, evio);
            }
        }
        this.close();
        this.list.addAll(new ArrayList(this.map.keySet()));
    }

    /**
     * Initialize the event ordering and (re)open the file
     * @param filename 
     */
    @Override
    public void open(String filename) {
        this.load(filename, -1);
        this.index = 0;
        super.open(filename);
    }

    /**
     * @return whether there's events remaining
     */
    @Override
    public boolean hasEvent() {
        return this.index < this.list.size()-1;
    }

    /**
     * Retrieve the next event, as sorted by CODA event number
     * @return event 
     */
    @Override
    public DataEvent getNextEvent() {
        final int coda = this.list.get(this.index);
        final int evio = this.map.get(coda);
        this.index++;
        return this.gotoEvent(evio);
    }

    /**
     * Go back to the "beginning" of the file
     */
    @Override
    public void reset() {
        this.index = 0;
    }

    public String toString(int index) {
        String ret = "";
        for (int i=index<0?0:index; i<this.list.size(); ++i) {
            final int coda = this.list.get(i);
            final int evio = this.map.get(coda);
            ret += String.format("LOCAL/EVIO/CODA:  %d %d %d%s", i, evio, coda, index<0?"\n":"");
            if (index >= 0) break;
        }
        return ret;
    }

    public static void main(String args[]) {
        EvioSortedSource s = new EvioSortedSource();
        s.open("/Users/baltzell/data/clas_pin_019331.evio.00000");
        System.out.print(s.toString(-1));
        int previousEventNumber = -999;
        int previousIndex = -999;
        while (s.hasEvent()) {
            DataEvent e = s.getNextEvent();
            //System.out.println(s.toString(s.index-1));
            int thisEventNumber = getCodaEventNumber((EvioDataEvent)e);
            if (thisEventNumber > 0 && thisEventNumber <= previousEventNumber) {
                System.out.println(String.format("PREVIOUS:  %d %d",
                        previousEventNumber, previousIndex));
                System.out.println(String.format("CURRENT:  %d %d",
                        thisEventNumber, s.getCurrentIndex()));
                break;
            }
            previousIndex = s.getCurrentIndex();
            previousEventNumber = thisEventNumber;
        }
    }

}
