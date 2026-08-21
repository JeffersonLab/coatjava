package org.jlab.detector.decode;

import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 * @author baltzell
 */
public class CLASDecoderPool {
    
    ArrayBlockingQueue<CLASDecoder> pool;

    int sharedConstantsManagers = 64;

    public CLASDecoderPool(int size, String variation, String timestamp) {

        pool = new ArrayBlockingQueue<>(size);
        
        CLASDecoder d0 = null;
        
        for (int i=0; i<size; i++) {
            
            CLASDecoder d;
            
            if (i % sharedConstantsManagers == 0) {
                d0 = new CLASDecoder();
                if (variation != null) d0.setVariation(variation);
                if (timestamp != null) d0.setTimestamp(timestamp);
                d = d0;
            }
            else d = new CLASDecoder(d0);
            
            pool.add(d);
        }
    }

    public CLASDecoder take() throws InterruptedException {
        return pool.take();
    }

    public void put(CLASDecoder decoder) throws InterruptedException {
        pool.put(decoder);
    }

}