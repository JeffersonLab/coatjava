package org.jlab.io.stream;

import java.util.TreeMap;

/**
 *
 * @author gavalian
 */
public interface EvioStreamObject {
    int  getType();
    TreeMap<Integer,Object> getStreamData();
    void setStreamData(TreeMap<Integer,Object> data);
}
