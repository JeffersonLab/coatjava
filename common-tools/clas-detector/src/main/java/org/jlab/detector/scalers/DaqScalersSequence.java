package org.jlab.detector.scalers;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Logger;

import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.detector.calib.utils.ConstantsManager;

/**
 * For easy access to most recent scaler readout for any given event.
 *
 * See the main() method for example use case, where only the 2 lines
 * marked with "!!!" are specific to accessing scalers.
 * 
 * @author baltzell
 */
public class DaqScalersSequence implements Comparator<DaqScalers> {
  
    public static final double TI_CLOCK_FREQ = 250e6; // Hz
    
    protected final List<DaqScalers> scalers=new ArrayList<>();
    
    private Bank runConfigBank=null;
    private Bank runScalerBank=null;
  
    protected static final Logger logger = Logger.getLogger(DaqScalersSequence.class.getName());
    
    protected DaqScalersSequence(){};

    public static class Interval {
        private DaqScalers previous = null;
        private DaqScalers next = null;
        public Interval(DaqScalersSequence seq) {
            if (!seq.scalers.isEmpty()) {
                this.previous = seq.scalers.get(0);
                this.next = seq.scalers.get(seq.scalers.size()-1);
            }
        }
        public Interval(DaqScalersSequence seq, long t1, long t2) {
            final int idx1 = seq.findIndex(t1);
            final int idx2 = seq.findIndex(t2);
            if (idx1>=0 && idx2<seq.scalers.size()-1) {
                this.previous = seq.scalers.get(idx1);
                this.next = seq.scalers.get(idx2+1);
            }
        }
        public double getBeamChargeGated() {
            if (previous!=null && next!=null) {
                return this.next.dsc2.getBeamChargeGated()
                      -this.previous.dsc2.getBeamChargeGated();
            }
            return 0;
        }
        public double getBeamCharge() {
            if (previous!=null && next!=null) {
                return this.next.dsc2.getBeamCharge()
                      -this.previous.dsc2.getBeamCharge();
            }
            return 0;
        }
        public double getBeamCurrent() {
            if (previous!=null && next!=null) {
                final double dt = (next.getTimestamp()-previous.getTimestamp())/TI_CLOCK_FREQ;
                if (dt>0) {
                    return this.getBeamCharge()/dt;
                }
            }
            return 0;
        }
    }
    
    @Override
    public int compare(DaqScalers o1, DaqScalers o2) {
        if (o1.getTimestamp() < o2.getTimestamp()) return -1;
        if (o1.getTimestamp() > o2.getTimestamp()) return +1;
        return 0;
    }

    /** @return the number of scalers in this sequence */
    public int size() {
      return scalers.size();
    }
  
    protected int findIndex(long timestamp) {
        if (this.scalers.isEmpty()) return -1;
        if (timestamp < this.scalers.get(0).getTimestamp()) return -1;
        // assume late timestamps are ok and go with last readout, so comment this out:
        //if (timestamp > this.scalers.get(this.scalers.size()-1).getTimestamp()) return -1;
        // make a fake state for timestamp search:
        DaqScalers ds=new DaqScalers();
        ds.setTimestamp(timestamp);
        final int index=Collections.binarySearch(this.scalers,ds,new DaqScalersSequence());
        final int n = index<0 ? -index-2 : index;
        logger.finest(" -> DaqScalersSequence.findIndex(" + timestamp + ") -> index = " + index + " -> return " + n);
        return n;
    }

    public DaqScalersSequence(SchemaFactory schema) {
        runConfigBank = new Bank(schema.getSchema("RUN::config"));
        runScalerBank = new Bank(schema.getSchema("RUN::scaler"));
    }

    public DaqScalersSequence(List<DaqScalers> inputScalers) {
        for (DaqScalers inputScaler : inputScalers)
            this.add(inputScaler);
    }

    /**
     * remove all readouts from the sequence
     */
    public void clear() {
        scalers.clear();
    }

    /**
     * remove all but the latest readouts from the sequence
     * @param keep the number of readouts to keep 
     */
    public void clear(int keep) {
        while (scalers.size() > keep) scalers.remove(0);
    }

    protected boolean add(DaqScalers ds) {
        if (this.scalers.isEmpty()) {
            this.scalers.add(ds);
            return true;
        }
        else {
            final int index=Collections.binarySearch(this.scalers,ds,new DaqScalersSequence());
            if (index==this.scalers.size()) {
                // its timestamp is later than the existing sequence:
                this.scalers.add(ds);
                return true;
            }
            else if (index<0) {
                // it's a unique timestamp, insert it:
                this.scalers.add(-index-1,ds);
                return true;
            }
            else {
                // it's a duplicate timestamp, ignore it:
                return false;
            }
        }
    }

    public boolean add(Event event){
        event.read(runScalerBank);
        event.read(runConfigBank);
        if (runScalerBank.getRows() > 0) {
            long timestamp=0;
            int evnum=0;
            if (runConfigBank.getRows()>0) {
                timestamp=runConfigBank.getLong("timestamp",0);
                evnum=runConfigBank.getInt("event",0);
            }
            DaqScalers ds=DaqScalers.create(runScalerBank);
            ds.setTimestamp(timestamp);
            ds.setEventNum(evnum);
            return add(ds);
        }
        return false;
    }

    /**
     * @param timestamp TI timestamp (i.e. RUN::config.timestamp)
     * @return the most recent DaqScalers for the given timestamp
     */
    public DaqScalers get(long timestamp) {
        final int n=this.findIndex(timestamp);
        if (n>=0) return this.scalers.get(n);
        return null;
    }

    /**
     * @param event 
     * @return the most recent DaqScalers for the given event
     */
    public DaqScalers get(Event event) {
        event.read(this.runConfigBank);
        return this.get(this.runConfigBank.getLong("timestamp", 0));
    }

    /**
     * @return largest available interval of scaler readings 
     */
    public Interval getInterval() {
        return new Interval(this);
    }

    /**
     * @param timestamp TI timestamp (i.e. RUN::config.timestamp)
     * @return smallest interval of scaler readings around that timestamp
     */
    public Interval getInterval(long timestamp) {
        return this.getInterval(timestamp,timestamp);
    }
    
    /**
     * @param event
     * @return smallest interval of scaler readings around that event
     */
    public Interval getInterval(Event event) {
        event.read(this.runConfigBank);
        return this.getInterval(this.runConfigBank.getLong("timestamp", 0));
    }

    /**
     * @param t1 first TI timestamp (i.e. RUN::config.timestamp)
     * @param t2 second TI timestamp
     * @return smallest interval of scaler readings around those timestamps
     */
    public Interval getInterval(long t1,long t2) {
        return new Interval(this,t1,t2);
    }
    
    /**
     * @param event1 first event
     * @param event2 second event
     * @return smallest interval of scaler readings around those events
     */
    public Interval getInterval(Event event1, Event event2) {
        event1.read(this.runConfigBank);
        final long t1 = this.runConfigBank.getLong("timestamp",0);
        event2.read(this.runConfigBank);
        final long t2 = this.runConfigBank.getLong("timestamp",0);
        return this.getInterval(t1,t2);
    }

    /**
     * This reads tag=1 events for RUN::scaler banks, and initializes and returns
     * a {@link DaqScalersSequence} that can be used to access the most recent scaler
     * readout for any given event.
     * 
     * @param filenames list of names of HIPO files to read
     * @return  sequence
     */
    public static DaqScalersSequence readSequence(List<String> filenames) {
        DaqScalersSequence seq=new DaqScalersSequence();
        seq.readFiles(filenames);
        return seq;
    }

    /**
     * @param filenames list of names of HIPO files to read
     */
    protected void readFiles(List<String> filenames) {
        logger.info("DaqScalersSequence::  Reading scaler sequence from "+String.join(",", filenames));
       
        for (String filename : filenames) {

            HipoReader reader = new HipoReader();
            reader.setTags(1);
            reader.open(filename);

            if (this.runConfigBank==null) {
                this.runConfigBank = new Bank(reader.getSchemaFactory().getSchema("RUN::config"));
            }
        
            SchemaFactory schema = reader.getSchemaFactory();
            Event event=new Event();
            Bank scalerBank=new Bank(schema.getSchema("RUN::scaler"));
            Bank configBank=new Bank(schema.getSchema("RUN::config"));

            while (reader.hasNext()) {
            
                reader.nextEvent(event);
                event.read(scalerBank);
                event.read(configBank);
         
                long timestamp=0;
                int evnum=0;
                
                if (scalerBank.getRows()<1) continue;
                if (configBank.getRows()>0) {
                    timestamp=configBank.getLong("timestamp",0);
                    evnum=configBank.getInt("event",0);
                }
        
                DaqScalers ds=DaqScalers.create(scalerBank);
                ds.setTimestamp(timestamp);
                ds.setEventNum(evnum);
                this.add(ds);
            }

            reader.close();
        }
    }
   
    /**
     * Reads the RAW::scaler bank and rebuilds the RUN::scaler and HEL::scaler banks
     * @param tags
     * @param conman
     * @param filenames
     * @return 
     */
    public static DaqScalersSequence rebuildSequence(int tags, ConstantsManager conman, List<String> filenames) {
        logger.info("DaqScalersSequence::  Rebuilding scaler sequence from "+String.join(",", filenames));
        DaqScalersSequence seq=new DaqScalersSequence();
        for (String filename : filenames) {
            HipoReader reader = new HipoReader();
            reader.setTags(tags);
            reader.open(filename);
            SchemaFactory schema = reader.getSchemaFactory();
            if (seq.runConfigBank==null)
                seq.runConfigBank = new Bank(schema.getSchema("RUN::config"));
            while (reader.hasNext()) {
                Event event=new Event();
                Bank scaler=new Bank(schema.getSchema("RAW::scaler"));
                Bank config=new Bank(schema.getSchema("RUN::config"));
                reader.nextEvent(event);
                event.read(scaler);
                event.read(config);
                if (scaler.getRows()<1 || config.getRows()<1) continue;
                seq.add(DaqScalers.create(conman, config, scaler));
            }
            reader.close();
        }
        return seq;
    }

    /**
     * Checks if the scalers list is sorted such that the scalers' timestamp and event number orderings are consistent and monotonically increasing.
     * @return {@code true} if timestamp and event number orderings are consistent
     */
    public boolean validateOrdering() {
        if (scalers.size() <= 1) return true; // trivial case
        boolean result = true;
        for (int i = 0; i < scalers.size() - 1; i++) {
            var prev = scalers.get(i);
            var next = scalers.get(i + 1);
            var timestampComparison = Long.compare(prev.getTimestamp(), next.getTimestamp());
            var evnumComparison     = Integer.compare(prev.getEventNum(), next.getEventNum());
            if (timestampComparison == 0 || evnumComparison == 0) {
                logger.warning("WARNING: found possible duplicate scaler readout: evnum=" + prev.getEventNum() + " timestamp=" + prev.getTimestamp() + " i=" + i);
                logger.warning("                                next readout has: evnum=" + next.getEventNum() + " timestamp=" + next.getTimestamp());
                result = false;
            }
            // if neither is equal, they must have the same sign: negative, i.e., increasing monotonically
            else {
                if (Integer.signum(timestampComparison) != -1 || Integer.signum(evnumComparison) != -1) {
                    logger.warning("WARNING: found non-monotonic scaler ordering: evnum=" + prev.getEventNum() + " timestamp=" + prev.getTimestamp() + " i=" + i);
                    logger.warning("                            next readout has: evnum=" + next.getEventNum() + " timestamp=" + next.getTimestamp());
                    result = false;
                }
            }
        }
        return result;
    }

    /**
     * Try to fix clock rollover on the run-integrating DSC2 scaler.
     * 1.  Assume the first clock readout has no rollover.
     * 2.  Assume any subsequent clock decrease is a rollover. 
     */
    public void fixClockRollover() {
        // fixed rollover size
        final long ROLLOVER = 2*(long)Integer.MAX_VALUE;
        // loop over ungated and gated, to apply correction separately for each
        final int is_ungated = 0;
        final int is_gated   = 1;
        for (int clk : List.of(is_ungated, is_gated)) {
            boolean modified = true;
            while (modified) {
                modified = false;
                for (int i=this.scalers.size()-1; i>0; --i) {
                    Dsc2Scaler previous = this.scalers.get(i-1).dsc2;
                    Dsc2Scaler next     = this.scalers.get(i).dsc2;
                    String  clock_name;
                    long    diff;
                    boolean is_rollover;
                    switch (clk) {
                        case is_ungated:
                            clock_name  = "ungated clock";
                            diff        = previous.clock - next.clock + 1;
                            is_rollover = previous.clock > next.clock;
                            break;
                        default: // is_gated
                            clock_name  = "gated clock";
                            diff        = previous.gatedClock - next.gatedClock + 1;
                            is_rollover = previous.gatedClock > next.gatedClock;
                            break;
                    }
                    boolean is_gap = diff <= -ROLLOVER / 2;
                    if (is_rollover || is_gap) {
                        for (int j=i; j<this.scalers.size(); ++j) {
                            switch (clk) {
                                case is_ungated:
                                    if (j==i)   logger.info( String.format("fixing ungated clock rollover:  %d ->", this.scalers.get(j).dsc2.clock));
                                    if (is_gap) this.scalers.get(j).dsc2.clock -= ROLLOVER;
                                    else        this.scalers.get(j).dsc2.clock += ROLLOVER;
                                    if (j==i)   logger.info( String.format("                             -> %d", this.scalers.get(j).dsc2.clock));
                                    break;
                                default: // is_gated
                                    if (j==i)   logger.info( String.format("fixing gated clock rollover:  %d ->", this.scalers.get(j).dsc2.gatedClock));
                                    if (is_gap) this.scalers.get(j).dsc2.gatedClock -= ROLLOVER;
                                    else        this.scalers.get(j).dsc2.gatedClock += ROLLOVER;
                                    if (j==i)   logger.info( String.format("                           -> %d", this.scalers.get(j).dsc2.gatedClock));
                                    break;
                            }
                            if (j==i) {
                                if (Math.abs( ((double)diff/ROLLOVER) - 1 ) > 0.01) {
                                    logger.warning("found " + clock_name + " rollover of unexpected size " + diff + " (expected about " + ROLLOVER + ")");
                                }
                            }
                        }
                        modified = true;
                        break;
                    }
                }
            }
        }
    }


    public static void main(String[] args) {
        
        final String dir = System.getenv("HOME")+"/data/";
        //final String file = "rollover-4013.hipo";
        final String file = "DVCSWagon_004013.hipo";
        //final String file = "clas_004003.evio.00040-00049.hipo";

        List<String> filenames=new ArrayList<>();
        if (args.length>0) filenames.addAll(Arrays.asList(args));
        else               filenames.add(dir+file);

        ConstantsManager consts = new ConstantsManager();
        consts.init("/runcontrol/fcup","/runcontrol/slm","/runcontrol/helicity","/daq/config/scalers/dsc1","/runcontrol/hwp");

        // 1!!!1 initialize a sequence from tag=1 events: 
        DaqScalersSequence seq = DaqScalersSequence.rebuildSequence(1, consts, filenames);
        //DaqScalersSequence seq = DaqScalersSequence.readSequence(filenames);
        
        //for (DaqScalers ds : seq.scalers) System.out.println(String.format("PRE:  %s",ds));
                
        seq.fixClockRollover();
        
        //for (DaqScalers ds : seq.scalers) System.out.println(String.format("POST:  %s",ds));
        
        System.exit(1);

        long good=0;
        long bad=0;
        
        for (String filename : filenames) {

            HipoReader reader = new HipoReader();
            reader.setTags(0);
            reader.open(filename);
            
            SchemaFactory schema = reader.getSchemaFactory();
        
            while (reader.hasNext()) {

                Bank rcfgBank=new Bank(schema.getSchema("RUN::config"));
               
                Event event=new Event();
                reader.nextEvent(event);
              
                event.read(rcfgBank);
            
                long timestamp = -1;
                if (rcfgBank.getRows()>0) 
                    timestamp = rcfgBank.getLong("timestamp",0);

                // 2!!!2 use the timestamp to get the most recent scaler data:
                DaqScalers ds=seq.get(timestamp);

                if (ds==null) {
                    bad++;
                }
                else {
                    good++;
                    // do something useful with beam charge here:
                    System.out.println(String.format("%d %s %f %f",
                        timestamp,
                        ds.dsc2,
                        ds.dsc2.getBeamCharge(),
                        ds.dsc2.getBeamChargeGated()));
                }
            }

            System.out.println("DaqScalersSequence:  bad/good/badPercent: "
                    +bad+" "+good+" "+100*((float)bad)/(bad+good)+"%");

            reader.close();

        }
    }
}
