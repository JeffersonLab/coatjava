/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.List;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.ValidationObject;
import org.jlab.io.base.DataEvent;

/** 
 * Legacy bridge for detector-specific or bank-level adapters. 
 * 
 * @author veronique
 */
public interface TrackingEventAdapter {
    List<Hit> readHits(DataEvent event);
    List<ValidationObject> readObjects(DataEvent event, List<Hit> hits);
}
