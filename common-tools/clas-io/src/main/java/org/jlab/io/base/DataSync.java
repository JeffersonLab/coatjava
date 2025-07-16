package org.jlab.io.base;

/**
 *
 * @author gavalian
 */
public interface DataSync {
    
    void open(String file);
    void writeEvent(DataEvent event);
    void close();
    
    DataEvent createEvent();
}
