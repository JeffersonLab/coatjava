package org.jlab.utils.benchmark;

import org.junit.Test;

public class BenchmarkTest {

    @Test
    public void multi() throws InterruptedException {
        Benchmark.getInstance().resume(1,"test");
        Benchmark.getInstance().resume(2,"test");
        Thread.sleep(1000);
        Benchmark.getInstance().pause(1,"test");
        Benchmark.getInstance().pause(2,"test");
        System.out.println(Benchmark.getInstance());
    } 
    
}
