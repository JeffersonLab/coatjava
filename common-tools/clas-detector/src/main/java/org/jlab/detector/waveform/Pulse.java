package org.jlab.detector.waveform;

public record Pulse (float integral, float time, long flags, int index) {};
