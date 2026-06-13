/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.data;

/**
 * Generated Monte Carlo particle.
 * 
 * @author veronique
 */

public final class Particle {

    private final int trackId;
    private final int pid;

    private final double px;
    private final double py;
    private final double pz;

    private final double vx;
    private final double vy;
    private final double vz;

    private final double time;

    public Particle(
            int trackId,
            int pid,
            double px,
            double py,
            double pz,
            double vx,
            double vy,
            double vz,
            double time) {

        this.trackId = trackId;
        this.pid = pid;

        this.px = px;
        this.py = py;
        this.pz = pz;

        this.vx = vx;
        this.vy = vy;
        this.vz = vz;

        this.time = time;
    }

    public int getTrackId() {
        return trackId;
    }

    public int getPid() {
        return pid;
    }

    public double getPx() {
        return px;
    }

    public double getPy() {
        return py;
    }

    public double getPz() {
        return pz;
    }

    public double getMomentum() {
        return Math.sqrt(
                px * px
                + py * py
                + pz * pz);
    }

    public double getVx() {
        return vx;
    }

    public double getVy() {
        return vy;
    }

    public double getVz() {
        return vz;
    }

    public double getTime() {
        return time;
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationParticle[trackId=%d, pid=%d, "
                        + "p=(%.4f, %.4f, %.4f), "
                        + "v=(%.4f, %.4f, %.4f), time=%.4f]",
                trackId,
                pid,
                px,
                py,
                pz,
                vx,
                vy,
                vz,
                time);
    }
}
