package org.jlab.detector.epics;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import org.jlab.io.hipo.HipoDataBank;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.JsonUtils;

/**
 * For easy access to most recent epics readout for any given event.
 *
 * See the main() method for example use case, where only the 2 lines
 * marked with "!!!" are specific to accessing epics information.
 * 
 * @author baltzell, devita
 */
public class DaqEpicsSequence implements Comparator<DaqEpics>{
      
    private final static String EPICS_DEFAULT = "scaler_calc1b";
    
    protected final List<DaqEpics> epics=new ArrayList<>();
    
    private Bank rcfgBank=null;
  

    @Override
    public int compare(DaqEpics o1, DaqEpics o2) {
        if     (o1.getUnixTime()<o2.getUnixTime()) return -1;
        else if(o1.getUnixTime()>o2.getUnixTime()) return  1;
        return 0;
    }

    protected int findIndex(int unixTime) {
        if (this.epics.isEmpty()) return -1;
        if (unixTime < this.epics.get(0).getUnixTime()) return -1;
        if (unixTime > this.epics.get(this.epics.size()-1).getUnixTime()) return -1;
        // make a fake state for timestamp search:
        DaqEpics de = new DaqEpics();
        de.setUnixTime(unixTime);
        final int index=Collections.binarySearch(this.epics,de,new DaqEpicsSequence());
        final int n = index<0 ? -index-2 : index;
        return n;
    }
   
    protected boolean add(DaqEpics de) {
        if (this.epics.isEmpty()) {
            this.epics.add(de);
            return true;
        }
        else {
            final int index=Collections.binarySearch(this.epics,de,new DaqEpicsSequence());
            if (index==this.epics.size()) {
                // its timestamp is later than the existing sequence:
                this.epics.add(de);
                return true;
            }
            else if (index<0) {
                // it's a unique timestamp, insert it:
                this.epics.add(-index-1,de);
                return true;
            }
            else {
                // it's a duplicate timestamp, ignore it:
                return false;
            }
        }
    }
    
    /**
     * @param index
     * @return the DaqEpics for the given index
     */
    public DaqEpics getElement(int index) {
        if (index>=0 && index<this.size()) return this.epics.get(index);
        return null;
    }

    /**
     * @param unixTime (i.e. RUN::config.timestamp)
     * @return the most recent DaqEpics for the given timestamp
     */
    public DaqEpics get(int unixTime) {
        final int n=this.findIndex(unixTime);
        if (n>=0) return this.epics.get(n);
        return null;
    }

    /**
     * @param event 
     * @return the most recent DaqEpics for the given event
     */
    public DaqEpics get(Event event) {
        event.read(this.rcfgBank);
        return this.get(this.rcfgBank.getInt("unixTime", 0));
    }

    /**
     * @param t1 lower limit of a Unix time interval
     * @param t2 upper limit of a Unix time interval
     * @return subset of sequence entries in the interval
     */
    public List<DaqEpics> getSubList(int t1, int t2) {
        int idx1 = this.findIndex(t1);
        int idx2 = this.findIndex(t2);
        if(idx1 > idx2)
            return null;
        else if(idx1>=0 && idx2<=this.epics.size()-1)
            return this.epics.subList(idx1, idx2);
        else 
            return null;
    }
    
    /**
     * @param name Epics variable name
     * @param defaultvalue Epics variable default value
     * @param t1 lower limit of a Unix time interval
     * @param t2 upper limit of a Unix time interval
     * @return integral of the chosen variable in the time interval
     */
    public double getIntegral(String name, double defaultvalue, int t1, int t2) {
        List<DaqEpics> sublist = this.getSubList(t1, t2);
        
        double integral=0;
        
        if(sublist!=null && sublist.size()>1) {
            for(int i=1; i<sublist.size(); i++) {
                DaqEpics de1 = sublist.get(i);
                DaqEpics de0 = sublist.get(i-1);
                if(de0.getUnixTime()>0)
                    integral += de1.getDouble(name, defaultvalue)*(de1.getUnixTime()-de0.getUnixTime());
            }
        }
        return integral;
    }
    
    /**
     * @param name Epics variable name
     * @param defaultvalue Epics variable default value
     * @return integral of the chosen variable
     */
    public double getIntegral(String name, double defaultvalue) {
        if(!this.epics.isEmpty())
            return this.getIntegral(name, defaultvalue, this.epics.get(0).getUnixTime(), this.epics.get(this.epics.size()-1).getUnixTime());
        else 
            return 0;
    }

    /**
     * @param name Epics variable name
     * @param defaultvalue Epics variable default value
     * @param t1 lower limit of a Unix time interval
     * @param t2 upper limit of a Unix time interval
     * @return minimum of the chosen variable in the time interval
     */
    public double getMinimum(String name, double defaultvalue, int t1, int t2) {
        List<DaqEpics> sublist = this.getSubList(t1, t2);
                
        if(sublist!=null && sublist.size()>1) {
            double minimum=Double.MAX_VALUE;
            for(int i=1; i<sublist.size(); i++) {
                DaqEpics de = sublist.get(i);
                if(de.getDouble(name, defaultvalue)<minimum)
                    minimum = de.getDouble(name, defaultvalue);
            }
            return minimum;
        }
        return defaultvalue;
    }
    
    /**
     * @param name Epics variable name
     * @param defaultvalue Epics variable default value
     * @param unixtime Unix time
     * @return minimum of the chosen variable in -2s:+4s around the input time
     */
    public double getMinimum(String name, double defaultvalue, int unixtime) {
        return this.getMinimum(name, defaultvalue, unixtime-2, unixtime+4);
    }
    
    public void print(String name) {
        if(!this.epics.isEmpty()) {
            System.out.println("Index\tTI timestamp\tname");
            for(int i=0; i<this.epics.size(); i++) {
                DaqEpics de = this.epics.get(i);
                System.out.println(i + "\t" + de.getUnixTime() + "\t" + de.getEpicsReadout().get(name));
            }
            System.out.println("Integral = " + this.getIntegral(name, 0));
        }
    }    
    /**
     * This reads tag=1 events for RAW::epics banks, and initializes and returns
     * a {@link DaqEpicsSequence} that can be used to access the most recent epics
     * readout for any given event.
     * 
     * @param filenames list of names of HIPO files to read
     * @return  sequence
     */
    public static DaqEpicsSequence readSequence(List<String> filenames) {
       
        DaqEpicsSequence seq=new DaqEpicsSequence();

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
                Bank epicsBank =new Bank(schema.getSchema("RAW::epics"));
                Bank configBank=new Bank(schema.getSchema("RUN::config"));
            
                reader.nextEvent(event);
                event.read(epicsBank );
                event.read(configBank);
                         
                if (epicsBank .getRows()<1 || configBank.getRows()<1) continue;
                
                int unixTime=configBank.getInt("unixtime",0);
                
                DaqEpics de = new DaqEpics(); 
                de.setUnixTime(unixTime);
                de.setEpicsReadout(JsonUtils.read(new HipoDataBank(epicsBank), "json"));
                if(de.getEpicsReadout().get(EPICS_DEFAULT)!=null) seq.add(de);
            }

            reader.close();
        }
        
        return seq;
    }
    
    public int size() {
        return this.epics.size();
    }
    
    public static void main(String[] args) {
        
        final String dir="/Users/devita/Work/clas12/data/";
        final String file="clas_005038.evio.00000-00004.hipo";
        
        List<String> filenames=new ArrayList<>();
        if (args.length>0) filenames.addAll(Arrays.asList(args));
        else               filenames.add(dir+file);

        // 1!!!1 initialize a sequence from tag=1 events: 
        DaqEpicsSequence seq = DaqEpicsSequence.readSequence(filenames);
        seq.print("scaler_calc1b");

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
            
                int unixtime = -1;
                int eventnumber = -1;
                if (rcfgBank.getRows()>0) {
                    unixtime    = rcfgBank.getInt("unixtime",0);
                    eventnumber = rcfgBank.getInt("event",0);
                }
                // 2!!!2 use the unix to get the most recent epics data:
                DaqEpics de=seq.get(unixtime);

                if (de==null) {
                    bad++;
                }
                else {
                    good++;
                    // do something useful with beam charge here:
                    System.out.println(eventnumber + " " + unixtime + " " + 
                                       seq.findIndex(unixtime) + " " + de.getUnixTime() + " " + de.getDouble("scaler_calc1b", 0));
                }
            }

            System.out.println("DaqScalersSequence:  bad/good/badPercent: "
                    +bad+" "+good+" "+100*((float)bad)/(bad+good)+"%");

            reader.close();

        }
    }
}