/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import org.jlab.io.base.DataEvent;

/**
 *
 * @author ziegler
 */
public class ConvTracking extends Tracking {
   
    @Override
    public boolean processDataEvent(DataEvent event) {
        super.processDataEvent(event);
        return true;
    }

    @Override
    public boolean init() {
        super.setOutputBankPrefix("");
        double[] aistatus = new double[]{0};
        super.setAistatus(aistatus);
        super.init();
        return true;
    }
    
    
}
