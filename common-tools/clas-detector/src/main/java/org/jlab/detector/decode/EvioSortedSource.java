package org.jlab.analysis.postprocess;

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
import org.jlab.detector.decode.CodaEventDecoder;

/**
 * A wrapper to read an EVIO file sorted by CODA event number.
 * 
 * @author baltzell
 */
public class EvioSortedSource extends EvioSource {
  
    public static final int HEAD_BANK_TAG = 57615; // 0xe10f
    public static final int HEAD_BANK_EVENT_INDEX = 4;

    private final Map<Integer,Integer> map = new TreeMap<>();
    private int index = 0;

    public EvioSortedSource() {
        super();
    }

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

    private void loadEventMap(String filename) {
        this.index = 0;
        this.map.clear();
        super.open(filename);
        while (super.hasEvent()) {
            final int coda = getCodaEventNumber((EvioDataEvent)super.getNextEvent());
            final int evio = this.getCurrentIndex()-1;
            if (coda > 0) {
                map.put(coda, evio);
            }
        }
        this.close();
    }

    @Override
    public void open(String filename) {
        this.loadEventMap(filename);
        super.open(filename);
    }

    @Override
    public boolean hasEvent() {
        return this.index < this.map.keySet().size()-1;
    }

    @Override
    public void reset() {
        this.index = 0;
    }

    @Override
    public DataEvent getNextEvent() {
        final int coda = (int)this.map.keySet().toArray()[this.index];
        final int evio = this.map.get(coda);
        System.out.println(String.format("LOCAL/EVIO/CODA:  %d %d %d",
                this.index, evio, coda));
        this.index++;
        return this.gotoEvent(evio);
    }

    public void showMap() {
        System.out.println("****** MAP ******");
        for (int index=0; index<this.map.keySet().size(); ++index) {
            final int coda = (int)this.map.keySet().toArray()[index];
            final int evio = this.map.get(coda);
            System.out.println(String.format("LOCAL/EVIO/CODA:  %d %d %d",
                    index,evio,coda));
        }
        System.out.println("****** END MAP ******");
    }

    public static void main(String args[]) {
        EvioSortedSource s = new EvioSortedSource();
        s.open("/Users/baltzell/data/clas_pin_019331.evio.00000");
        //s.open("/Users/baltzell/data/clas_019349.evio.00040");
        s.showMap();
        int previousEventNumber = -999;
        int previousIndex = -999;
        while (s.hasEvent()) {
            DataEvent e = s.getNextEvent();
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
