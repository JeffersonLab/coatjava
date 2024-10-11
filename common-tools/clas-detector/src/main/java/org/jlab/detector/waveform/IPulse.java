package org.jlab.detector.waveform;

public interface IPulse {
    public float integral();
    public float time();
    public float flags();
    public int id();
}
