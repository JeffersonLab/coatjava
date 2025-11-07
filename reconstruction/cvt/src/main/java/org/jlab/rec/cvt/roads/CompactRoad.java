/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.Serializable;
import java.util.*;
/**
 *
 * @author veronique
 */


public class CompactRoad implements Serializable {
    private static final long serialVersionUID = 1L; 
    public int q;  // charge sign
    public double p;
    public double theta;
    public double phi;
    public double z;
    public List<CompactElement> elements = new ArrayList<>();
    
    public String getIdentifier() {
        String s = "r"+q+"_"+p+"_"+theta+"_"+phi+"_"+z;
        return s;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CompactRoad[q=%d p=%6.1f theta=%6.1f phi=%6.1f z=%6.3f, elements=%d]%n",  q, p, theta, phi, z, elements.size()));
        for (CompactElement e : elements) {
            sb.append("  ").append(e.toString()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
