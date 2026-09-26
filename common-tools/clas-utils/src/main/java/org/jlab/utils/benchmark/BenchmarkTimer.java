package org.jlab.utils.benchmark;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author gavalian
 */
public class BenchmarkTimer {

    public static int WARMUP_CALLS = 300;
   
    public static class BenchmarkMultiTimer extends BenchmarkTimer {
        ConcurrentHashMap<Integer,Long> timeAtResume = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer,Boolean> isPaused = new ConcurrentHashMap<>();
        public BenchmarkMultiTimer(String name) { super(name); }
        public void resume(int thread) {
            if (!isPaused.containsKey(thread) || isPaused.get(thread)) {
                timeAtResume.put(thread, System.nanoTime());
                isPaused.put(thread, false);
            }
        }
        public void pause(int thread) {
            if (!isPaused.get(thread)) {
                if (numberOfCalls.incrementAndGet() > WARMUP_CALLS)
                    totalTime.addAndGet(System.nanoTime() - timeAtResume.get(thread));
                isPaused.put(thread, true);
            }
        }
        @Override
        public void reset(){
            super.reset();
            timeAtResume.clear();
            isPaused.clear();
        }
    }

    private String timerName = "generic";
    private long timeAtResume = 0;
    private Boolean isPaused = true;

    AtomicInteger numberOfCalls = new AtomicInteger(0);
    AtomicLong totalTime = new AtomicLong(0);

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
            if (numberOfCalls.incrementAndGet() > WARMUP_CALLS)
                totalTime.addAndGet(System.nanoTime() - timeAtResume);
            isPaused = true;
        }
    }

    public void reset(){
        totalTime.set(0);
        numberOfCalls.set(0);
        timeAtResume = 0;
        isPaused = true;
    }
    
    public double getMilliseconds(){
        return totalTime.get() / 1.0e6;
    }
    
    public double getSeconds(){
        return totalTime.get() / 1.0e9;
    }
    
    public double getMillisecondsPerCall() {
        return numberOfCalls.get() > WARMUP_CALLS ? getMilliseconds() / (numberOfCalls.get() - WARMUP_CALLS) : 0;
    }

    @Override
    public String toString() {
        return String.format("%-15s : #Calls %12d, Total = %12.2f sec, Unit = %12.3f msec",
            getName(), numberOfCalls.get()-WARMUP_CALLS, getSeconds(), getMillisecondsPerCall());
    }

}
