/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.data;

/**
 * Association between a simulated detector hit and its MC particle.
 * 
 * @author veronique
 */

public final class McTruthAssociation {

    private final int detector;
    private final int truthHitId;
    private final int truthTrackId;
    private final int pid;

    private final double x;
    private final double y;
    private final double z;

    private final double time;
    private final double energyDeposit;

    public McTruthAssociation(
            int detector,
            int truthHitId,
            int truthTrackId,
            int pid,
            double x,
            double y,
            double z,
            double time,
            double energyDeposit) {

        this.detector = detector;
        this.truthHitId = truthHitId;
        this.truthTrackId = truthTrackId;
        this.pid = pid;

        this.x = x;
        this.y = y;
        this.z = z;

        this.time = time;
        this.energyDeposit = energyDeposit;
    }

    public int getDetector() {
        return detector;
    }

    public int getTruthHitId() {
        return truthHitId;
    }

    public int getTruthTrackId() {
        return truthTrackId;
    }

    public int getPid() {
        return pid;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getTime() {
        return time;
    }

    public double getEnergyDeposit() {
        return energyDeposit;
    }

    public TruthHitKey key() {
        return new TruthHitKey(
                detector,
                truthHitId);
    }
}