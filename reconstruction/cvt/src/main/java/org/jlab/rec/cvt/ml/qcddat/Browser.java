/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml.qcddat;

import javafx.application.Application;
/**
 *
 * @author veronique
 */
public class Browser {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Viewer <input.hipo>");
            System.err.println("Example: java Viewer file.hipo");
            System.exit(1);
        }

        String inputFile = args[0];

        Viewer.configure(inputFile);
        Application.launch(Viewer.class);
    }
}