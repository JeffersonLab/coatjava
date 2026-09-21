package org.jlab.utils.benchmark;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.jlab.utils.benchmark.BenchmarkTimer.BenchmarkMultiTimer;

/**
 *
 * @author gavalian
 */
public class Benchmark implements Comparator<String> {
   
    private static final Benchmark benchmarkInstance = new Benchmark();
    private Map<String,BenchmarkMultiTimer> timerStore = new LinkedHashMap<>();
    private Timer updateTimer = null;
    private final ArrayList<String> specials = new ArrayList<>();
    
    public Benchmark() {}

    public Benchmark(String[] specials) {
        this.specials.addAll(Arrays.asList(specials));
    }

    public static Benchmark getInstance(){
        return benchmarkInstance;
    }
    
    public void printTimer(int seconds){
        TimerTask timerTask = new TimerTask() { 
            @Override
            public void run() { System.out.println(benchmarkInstance); }
        };
        updateTimer = new Timer("Benchmark", true);
        updateTimer.scheduleAtFixedRate(timerTask, 0, 1000*seconds);
    }
    
    public void reset(){
        for (BenchmarkTimer bt : timerStore.values())
            bt.reset(); 
    }
    
    public synchronized void addTimer(String name){
        if (!timerStore.containsKey(name))
            timerStore.put(name, new BenchmarkMultiTimer(name));
    }
    
    public void pause(String name){
        if (!timerStore.containsKey(name))
            addTimer(name);
        else
            timerStore.get(name).pause();
    }
    
    public void resume(String name){
        if (!timerStore.containsKey(name))
            addTimer(name);
        timerStore.get(name).resume();
    }
    
    public void pause(int thread, String name){
        if (!timerStore.containsKey(name))
            addTimer(name);
        else
            timerStore.get(name).pause(thread);
    }
    
    public void resume(int thread, String name){
        if (!timerStore.containsKey(name))
            addTimer(name);
        timerStore.get(name).resume(thread);
    }
    
    public BenchmarkTimer getTimer(String name){
        return timerStore.getOrDefault(name, null);
    }

    @Override
    public String toString(){
        StringBuilder s = new StringBuilder();
        Collection<BenchmarkMultiTimer> timers = timerStore.values();
        if (!timers.isEmpty()) {
            int len = timers.iterator().next().toString().length();
            char[] asterix = new char[len+8];
            Arrays.fill(asterix,'*');
            String margins = new String(asterix);
            s.append(margins);
            s.append("\n");
            s.append("*     Benchmark  Results \n");
            s.append(margins);
            s.append("\n");
            for (BenchmarkTimer b : timers) {
                s.append("*   ");
                s.append(b);
                s.append("   *\n");
            }
            s.append(String.format("*   %-15s : #Calls %12.2f, Total = %12.2f sec, Unit = %12.3f msec   *\n",
                 "TOTAL",
                 ((float)timers.stream().mapToInt(x -> x.numberOfCalls.get()).sum())/timers.size(),
                 timers.stream().mapToDouble(x -> x.getSeconds()).sum(),
                 timers.stream().mapToDouble(x -> x.getMillisecondsPerCall()).sum()));
            s.append(margins);
            s.append("\n");
        }
        return s.toString();
    }

    public String[] toCSV() {
        return new String[]{
            String.join(",",timerStore.keySet()) + ",TOTAL",
            String.join(",",timerStore.values().stream().map(x -> String.valueOf(x.getMillisecondsPerCall())).toList())
                + "," + timerStore.values().stream().mapToDouble(x -> x.getMillisecondsPerCall()).sum()
        };
    }

    public void sortHeaders() {
        List<String> keys = new ArrayList<>(timerStore.keySet());
        Collections.sort(keys, this);
        Map<String,BenchmarkMultiTimer> timers = new LinkedHashMap<>();
        for (String s : keys)
            timers.put(s, timerStore.get(s));
        timerStore = timers;
    }

    @Override
    public int compare(String s1, String s2) {
        if (specials.contains(s1)) {
            return specials.contains(s2) ? s1.compareTo(s2) : -1;
        } else {
            return specials.contains(s2) ? 1 : s1.compareTo(s2);
        }
    }
    
}
