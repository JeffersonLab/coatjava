/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

/**
 *
 * @author veronique
 */

/** Reconstruction stage represented as a set of contributing measured hits. */
public enum TrackingObjectType {
    CLUSTER,
    CROSS,
    SEGMENT,
    SEED,
    CANDIDATE,
    TRACK,
    SUGGESTION
}
