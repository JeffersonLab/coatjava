package org.jlab.detector.pulse;

public class Pulse {

    public float integral;
    public float time;
    public long flags;
    public int id;

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
