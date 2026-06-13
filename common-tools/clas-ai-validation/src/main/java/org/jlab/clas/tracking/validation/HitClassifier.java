/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.ValidationObject;

/** 
 * Classifies event hits relative to one selected tracking object and truth track. 
 * 
 * @author veronique
 */

public final class HitClassifier {

    private HitClassifier() { }

    public static List<HitClassification> classify(
            TruthIndex truth,
            ValidationObject object,
            int selectedTruthTrackId) {

        if (truth == null) {
            throw new IllegalArgumentException("truth must not be null");
        }

        Set<HitKey> onObject = object == null
                ? Collections.emptySet()
                : new HashSet<>(object.getHitKeys());

        List<HitClassification> result = new ArrayList<>();
        for (Hit hit : truth.getHits()) {
            boolean signal = selectedTruthTrackId > 0
                    && hit.getTruthTrackId() == selectedTruthTrackId;
            boolean used = onObject.contains(hit.key());
            boolean matched = used && signal;
            boolean contaminant = used && !signal;
            boolean missed = signal && !used;
            boolean background = !signal && !used;

            result.add(new HitClassification(
                    hit,
                    signal,
                    hit.isAiSelected(),
                    used,
                    matched,
                    contaminant,
                    missed,
                    background));
        }
        return result;
    }
}
