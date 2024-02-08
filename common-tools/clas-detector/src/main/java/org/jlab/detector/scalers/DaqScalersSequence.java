package org.jlab.detector.scalers;

import java.sql.Time;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.RCDBConstants;

import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.groups.IndexedTable;

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
    
    private Bank rcfgBank=null;
  
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
        return n;
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
        event.read(this.rcfgBank);
        return this.get(this.rcfgBank.getLong("timestamp", 0));
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
        event.read(this.rcfgBank);
        return this.getInterval(this.rcfgBank.getLong("timestamp", 0));
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
        event1.read(this.rcfgBank);
        final long t1 = this.rcfgBank.getLong("timestamp",0);
        event2.read(this.rcfgBank);
        final long t2 = this.rcfgBank.getLong("timestamp",0);
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

        for (String filename : filenames) {

            HipoReader reader = new HipoReader();
            reader.setTags(1);
            reader.open(filename);

            if (seq.rcfgBank==null) {
                seq.rcfgBank = new Bank(reader.getSchemaFactory().getSchema("RUN::config"));
            }
        
            SchemaFactory schema = reader.getSchemaFactory();
        
            while (reader.hasNext()) {
            
                Event event=new Event();
                Bank scalerBank=new Bank(schema.getSchema("RUN::scaler"));
                Bank configBank=new Bank(schema.getSchema("RUN::config"));
            
                reader.nextEvent(event);
                event.read(scalerBank);
                event.read(configBank);
         
                long timestamp=0;
                
                if (scalerBank.getRows()<1) continue;
                if (configBank.getRows()>0) {
                    timestamp=configBank.getLong("timestamp",0);
                }
        
                DaqScalers ds=DaqScalers.create(scalerBank);
                ds.setTimestamp(timestamp);
                seq.add(ds);
            }

            reader.close();
        }
        
        return seq;
    }
    
    public static DaqScalersSequence readSequenceRaw(String... inputFile) {
        return readSequenceRaw(Arrays.asList(inputFile));        
    }

    public static DaqScalersSequence readSequenceRaw(List<String> inputFile) {

        DaqScalersSequence sequence = new DaqScalersSequence();
        ConstantsManager conman = new ConstantsManager();
        conman.init("/runcontrol/fcup","/runcontrol/slm","/runcontrol/helicity","/daq/config/scalers/dsc1");
        
        for (String filename : inputFile) {

            HipoReader reader = new HipoReader();
            reader.open(filename);
            reader.setTags(0);

            Event event = new Event();
            Bank runConfigBank = new Bank(reader.getSchemaFactory().getSchema("RUN::config"));
            Bank rawScalerBank = new Bank(reader.getSchemaFactory().getSchema("RAW::scaler"));
            Bank runScalerBank = new Bank(reader.getSchemaFactory().getSchema("RUN::scaler"));
            
            RCDBConstants rcdb = null;
            IndexedTable ccdb_fcup = null;
            IndexedTable ccdb_slm = null;
            IndexedTable ccdb_hel = null;
            IndexedTable ccdb_dsc = null;

            while (reader.hasNext()) {

                // read the event and necessary banks:
                reader.nextEvent(event);
                event.read(runConfigBank);
                event.read(runScalerBank);
                event.read(rawScalerBank);
       
                if (sequence.rcfgBank == null)
                    sequence.rcfgBank = new Bank(reader.getSchemaFactory().getSchema("RUN::config"));

                // get CCDB/RCDB constants:
                if (runConfigBank.getInt("run",0) >= 100) {
                    ccdb_fcup = conman.getConstants(runConfigBank.getInt("run",0),"/runcontrol/fcup");
                    ccdb_slm = conman.getConstants(runConfigBank.getInt("run",0),"/runcontrol/slm");
                    ccdb_hel = conman.getConstants(runConfigBank.getInt("run",0),"/runcontrol/helicity");
                    ccdb_dsc = conman.getConstants(runConfigBank.getInt("run",0),"/daq/config/scalers/dsc1");
                    rcdb = conman.getRcdbConstants(runConfigBank.getInt("run",0));
                }

                // now rebuild the RUN::scaler bank: 
                if (rcdb!=null && ccdb_fcup !=null && rawScalerBank.getRows()>0) {
       
                    DaqScalers ds;
                    if (ccdb_dsc.getIntValue("frequency", 0,0,0) < 2e5) {
                        ds = DaqScalers.create(rawScalerBank, ccdb_fcup, ccdb_slm, ccdb_hel, ccdb_dsc);
                    }
                    else {
                        // Inputs for calculation run duration in seconds, since for
                        // some run periods the DSC2 clock rolls over during a run.
                        Time rst = rcdb.getTime("run_start_time");
                        Date uet = new Date(runConfigBank.getInt("unixtime",0)*1000L);
                        ds = DaqScalers.create(rawScalerBank, ccdb_fcup, ccdb_slm, ccdb_hel, rst, uet);
                    }

                    sequence.add(ds);
                }
            }
            reader.close();
        }
        return sequence;
    }
    
    public static void main(String[] args) {
        
        final String dir="/Users/baltzell/data/CLAS12/rg-a/decoded/6b.2.0/";
        final String file="clas_005038.evio.00000-00004.hipo";
        //final String dir="/Users/baltzell/data/CLAS12/rg-b/decoded/";
        //final String file="clas_006432.evio.00041-00042.hipo";

        List<String> filenames=new ArrayList<>();
        if (args.length>0) filenames.addAll(Arrays.asList(args));
        else               filenames.add(dir+file);

        // 1!!!1 initialize a sequence from tag=1 events: 
        DaqScalersSequence seq = DaqScalersSequence.readSequence(filenames);

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
                    System.out.println(timestamp+" "+ds.dsc2.getBeamCharge()+" "+ds.dsc2.getBeamChargeGated());
                }
            }

            System.out.println("DaqScalersSequence:  bad/good/badPercent: "
                    +bad+" "+good+" "+100*((float)bad)/(bad+good)+"%");

            reader.close();

        }
    }
}