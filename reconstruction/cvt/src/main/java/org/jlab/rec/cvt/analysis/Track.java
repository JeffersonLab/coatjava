/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.analysis;

import java.util.HashMap;
import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author ziegler
 */
public class Track {
    public boolean isMCGen;
    public boolean isBgFree;
    public int tid;
    public int q;
    public double p;
    public double theta;
    public double phi;
    public boolean isAccidental = true;
    Track(int id, int q, double p, double theta, double phi) {
        this.tid = id;
        this.q = q;
        this.p = p;
        this.theta = theta;
        this.phi = phi;
    }
    
}
