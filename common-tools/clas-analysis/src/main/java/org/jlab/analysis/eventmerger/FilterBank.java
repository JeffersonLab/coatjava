package org.jlab.analysis.eventmerger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlab.jnp.hipo4.data.*;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.utils.data.TextHistogram;

/**
 * Hipo Reduce Worker: filter event based on bank size
 * 
 * Inputs: bank name and number of rows
 * Returns "true" if selected bank size is greater than given value or bank name is an empty string
 * Returns "false" otherwise
 * @author devita
 */
public class FilterBank implements Worker {

    private Bank filterBank = null;
    private String bankName = null;
    private int nRows = -1;
    private int[]  rowBuffer = new int[21];
    private int    rowMax    = 500; 
    
    public FilterBank(String bankName,int nRows){
        this.bankName = bankName;
        this.nRows  = nRows;
        System.out.println("\nInitializing bank size reduction: bank set to " + this.bankName + " with minimum rows set to " + this.nRows + "\n");
    }

    /**
     * Initialize bank schema
     * 
     * @param reader
     */
    @Override
    public void init(HipoReader reader) {
        if(!bankName.isEmpty())
            filterBank = new Bank(reader.getSchemaFactory().getSchema(bankName));
    }

    /**
     * Event filter: select events according to trigger bit
     * 
     * @param event
     * @return
     */
    @Override
    public boolean processEvent(Event event) {
        
        if(filterBank==null) return true;
        
        event.read(filterBank);
        double value = (double) filterBank.getRows();

        // fill statistics array
        int rowBins = rowBuffer.length-1;
        if(value>rowMax){
            rowBuffer[rowBins] = rowBuffer[rowBins] + 1;
        } else{
            int bin =  (int) (rowBins*value/(rowMax));
            rowBuffer[bin] = rowBuffer[bin] + 1;
        }        

        return filterBank.getRows()>this.nRows;
    }
    
    // This function has to be implemented, but not used if
    // HipoStream is not trying to classify the events.
    @Override
    public long clasifyEvent(Event event) { return 0L; }

    /**
     * Get Map of beam current values
     * @return
     */
    public Map<String,Double> getBankSizeMap(){
        Map<String,Double> sizeMap = new LinkedHashMap<>();
        int rowBins = rowBuffer.length-1;
        double     step =  ((double) rowMax)/rowBins;
        for(int i = 0; i < rowBins; i++){
           String key = String.format("[%6.1f -%6.1f]", (i*step),(i+1)*step);
           sizeMap.put(key, (double) rowBuffer[i]);
        }
        sizeMap.put("overflow", (double) rowBuffer[rowBins] );
        return sizeMap;
    }

    /**
     * Show beam current histogram
     */
    public void showStats() {
        if(filterBank==null) return;
        System.out.println("\n\n");
        System.out.println(bankName.toUpperCase() + " BANK SIZE HISTOGRAM (ENTRIES ARE EVENTS)\n");        
        TextHistogram histo = new TextHistogram();
        Map<String,Double> sizeMap = this.getBankSizeMap();
        histo.setPrecision(0);
        histo.setMinDecriptorWidth(28);
        histo.setWidth(80);
        histo.setData(sizeMap);
        histo.print();
    }
}
