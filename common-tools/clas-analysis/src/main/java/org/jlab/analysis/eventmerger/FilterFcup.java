package org.jlab.analysis.eventmerger;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private double currentMin = -1;
    private double currentMax = 80; 
    private String source  = null;
    private int[]  histoBuffer = new int[21];
    private int    histoMax = 80; 
    
    public FilterFcup(double min, double max, String source){
        this.currentMin=min;
        this.currentMax=max;
        this.histoMax=(int) (2*max);
        this.source=source;
        System.out.print("\nInitializing Faraday Cup reduction: current range set to " + this.currentMin + " - " + this.currentMax);
        System.out.print("\n                                    current source set to " + (this.source.equals(FCUP_SCALER) ? source : "RAW:epics."+source) + "\n");
    }
    
    public FilterFcup(double min, double max){
        this(min, max, FCUP_SCALER);
    }
    
    public FilterFcup(double min){
        this(min, Double.MAX_VALUE, FCUP_SCALER);
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
            int currentBins = histoBuffer.length-1;
            if(value>histoMax){
                histoBuffer[currentBins] = histoBuffer[currentBins] + 1;
            } else if(value<0){
                histoBuffer[0] = histoBuffer[0];
            } else{
                int bin =  (int) (currentBins*value/(histoMax));
                histoBuffer[bin] = histoBuffer[bin] + 1;
            }
            
            // set filter value
            if(value>currentMin && value<currentMax) return true;
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
        int currentBins = histoBuffer.length-1;
        double     step =  ((double) currentMax)/currentBins;
        for(int i = 0; i < currentBins; i++){
           String key = String.format("[%6.1f -%6.1f]", (i*step),(i+1)*step);
           sizeMap.put(key, (double) histoBuffer[i]);
        }
        sizeMap.put("overflow", (double) histoBuffer[currentBins] );
        return sizeMap;
    }

    /**
     * Show beam current histogram
     */
    public void showStats() {
        System.out.println("\n\n");
        System.out.println(" BEAM CURRENT HISTOGRAM BEFORE FILTER (ENTRIES ARE EVENTS)\n");        
        TextHistogram histo = new TextHistogram();
        Map<String,Double> sizeMap = this.getCurrentMap();
        histo.setPrecision(0);
        histo.setMinDecriptorWidth(28);
        histo.setWidth(80);
        histo.setData(sizeMap);
        histo.print();
    }
}
