/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.Serializable;
import org.jlab.geom.prim.Point3D;

/**
 *
 * @author veronique
 */

public class RoadElement implements Serializable {
    private static final long serialVersionUID = 1L;
    public int layer;
    public int strip;
    public int phiBin;
    public Point3D point;
}
