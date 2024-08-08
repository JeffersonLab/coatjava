/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.hit;

/**
 *
 * @author ziegler
 */
import java.util.Random;

public class LayerEfficiency {

    /**
     * @return the efficiency
     */
    public double getEfficiency() {
        return efficiency;
    }

    /**
     * @param efficiency the efficiency to set
     */
    public void setEfficiency(double efficiency) {
        this.efficiency = efficiency;
    }
    
    private double efficiency;
    private Random randomGenerator;

    // Constructor
    public LayerEfficiency() {
        this.randomGenerator = new Random();
        this.randomGenerator.setSeed(42);
    }
    
    // Method to simulate passing hits through the detector layer
    public boolean passHit() {
        // Generate a random number between 0 and 1
        double randomValue = randomGenerator.nextDouble();
        // Check if the random value is less than the efficiency
        // If yes, the hit passes through the layer
        return randomValue < getEfficiency();
    }

    public static void main(String[] args) {
        // Example usage
        double efficiency = 0.8; // Example efficiency value
        LayerEfficiency detectorLayer = new LayerEfficiency();
        detectorLayer.setEfficiency(efficiency);
        // Simulate passing 10 hits through the detector layer
        for (int i = 0; i < 10; i++) {
            boolean hitPassed = detectorLayer.passHit();
            if (hitPassed) {
                System.out.println("Hit passed through the detector layer.");
            } else {
                System.out.println("Hit did not pass through the detector layer.");
            }
        }
    }
}