/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.Serializable;

/**
 *
 * @author veronique
 */


public class CompactElement implements Serializable {
    private static final long serialVersionUID = 1L;

    public int sector;
    public int layer;
    public int strip;
    public int phiBin;
    public double[] point; // [x,y,z]
    
    public CompactElement() {}
    public CompactElement(int sector, int layer, int strip, int phiBin, double x, double y, double z) {
        this.sector = sector;
        this.layer = layer;
        this.strip = strip;
        this.phiBin = phiBin;
        this.point = new double[]{x, y, z};
    }
    public CompactElement(int sector, int layer, int strip) {
        this.sector = sector;
        this.layer = layer;
        this.strip = strip;
    }
    @Override
    public String toString() {
//        return String.format(
//            "CompactElement[sector=%d, layer=%d, strip=%d, phiBin=%d, point=(%.3f, %.3f, %.3f)]",
//            sector, layer, strip, phiBin, point[0], point[1], point[2]
//        );
        return String.format(
            "CompactElement[sector=%d, layer=%d, strip=%d]",
            sector, layer, strip
        );
    }
}
