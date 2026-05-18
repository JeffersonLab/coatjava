package org.jlab.utils.benchmark;

import java.util.ArrayList;

/**
 *
 * @author baltzell
 */
public class BenchmarkTimerTotal extends BenchmarkTimer {

    ArrayList<BenchmarkTimer> benchmarks = new ArrayList<>();

    public BenchmarkTimerTotal(String name) {
        super(name);
    }

    @Override
    public String toString() {
        double timePerCall = 0.0;
        if (numberOfCalls != 0) timePerCall = getMiliseconds() / numberOfCalls * benchmarks.size();
        return String.format("TIMER (%-12s) : N Calls %12d,  Total Time  = %12.2f sec,  Unit Time = %12.3f msec",
            getName(), numberOfCalls, getSeconds(), timePerCall);
    }

    public void add(BenchmarkTimer b) {
        benchmarks.add(b);
        totalTime += b.totalTime;
        numberOfCalls += b.numberOfCalls;
    }
}
