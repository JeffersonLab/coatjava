package org.jlab.detector.pulse;

public interface IPulse {
    public float integral();
    public float time();
    public float flags();
    public int id();
}
