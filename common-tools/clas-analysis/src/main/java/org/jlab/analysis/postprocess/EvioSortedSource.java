package org.jlab.analysis.postprocess;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlab.coda.jevio.ByteDataTransformer;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EvioNode;
import org.jlab.detector.decode.CodaEventDecoder;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.evio.EvioTreeBranch;

/**
 * Just a wrapper to read an EVIO file sorted by event number.
 * @author baltzell
 */
public class EvioSortedSource extends EvioSource {
    
    public static final int HEAD_BANK_TAG = 57615; // 0xe10f
    public static final int EVENT_NUMBER_INDEX = 4;

    Map<Integer,Integer> eventMap;
    int localIndex;

    public EvioSortedSource() {
        super();
        eventMap = new TreeMap<>();
    }

    private static int getCodaEventNumber(EvioDataEvent event){
        if (event.getHandler().getStructure() != null){
            List<EvioTreeBranch> branches = CodaEventDecoder.getEventBranches(event);
            for (EvioTreeBranch branch : branches){
                EvioTreeBranch cbranch = CodaEventDecoder.getEventBranch(branches, branch.getTag());
                for (EvioNode node : cbranch.getNodes()) {
                    if (node.getTag() == HEAD_BANK_TAG) {
                        if (node.getDataTypeObj()==DataType.INT32||node.getDataTypeObj()==DataType.UINT32) {
                            return ByteDataTransformer.toIntArray(node.getStructureBuffer(true))[EVENT_NUMBER_INDEX];
                        }
                    }
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private void loadEventMap(String filename) {
        eventMap.clear();
        super.open(filename);
        while (super.hasEvent()==true){
            final int eventNumber = getCodaEventNumber((EvioDataEvent)super.getNextEvent());
            eventMap.put(eventNumber, this.getCurrentIndex());
        }
        this.close();
    }

    @Override
    public void open(String filename) {
        this.loadEventMap(filename);
        this.localIndex = 0;
        super.open(filename);
    }

    @Override
    public boolean hasEvent() {
        return this.localIndex < this.eventMap.keySet().size()-1;
    }

    @Override
    public void reset() {
        this.localIndex = 0;
    }

    @Override
    public DataEvent getNextEvent() {
        int evioIndex = (int)this.eventMap.values().toArray()[this.localIndex];
        this.localIndex++;
        return this.gotoEvent(evioIndex);
    }

    public static void main(String args[]) {
        EvioSortedSource s = new EvioSortedSource();
        s.open("/Users/baltzell/data/clas_019439/clas_019439.evio.00040");
        for (int i : s.eventMap.keySet()) {
            System.out.println(String.format("%d %d",i,s.eventMap.get(i)));
        }
        while (s.hasEvent()) {
            DataEvent e = s.getNextEvent();
            System.out.println(getCodaEventNumber((EvioDataEvent)e));
        }
    }

}
