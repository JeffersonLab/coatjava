package org.jlab.utils.benchmark;

import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author gavalian
 */
public class ProgressPrintout {

    private final int WARMUP_CALLS = 100;
    
    private TreeMap<String,Object> items   = new TreeMap<>();
    private Long     previousPrintoutTime = (long) 0;
    private Long     startPrintoutTime    = (long) 0;
    private double   printoutIntervalSeconds = 10.0;
    private String   printoutLeadingString   = ">>>>> progress : ";
    private Integer  numberOfCalls           = 0;
    
    public  ProgressPrintout(){
        this.previousPrintoutTime = System.currentTimeMillis();
        this.startPrintoutTime = System.currentTimeMillis();
    }
    
    public  ProgressPrintout(String name){
        this.printoutLeadingString = name;
        this.previousPrintoutTime = System.currentTimeMillis();
        this.startPrintoutTime = System.currentTimeMillis();
    }
    
    public void setInterval(double interval){
        this.printoutIntervalSeconds = interval;
    }
    
    public String getUpdateString(){
        double totalElapsedTime = (this.previousPrintoutTime-this.startPrintoutTime)*1e-3;
        StringBuilder str = new StringBuilder();
        double averageTime = 1000.0*totalElapsedTime/(this.numberOfCalls-WARMUP_CALLS);
        str.append(String.format("%s (%12d) : ", this.printoutLeadingString,this.numberOfCalls-WARMUP_CALLS));
        str.append(String.format(" time : %8.2f (sec) =>>> average time = %9.3f msec", totalElapsedTime,averageTime));
        Set<String> keys = this.items.keySet();
        for(String key : keys){
            str.append(this.getItemString(key));
        }
        return str.toString();
    }
    
    public void showStatus(){
        System.out.println("\n\n");
        System.out.println(this.getUpdateString());
        System.out.println("\n\n");        
    }
    
    public void updateStatus(){        
        if (++this.numberOfCalls < WARMUP_CALLS) {
            this.previousPrintoutTime = System.currentTimeMillis();
            this.startPrintoutTime = System.currentTimeMillis();
        }
        else {
            Long currentTime   = System.currentTimeMillis();
            Double elapsedTime = (currentTime - this.previousPrintoutTime)*1e-3;
            if(elapsedTime >= this.printoutIntervalSeconds){
                this.previousPrintoutTime = System.currentTimeMillis();
                System.out.println(this.getUpdateString());
            }
        }
    }
    
    public void   setAsInteger(String name, Integer value){
        this.items.put(name, value);
    }
    
    public void   setAsDouble(String name, Double value){
        this.items.put(name, value);
    }
    
    public String getItemString(String itemname){
        StringBuilder str = new StringBuilder();
        if(this.items.get(itemname) instanceof Integer integer){
            str.append(String.format("  %s : %5d",itemname, integer));
        }
        
        if(this.items.get(itemname) instanceof Double aDouble){
            str.append(String.format("  %s : %8.3f",itemname, aDouble));
        }        
        return str.toString();
    }
    
    public static void main(String[] args){
        ProgressPrintout  progress = new ProgressPrintout();
        int loop = 0;
        while(true){
            loop++;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProgressPrintout.class.getName()).log(Level.SEVERE, null, ex);
            }
            progress.updateStatus();
        }
    }
}
