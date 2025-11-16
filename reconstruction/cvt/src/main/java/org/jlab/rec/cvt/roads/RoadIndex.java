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
public class RoadIndex {
    
    public record UberEntry(int paddle, long offsetBits, int nElements) implements Serializable {}
    //public record RoadIndexEntry(long roadId) implements Serializable {}


}
