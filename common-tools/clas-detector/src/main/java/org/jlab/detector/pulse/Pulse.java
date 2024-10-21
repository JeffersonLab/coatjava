package org.jlab.detector.pulse;

import org.jlab.detector.base.DetectorDescriptor;

public class Pulse {

    public DetectorDescriptor descriptor;
    public long timestamp;
    public float integral;
    public float time;
    public long flags;
    public int id;

    public Pulse(DetectorDescriptor d) {
        this.descriptor = d;
    }

    public Pulse(float integral, float time, long flags, int id) {
        this.integral = integral;
        this.time = time;
        this.flags = flags;
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("org.jlab.detector.Pulse: integral=%f time=%f flags=%d id=%d",
                integral, time, flags, id);
    }

}    