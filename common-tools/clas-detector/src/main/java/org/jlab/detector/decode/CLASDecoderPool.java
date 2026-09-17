package org.jlab.detector.decode;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author baltzell
 */
public class CLASDecoderPool extends ConcurrentLinkedQueue<CLASDecoder> {
   
    int sharedConstantsManagers = 64;

    public CLASDecoderPool(int size, String variation, String timestamp) {

        super();

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
            
            add(d);
        }
    }

    static CLASDecoderPool instance = null;

    public static CLASDecoderPool getInstance() {
        if (instance == null) 
            instance = new CLASDecoderPool(64,"default",null);
        return instance;
    }
}