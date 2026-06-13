/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.io;

import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.io.base.DataEvent;

/**
 * Converts detector- or algorithm-specific reconstruction banks into the
 * detector-independent validation data model.
 * 
 * @author veronique
 */

public interface TrackingBankReader {

    boolean isApplicable(DataEvent event);

    default void readTruth(
            DataEvent event,
            ValidationEvent output) {
    }

    default void readHits(
            DataEvent event,
            ValidationEvent output) {
    }

    default void readClusters(
            DataEvent event,
            ValidationEvent output) {
    }

    default void readSegments(
            DataEvent event,
            ValidationEvent output) {
    }

    default void readCrosses(
            DataEvent event,
            ValidationEvent output) {
    }

    default void readSeeds(
            DataEvent event,
            ValidationEvent output) {
    }

    default void readTracks(
            DataEvent event,
            ValidationEvent output) {
    }
}