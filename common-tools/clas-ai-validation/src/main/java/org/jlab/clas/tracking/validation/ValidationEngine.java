/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.data.ValidationObject;
import org.jlab.clas.tracking.validation.io.ValidationEventReader;
import org.jlab.clas.tracking.validation.io.detector.DcAiBankReader;
import org.jlab.clas.tracking.validation.io.detector.DcBankReader;
import org.jlab.clas.tracking.validation.io.mc.McTruthBankReader;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author veronique
 */


public final class ValidationEngine {

    private final ValidationEventReader reader;

    public ValidationEngine() {

        reader = new ValidationEventReader();
        reader.addReader(new McTruthBankReader());
        reader.addReader(new DcBankReader());
        reader.addReader(new DcAiBankReader());
        
    }

    public EventValidation process(DataEvent event) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "DataEvent must not be null");
        }

        ValidationEvent validationEvent =
                reader.readEvent(event);

        List<ValidationObject> objects =
                new ArrayList<>();

        objects.addAll(validationEvent.getClusters());
        objects.addAll(validationEvent.getSegments());
        objects.addAll(validationEvent.getCrosses());
        objects.addAll(validationEvent.getSeeds());
        objects.addAll(validationEvent.getTracks());

        return new EventValidation(
                validationEvent.getRun(),
                validationEvent.getEvent(),
                validationEvent.getHits(),
                objects);
    }
}
