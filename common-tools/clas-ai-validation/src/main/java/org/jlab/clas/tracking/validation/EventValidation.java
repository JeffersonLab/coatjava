/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.ValidationObject;

/** 
 * Truth-matching results for all reconstructed tracking objects in one event. 
 * 
 * @author veronique
 */

public final class EventValidation {

    private final int run;
    private final int event;
    private final TruthIndex truthIndex;
    private final List<ValidationObject> objects;
    private final List<MatchResult> matches;

    public EventValidation(
            int run,
            int event,
            Collection<Hit> hits,
            Collection<? extends ValidationObject> objects) {

        Objects.requireNonNull(hits, "hits must not be null");
        Objects.requireNonNull(objects, "objects must not be null");

        this.run = run;
        this.event = event;
        this.truthIndex = new TruthIndex(hits);

        List<ValidationObject> objectList = new ArrayList<>();
        for (ValidationObject object : objects) {
            if (object != null) {
                objectList.add(object);
            }
        }
        this.objects = Collections.unmodifiableList(objectList);

        List<MatchResult> resultList = new ArrayList<>();
        for (ValidationObject object : this.objects) {
            resultList.add(TruthMatcher.evaluate(object, truthIndex));
        }
        this.matches = Collections.unmodifiableList(resultList);
    }

    public int getRun() { return run; }
    public int getEvent() { return event; }
    public TruthIndex getTruthIndex() { return truthIndex; }
    public List<ValidationObject> getObjects() { return objects; }
    public List<MatchResult> getMatches() { return matches; }
}
