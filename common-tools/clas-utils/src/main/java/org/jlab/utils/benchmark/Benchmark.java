package org.jlab.utils.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author gavalian
 */
public class Benchmark {
    
    private static final Benchmark benchmarkInstance = new Benchmark();
    private final Map<String,BenchmarkTimer> timerStore = new HashMap<>();
    private Timer updateTimer = null;
    
    public Benchmark(){}
    
    public static Benchmark getInstance(){
        return benchmarkInstance;
    }
    
    public void printTimer(int interval){
        TimerTask timerTask = new TimerTask() { 
                @Override
                public void run() { System.out.println(this); }
            };
        updateTimer = new Timer("Benchmark");
        updateTimer.scheduleAtFixedRate(timerTask, 0, interval);
    }
    
    public void reset(){
        for(Map.Entry<String,BenchmarkTimer> entry : timerStore.entrySet()){
            entry.getValue().reset();
        }
    }
    
    public void addTimer(String name){
        if (!timerStore.containsKey(name)){
            timerStore.put(name, new BenchmarkTimer(name));
        } else {
            System.err.println("[Benchmark] -----> error. timer with name ("
            + name + ") already exists");
        }
    }
    
    public void pause(String name){
        if (!timerStore.containsKey(name))
            timerStore.put(name, new BenchmarkTimer(name));
        timerStore.get(name).pause();
    }
    
    public void resume(String name){
        if (!timerStore.containsKey(name))
            timerStore.put(name, new BenchmarkTimer(name));
        timerStore.get(name).resume();
    }
    
    public BenchmarkTimer getTimer(String name){
        return timerStore.getOrDefault(name, null);
    }

    public BenchmarkTimer getTotal(String name) {
        BenchmarkTimer b = new BenchmarkTimer(name);
        for(Map.Entry<String,BenchmarkTimer> timer : timerStore.entrySet()){
            b.numberOfCalls += timer.getValue().numberOfCalls;
            b.totalTime += timer.getValue().totalTime;
        }
        return b;
    }

    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();
        ArrayList<String> timerStrings = new ArrayList<>();
        for(Map.Entry<String,BenchmarkTimer> timer : timerStore.entrySet()){
            timerStrings.add(timer.getValue().toString());
        }
        Collections.sort(timerStrings);
        timerStrings.add(getTotal("").toString());
        
        if(!timerStrings.isEmpty()){
            int len = timerStrings.get(0).length();
            char[]  asterix = new char[len+8];
            Arrays.fill(asterix,'*');
            String margins = new String(asterix);
            str.append(margins);
            str.append("\n");
            str.append("*     BENCHMARK  RESULTS \n");
            str.append(margins);
            str.append("\n");
            for(String lines : timerStrings){
                str.append("*   ");
                str.append(lines);
                str.append("   *\n");
            }
            str.append(margins);
            str.append("\n");
        }
        
        return str.toString();
    }
}
