/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.data;

import java.util.List;
import java.util.Set;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.TrackingObjectType;

/**
 *
 * @author veronique
 */


public interface ValidationObject {

    int getId();

    TrackingObjectType getType();

    String getAlgorithm();

    List<HitKey> getHitKeys();

    Set<Integer> getDetectorScope();

    default int getNumberOfHits() {
        return getHitKeys().size();
    }
    
    public record ObjectKey(
        String algorithm,
        int detector,
        TrackingObjectType type,
        int id) {
    }
}
