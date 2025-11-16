/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.util.HashSet;
import java.util.Set;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.geom.prim.Line3D;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.Geometry;

/**
 *
 * @author veronique
 */
public class Util {
    
    public static int getPhiBin(double phiDeg) {
        final double BIN_WIDTH = 1;
        final int N_BINS = (int) (360.0 / BIN_WIDTH); 

        // Normalize phi to [0, 360)
        phiDeg = phiDeg % 360.0;
        if (phiDeg < 0) phiDeg += 360.0;
        // Find bin index [0..179]
        int bin = (int) Math.floor(phiDeg / BIN_WIDTH);
        if (bin >= N_BINS) bin = N_BINS - 1;  // safety
        return bin;
    }
    
    public static int getPhiBin(int layer, int sector, int svtStrip) {
        Line3D l = Geometry.getInstance().getSVT().getStrip(layer, sector, svtStrip);
        double phi = Math.atan2(l.origin().y(),l.origin().x());
        double phiDeg = Math.toDegrees(phi);
        int bin = getPhiBin(phiDeg);
        return bin;
    }
    
    public static Set<Integer> getCTOFHitPaddles(DataEvent event) {
        Set<Integer> paddles = new HashSet<>();
        String detADC = "CTOF";
        detADC += "::adc";
        String detTDC = "CTOF";
        detTDC += "::tdc";

        if (event.hasBank(detADC) == false && event.hasBank(detTDC) == false) {
            return paddles;
        }
        if (event.hasBank(detADC) == true) {
            RawDataBank bank = new RawDataBank(detADC);
            bank.read(event);
            int bankSize = bank.rows();
            for (int i = 0; i < bankSize; i++) {
                paddles.add(bank.getShort("component", i));
            }
        }
        if (event.hasBank(detTDC) == true) {
            RawDataBank bank = new RawDataBank(detTDC);
            bank.read(event);
            int bankSize = bank.rows();
            for (int i = 0; i < bankSize; i++) {
                paddles.add(bank.getShort("component", i));
            }
        }
        return paddles;
    }

    
}
