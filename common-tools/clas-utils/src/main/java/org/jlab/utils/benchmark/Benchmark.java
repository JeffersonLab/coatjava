package org.jlab.utils.benchmark;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.jlab.utils.benchmark.BenchmarkTimer.BenchmarkMultiTimer;
import org.jlab.utils.benchmark.BenchmarkTimer.BenchmarkTimerTotal;

/**
 *
 * @author gavalian
 */
public class Benchmark {
    
    private static final Benchmark benchmarkInstance = new Benchmark();
    private final Map<String,BenchmarkMultiTimer> timerStore = new LinkedHashMap<>();
    private Timer updateTimer = null;
    
    private Benchmark() {}
    
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
        timerStore.get(name).pause();
    }
    
    public void resume(String name){
        if (!timerStore.containsKey(name)) addTimer(name);
        timerStore.get(name).resume();
    }
    
    public void pause(int thread, String name){
        timerStore.get(name).pause(thread);
    }
    
    public void resume(int thread, String name){
        if (!timerStore.containsKey(name)) addTimer(name);
        timerStore.get(name).resume(thread);
    }
    
    public BenchmarkTimer getTimer(String name){
        return timerStore.getOrDefault(name, null);
    }

    public BenchmarkTimer getTotal(String name) {
        BenchmarkTimerTotal total = new BenchmarkTimerTotal(name);
        for (BenchmarkTimer b : timerStore.values())
            total.add(b);
        return total;
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
            s.append("*   ");
            s.append(getTotal(""));
            s.append("   *\n");
            s.append(margins);
            s.append("\n");
        }
        return s.toString();
    }

    public static void main(String[] args){
        Benchmark b = getInstance();
        b.printTimer(10);
        int loop = 0;
        while(true){
            b.resume("COUNT");
            loop++;
            b.pause("COUNT");
            try { Thread.sleep(2000); }
            catch (InterruptedException ex) {}
        }
    }
}
