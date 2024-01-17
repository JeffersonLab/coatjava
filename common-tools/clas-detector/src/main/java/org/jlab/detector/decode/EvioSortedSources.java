package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.TreeMap;
import javafx.util.Pair;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;

/**
 * Read a group of files sorted by CODA event number.
 * 
 * Multiple run numbers in one instance isn't protected against, so don't do it.
 * 
 * To minimize JEVIO memory leak, this closes each file when appropriate.
 *
 * @author baltzell
 */
public class EvioSortedSources {

    private static class EvioSource2 extends EvioSource {
        public String filename;
        public boolean open = false;
        public int min = Integer.MAX_VALUE;
        public int max = Integer.MIN_VALUE;
        public EvioSource2(String filename) {
            super();
            this.filename = filename;
        }
        public void open() {
            System.err.println("OPENING  "+this.filename);
            super.open(this.filename);
            this.open = true;
        }
        @Override
        public void close() {
            System.err.println("CLOSING  "+this.filename);
            super.close();
            this.open = false;
        }
        @Override
        public DataEvent gotoEvent(int i) {
            if (!this.open) this.open();
            return super.gotoEvent(i);
        }
    }

    // List of sources for simple access:
    private final ArrayList<EvioSource2> files = new ArrayList();

    // Ordered map of CODA event number to EVIO file/event:
    private final TreeMap<Integer, Pair<EvioSource2,Integer>> map = new TreeMap();

    // Ordered list of CODA event numbers (just for map lookup
    // by key index without creating a new object every time): 
    private final ArrayList<Integer> list = new ArrayList<>();

    // Current event index during sorted reading:
    private int index = 0;

    public EvioSortedSources() {}

    private void load(String filename) {
        EvioSource2 e = new EvioSource2(filename);
        this.files.add(e);
        this.list.clear();
        e.open();
        while (e.hasEvent()) {
            final int coda = EvioSortedSource.getCodaEventNumber((EvioDataEvent)e.getNextEvent());
            final int evio = e.getCurrentIndex()-1;
            if (coda >= 0) {
                e.max = Math.max(coda,e.max);
                e.min = Math.min(coda,e.min);
                map.put(coda, new Pair(e, evio));
            }
        }
        e.close();
        this.list.addAll(new ArrayList<>(map.keySet()));
    }

    public void open(String... filenames) {
        this.index = 0;
        for (String f : filenames) {
            this.load(f);
        }
    }

    public boolean hasEvent() {
        return this.index < this.list.size()-1;
    }

    public DataEvent getNextEvent() {
        final int coda = this.list.get(this.index);
        final int evio = this.map.get(coda).getValue();
        this.index++;
        for (EvioSource2 e : this.files) {
            if (e.open && coda > e.max) {
                e.close();
            }
        }
        return this.map.get(coda).getKey().gotoEvent(evio);
    }

    public static void main(String args[]) {
        EvioSortedSources s = new EvioSortedSources();
        s.open("/Users/baltzell/data/clas_019349.evio.00040",
               "/Users/baltzell/data/clas_019349.evio.00047");
        while (s.hasEvent()) {
            DataEvent e = s.getNextEvent();
            System.err.println(EvioSortedSource.getCodaEventNumber((EvioDataEvent)e));
        }
    }
}
