/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

/** 
 * Purity and efficiency counters for one detector or for the combined system. 
 * @author veronique
 */

public final class DetectorMetrics {
    private final int objectHits;
    private final int matchedHits;
    private final int truthHits;

    public DetectorMetrics(int objectHits, int matchedHits, int truthHits) {
        this.objectHits = objectHits;
        this.matchedHits = matchedHits;
        this.truthHits = truthHits;
    }

    public int getObjectHits() { 
        return objectHits; 
    }
    public int getMatchedHits() { 
        return matchedHits; 
    }
    public int getTruthHits() { 
        return truthHits; 
    }
    public int getContaminatingHits() { 
        return Math.max(0, objectHits - matchedHits); 
    }
    public int getMissedHits() { 
        return Math.max(0, truthHits - matchedHits); 
    }
    public double getPurity() { 
        return objectHits == 0 ? 0.0 : (double) matchedHits / objectHits; 
    }
    public double getEfficiency() { 
        return truthHits == 0 ? 0.0 : (double) matchedHits / truthHits; 
    }
    public double getFakeFraction() { 
        return objectHits == 0 ? 0.0 : (double) getContaminatingHits() / objectHits; 
    }
}
