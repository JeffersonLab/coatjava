package org.jlab.utils.benchmark;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author gavalian
 */
public class Benchmark {
    
    private static final Benchmark benchmarkInstance = new Benchmark();
    private final Map<String,BenchmarkTimer> timerStore = new LinkedHashMap<>();
    private Timer updateTimer = null;

    public Benchmark(){}
    
    public static Benchmark getInstance(){
        return benchmarkInstance;
    }
    
    public void printTimer(int seconds){
        TimerTask timerTask = new TimerTask() { 
            @Override
            public void run() { System.out.println(benchmarkInstance); }
        };
        System.err.println("KITTY");
        updateTimer = new Timer("Benchmark", true);
        updateTimer.scheduleAtFixedRate(timerTask, 0, 1000*seconds);
        System.err.println("DOGGY");
    }
    
    public void reset(){
        for (BenchmarkTimer bt : timerStore.values())
            bt.reset(); 
    }
    
    public void addTimer(String name){
        if (!timerStore.containsKey(name))
            timerStore.put(name, new BenchmarkTimer(name));
        else
            System.err.println("[Benchmark] -----> error. timer with name ("+ name + ") already exists");
    }
    
    public void pause(String name){
        if (!timerStore.containsKey(name))
            timerStore.put(name, new BenchmarkTimer(name));
        else
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
        BenchmarkTimer total = new BenchmarkTimer(name);
        for (BenchmarkTimer b : timerStore.values())
            total.add(b);
        return total;
    }

    @Override
    public String toString(){
        StringBuilder s = new StringBuilder();
        Collection<BenchmarkTimer> timers = timerStore.values();
        if (!timers.isEmpty()) {
            int len = timers.iterator().next().toString().length();
            char[] asterix = new char[len+8];
            Arrays.fill(asterix,'*');
            String margins = new String(asterix);
            s.append(margins);
            s.append("\n");
            s.append("*     BENCHMARK  RESULTS \n");
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
