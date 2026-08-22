package org.jlab.utils.benchmark;

/**
 *
 * @author gavalian
 */
public class BenchmarkTimer {

    private String timerName = "generic";
    private long timeAtResume = 0;
    private Boolean isPaused = true;

    protected int numberOfCalls = 0;
    protected long totalTime = 0;

    public BenchmarkTimer() {}

    public BenchmarkTimer(String name) {
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
    public String toString() {
        double timePerCall = 0.0;
        if (numberOfCalls != 0) timePerCall = getMiliseconds() / numberOfCalls;
        return String.format("(%-15s) : #Calls %12d,  Total  = %12.2f sec,  Unit = %12.3f msec",
            getName(), numberOfCalls, getSeconds(), timePerCall);
    }
}
