package org.jlab.io.hipo;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 * A wrapper on HipoDataSource to read one file sorted by CODA event number.
 *
 * WARNING:  This is strictly for reading sorted events sequentially.  Methods
 * other than the overridden "open", "hasEvent", "getNextEvent", and "reset"
 * may not work as intended.
 * 
 * FIXME:  Events with no HEAD bank or negative event number are unreachable.
 * 
 * @author baltzell
 */
public class HipoDataSourceSorted extends HipoDataSource {
    
    // Ordered map of CODA event number to EVIO event index:
    private final TreeMap<Integer,Integer> map = new TreeMap<>();

    // Ordered list of CODA event numbers (just for TreeMap lookup
    // by key index without creating a new object every time): 
    private final ArrayList<Integer> list = new ArrayList<>();

    // Current event index during sorted reading:
    private int index = 0;

    public HipoDataSourceSorted () {
        super();
    }

    public TreeMap<Integer, Integer> getMapping() {
        return new TreeMap<>(this.map);
    }

    /**
     * Read the file once and initialize the event ordering
     * @param filename 
     */
    private void load(String filename, int maxEvents) {
        Logger.getLogger(this.getClass().getName()).info("Loading event ordering ...");
        this.map.clear();
        this.list.clear();
        super.open(filename);
        while (super.hasEvent()) {
            if (maxEvents>0 && this.map.keySet().size()>maxEvents) break;
            DataEvent e = super.getNextEvent();
            if (e.hasBank("RUN::config")) {
                DataBank b = e.getBank("RUN::config");
                if (b.rows() > 0) {
                    int coda = b.getInt("event",0);
                    if (coda > 0) {
                        int evio = this.currentEventNumber-1;
                        map.put(coda, evio);
                    }
                }
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
        int coda = this.list.get(this.index++);
        return this.gotoEvent(this.map.get(coda));
    }

    /**
     * Go back to the "beginning" of the file
     */
    @Override
    public void reset() {
        this.index = 0;
    }
    
    public static void main(String args[]) {
        HipoDataSource s = new HipoDataSourceSorted();
        s.open("/Users/baltzell/Software/coatjava/iss166-eventordering/x.hipo");
        int previousEventNumber = -999;
        int previousIndex = -999;
        while (s.hasEvent()) {
            DataEvent e = s.getNextEvent();
            if (e.hasBank("RUN::config")) {
                DataBank b = e.getBank("RUN::config");
                if (b.rows() > 0) {
                    int thisEventNumber = b.getInt("event",0);
                    if (thisEventNumber > 0 && thisEventNumber < previousEventNumber) {
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
    }

}