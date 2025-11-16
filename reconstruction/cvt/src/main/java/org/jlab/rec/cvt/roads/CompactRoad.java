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
    
    public List<CompactElement> elements = new ArrayList<>();
    public int paddle;
    
    
    public CompactRoad() {}
   
    public CompactRoad(List<CompactElement> elements) {
        this.elements = elements;
    }
    public CompactRoad(CompactElement[] elementsArray) {
        elements=new ArrayList<>();
        for(int i = 0; i<elementsArray.length; i++) {
            elements.add(elementsArray[i]);
        }
    }
    /**
    * Checks if two CompactRoads are identical.
    * Two roads are considered identical if:
    *   - They have the same charge (q)
    *   - The same momentum p, theta, phi
    *   - The same number of elements
    *   - Each element has the same sector, layer, and strip in the same order
    */
   public static boolean areRoadsIdentical(CompactRoad r1, CompactRoad r2) {
       if (r1 == r2) return true; // same reference
       if (r1 == null || r2 == null) return false;

       if (r1.elements.size() != r2.elements.size()) return false;

       for (int i = 0; i < r1.elements.size(); i++) {
           CompactElement e1 = r1.elements.get(i);
           CompactElement e2 = r2.elements.get(i);
           if (e1.layer != e2.layer) return false;
           if (e1.sector != e2.sector) return false;
           if (e1.strip != e2.strip) return false;
       }

       return true;
   }
    

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CompactRoad[elements=%d]%n", elements.size()));
        for (CompactElement e : elements) {
            sb.append("  ").append(e.toString()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
