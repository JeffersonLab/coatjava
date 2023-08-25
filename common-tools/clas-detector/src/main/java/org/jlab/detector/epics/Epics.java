package org.jlab.detector.epics;

import org.jlab.jnp.utils.json.JsonObject;

/**
 * Stores Epics data in Json format and the associated Unix time
 * @author devita
 */
public class Epics {
    
    private JsonObject epicsReadout = null;
    
    private int unixTime = 0;
    
    public JsonObject getEpicsReadout() {
        return epicsReadout;
    }
    
    public int getUnixTime() {
        return unixTime;
    }

    public void setEpicsReadout(JsonObject epicsreadout) {
        this.epicsReadout = epicsreadout;
    }

    public void setUnixTime(int unixTime) {
        this.unixTime = unixTime;
    }
    
    public int getInt(String name, int defaultvalue) {
        return this.epicsReadout.getInt(name, defaultvalue);
    }
    
    public double getDouble(String name, double defaultvalue) {
        return this.epicsReadout.getDouble(name, defaultvalue);
    }
    
}
