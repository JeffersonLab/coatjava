/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.decay.analysis;

import org.jlab.clas.swimtools.MagFieldsEngine;

/**
 *
 * @author veronique
 * Tools specifically designed for reconstructing decay channels.
 */
public class AnalysisEngine extends Analysis {
    
    public AnalysisEngine() {
        super("DECAYS");
    }
    
    public static void main(String[] args) {

        MagFieldsEngine enf = new MagFieldsEngine();
        enf.init();
        
        AnalysisEngine ae = new AnalysisEngine();
        ae.loadConfiguration();
        
        
    }

}
