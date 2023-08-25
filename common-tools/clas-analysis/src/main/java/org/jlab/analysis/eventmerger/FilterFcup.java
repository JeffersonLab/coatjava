package org.jlab.analysis.eventmerger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlab.detector.epics.Epics;
import org.jlab.detector.epics.EpicsSequence;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.*;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.utils.data.TextHistogram;

/**
 * Hipo Reduce Worker: filters events based on beam current 
 * calculated from integrating scalers
 * 
 * Inputs: minimum accepted beam current
 * 
 * @author devita
 */

public class FilterFcup implements Worker {

    public final static String FCUP_SCALER = "DSC2";
    private Bank runConfigBank = null;
    private DaqScalersSequence scalerSeq = null;
    private EpicsSequence epicsSeq = null;
    private double charge  = -1;
    private double current = -1;
    private String source  = null;
    private int[]  currentBuffer = new int[21];
    private int    currentMax    = 80; 
    
    public FilterFcup(double current, String source){
        this.current=current;
        this.source=source;
        System.out.print("\nInitializing Faraday Cup reduction: threshold current set to " + this.current);
        System.out.print("\n                                    current source set to " + (this.source.equals(FCUP_SCALER) ? source : "RAW:epics."+source) + "\n");
    }
    
    public FilterFcup(double current){
        this(current, FCUP_SCALER);
    }
    
    /**
     * Initialize bank schema
     * 
     * @param reader
     */
    @Override
    public void init(HipoReader reader) {
       runConfigBank = new Bank(reader.getSchemaFactory().getSchema("RUN::config"));
    }

    /**
     * Set sequence of scaler readings
     * 
     * @param sequence
     */
    public void setScalerSequence(DaqScalersSequence sequence) {
        this.scalerSeq=sequence;
    }
    
    /**
     * Set sequence of Epics readings
     * 
     * @param sequence
     */
    public void setEpicsSequence(EpicsSequence sequence) {
        this.epicsSeq=sequence;
    }
    
    /**
     * Event filter: selects events in scaler interval with beam current greater than threshold and build statistics information
     * 
     * @param event
     * @return
     */
    @Override
    public boolean processEvent(Event event) {
        event.read(runConfigBank);
        
        if(runConfigBank.getRows()>0){
            long timeStamp  = runConfigBank.getLong("timestamp",0);
            int  unixTime   = runConfigBank.getInt("unixtime",0);
            
            // get beam current
            double value=0;
            if(source.equals(FCUP_SCALER))
                value = scalerSeq.getInterval(timeStamp).getBeamCurrent();
            else {
                if(epicsSeq.get(unixTime)!=null)
                    value = epicsSeq.getMinimum(source, 0, unixTime);
            }
            
            // fill statistics array
            int currentBins = currentBuffer.length-1;
            if(value>currentMax){
                currentBuffer[currentBins] = currentBuffer[currentBins] + 1;
            } else if(value<0){
                currentBuffer[0] = currentBuffer[0];
            } else{
                int bin =  (int) (currentBins*value/(currentMax));
                currentBuffer[bin] = currentBuffer[bin] + 1;
            }
            
            // set filter value
            if(value>current) return true;
        }
        return false;
    }
    
    // This function has to be implemented, but not used if
    // HipoStream is not trying to classify the events.
    @Override
    public long clasifyEvent(Event event) { return 0L; }

    /**
     * Get Map of beam current values
     * @return
     */
    public Map<String,Double> getCurrentMap(){
        Map<String,Double> sizeMap = new LinkedHashMap<>();
        int currentBins = currentBuffer.length-1;
        double     step =  ((double) currentMax)/currentBins;
        for(int i = 0; i < currentBins; i++){
           String key = String.format("[%6.1f -%6.1f]", (i*step),(i+1)*step);
           sizeMap.put(key, (double) currentBuffer[i]);
        }
        sizeMap.put("overflow", (double) currentBuffer[currentBins] );
        return sizeMap;
    }

    /**
     * Show beam current histogram
     */
    public void showStats() {
        System.out.println("\n\n");
        System.out.println(" BEAM CURRENT HISTOGRAM (ENTRIES ARE EVENTS)\n");        
        TextHistogram histo = new TextHistogram();
        Map<String,Double> sizeMap = this.getCurrentMap();
        histo.setPrecision(0);
        histo.setMinDecriptorWidth(28);
        histo.setWidth(80);
        histo.setData(sizeMap);
        histo.print();
    }
}
