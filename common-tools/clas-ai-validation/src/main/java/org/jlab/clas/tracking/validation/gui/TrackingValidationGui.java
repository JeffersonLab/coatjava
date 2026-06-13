/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt
 * to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java
 * to edit this template
 */
package org.jlab.clas.tracking.validation.gui;

/**
 * Standalone launcher for the tracking-validation event browser.
 *
 * The frame owns file selection, event processing, and Previous/Next
 * navigation. This launcher therefore does not create a second validation
 * engine or process an event independently.
 *
 * @author veronique
 */
public final class TrackingValidationGui {

    private TrackingValidationGui() {
    }

    public static void main(String[] args) {

        System.setProperty(
                "CLAS12DIR",
                "/Users/veronique/BASE/Code/coatjava");

        TrackingValidationFrame.main(args);
    }
}
