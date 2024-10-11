package org.jlab.detector.pulse;

import java.util.Arrays;

/**
 *
 * @author baltzell
 */
public class PulseTest {

    public static void main(String[] args) {
        short[] samples = {9,10,11,8,1000,100,10,10,10,10,10,2000,200,10,10};
        Mode1Extractor e = new Mode1Extractor(5f,10f,2,2);
        System.out.println(e.extract(2,samples));
        Mode7Extractor s = new Mode7Extractor(5f,10f,2,2);
        System.out.println(s.extract(2,samples));
        System.out.println(Arrays.asList(0,1,2,3,4,5,6,7,8,9).stream().map(x -> x*10).reduce((a, b) -> a + b));
    }
    
}
