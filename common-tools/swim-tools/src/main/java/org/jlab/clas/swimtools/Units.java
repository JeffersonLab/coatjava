/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.swimtools;

/**
 *
 * @author ziegler
 */
public enum Units {
    MM (1000.0),
    CM (100.0),
    M  (1.0);
    private final double unit;  

    Units(double unit) {
        this.unit = unit;
    }

    public double value() { 
        return unit; 
    }

    public static Units getUnit(double value) {
        for (Units unit : Units.values()) {
            if (unit.unit == value) {
                return unit;
            }
        }
        return Units.CM;
    }
}

