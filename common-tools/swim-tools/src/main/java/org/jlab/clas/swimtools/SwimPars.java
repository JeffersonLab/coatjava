package org.jlab.clas.swimtools;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author ziegler
 */
public class SwimPars {
    private Units inputUnits = Units.CM;
    private Units outputUnits = Units.M;
    
    private double _x0;
    private double _y0;
    private double _z0;
    private double _phi;
    private double _theta;
    private double _pTot;
    private double _rMax = 5 + 3; // increase to allow swimming to outer
    // detectors
    private double _maxPathLength = 9;
    private boolean SwimUnPhys = false; //Flag to indicate if track is swimmable
    private int _charge;

    private double SWIMZMINMOM = 0.75; // GeV/c
    private double MINTRKMOM = 0.05; // GeV/c
    private double accuracy = 20e-6; // 20 microns
    private double tolerance = 10e-6; // 10 microns
    private double stepSize = 5.00 * 1.e-4; // 500 microns
    
    /**
     * @return the inputUnits
     */
    public Units getInputUnits() {
        return inputUnits;
    }

    /**
     * @param inputUnits the inputUnits to set
     */
    public void setInputUnits(Units inputUnits) {
        this.inputUnits = inputUnits;
    }

    /**
     * @return the outputUnits
     */
    public Units getOutputUnits() {
        return outputUnits;
    }

    /**
     * @param outputUnits the outputUnits to set
     */
    private void setOutputUnits(Units outputUnits) {
        this.outputUnits = outputUnits;
        _rMax*=outputUnits.value();
        _maxPathLength*=outputUnits.value();
        accuracy*=outputUnits.value();
        tolerance*=outputUnits.value();
        stepSize*=outputUnits.value();
    }

}
