/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import org.jlab.clas.tracking.validation.data.Hit;

/** 
 * Non-exclusive display flags for a hit relative to a selected object and truth track. 
 * 
 * @author veronique
 */

public final class HitClassification {

    private final Hit hit;
    private final boolean truthSignal;
    private final boolean aiSelected;
    private final boolean onObject;
    private final boolean matchedOnObject;
    private final boolean contaminantOnObject;
    private final boolean missedTruth;
    private final boolean background;

    public HitClassification(
            Hit hit,
            boolean truthSignal,
            boolean aiSelected,
            boolean onObject,
            boolean matchedOnObject,
            boolean contaminantOnObject,
            boolean missedTruth,
            boolean background) {
        this.hit = hit;
        this.truthSignal = truthSignal;
        this.aiSelected = aiSelected;
        this.onObject = onObject;
        this.matchedOnObject = matchedOnObject;
        this.contaminantOnObject = contaminantOnObject;
        this.missedTruth = missedTruth;
        this.background = background;
    }

    public Hit getHit() { return hit; }
    public boolean isTruthSignal() { return truthSignal; }
    public boolean isAiSelected() { return aiSelected; }
    public boolean isOnObject() { return onObject; }
    public boolean isMatchedOnObject() { return matchedOnObject; }
    public boolean isContaminantOnObject() { return contaminantOnObject; }
    public boolean isMissedTruth() { return missedTruth; }
    public boolean isBackground() { return background; }
}
