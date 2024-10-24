package org.jlab.detector.pulse;

import org.jlab.detector.base.DetectorDescriptor;

public class Pulse {

    public DetectorDescriptor descriptor;
    public long timestamp;
    public float integral;
    public float time;
    public long flags;
    public int id;

    /**
     * Units are the same as the raw units of the samples.
     * @param integral pulse integral, pedestal-subtracted
     * @param time pulse time
     * @param flags user flags
     * @param id link to row in source bank
     */
    public Pulse(float integral, float time, long flags, int id) {
        this.integral = integral;
        this.time = time;
        this.flags = flags;
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("pulse: integral=%f time=%f flags=%d id=%d",
                integral, time, flags, id);
    }

}    