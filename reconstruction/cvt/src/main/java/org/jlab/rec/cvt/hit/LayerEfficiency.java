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
    private Random randomGenerator;
    // Constructor
    public LayerEfficiency() {
        randomGenerator = new Random(42);
    }
   
    // Method to simulate passing hits through the detector layer
    public boolean passLayer(double efficiency) {
        // Generate a random number between 0 and 1
        double randomValue =randomGenerator.nextDouble();
        // Check if the random value is less than the efficiency
        // If yes, the hit passes through the layer
        return randomValue < efficiency;
    }

    public static void main(String[] args) {
        
        // Example usage
        double efficiency = 0.0; // Example efficiency value
        LayerEfficiency detectorLayer = new LayerEfficiency();
        // Simulate passing 10 hits through the detector layer
        
        int loop = 100000;
        for(int e =10; e<100; e++) {
            efficiency = (double)e/100.0;
            int cnt =0;
            for (int i = 0; i < loop; i++) {
                boolean hitPassed = detectorLayer.passLayer(efficiency);
                if (hitPassed) {
                    //System.out.println("Hit passed through the detector layer.");
                    cnt++;
                } else {
                    //System.out.println("Hit did not pass through the detector layer.");
                }
            }
            System.out.println((efficiency -(double)cnt/(double) loop));
        }
    }
}