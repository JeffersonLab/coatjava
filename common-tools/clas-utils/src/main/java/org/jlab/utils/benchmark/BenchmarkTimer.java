package org.jlab.utils.benchmark;

/**
 *
 * @author gavalian
 */
public class BenchmarkTimer {
    
    private String timerName = "generic";
    private Boolean isPaused = true;
    
    protected long totalTime = 0;
    protected long timeAtResume = 0;
    protected int numberOfCalls = 0;

    public BenchmarkTimer(){}
    
    public BenchmarkTimer(String name){
        timerName = name;
    }
    
    public String getName(){
        return timerName;
    }
    
    public void resume(){
        if(isPaused == true){
            timeAtResume = System.nanoTime();
            isPaused = false;
        }
    }
    
    public void pause(){
        if(isPaused==false){
            long timeAtPause = System.nanoTime();
            totalTime += (timeAtPause - timeAtResume);
            numberOfCalls++;
            isPaused = true;
        }
    }
    
    public void reset(){
        totalTime = 0;
        timeAtResume = 0;
        numberOfCalls = 0;
        isPaused = true;
    }
    
    public double getMiliseconds(){
        return totalTime/(1.0e6);
    }
    
    public double getSeconds(){
        return totalTime/(1.0e9);
    }
    
    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();
        double timePerCall = 0.0;
        if(numberOfCalls!=0) timePerCall = this.getMiliseconds()/numberOfCalls;
        str.append(String.format("%-12s : #Calls %15d,  Total Time  = %8.2f sec,  Unit Time = %8.4f msec",
                this.getName(),this.getName().equals("") ? 0 : numberOfCalls,this.getSeconds(),this.getName().equals("") ? 0 : timePerCall));
        return str.toString();
    }
}
