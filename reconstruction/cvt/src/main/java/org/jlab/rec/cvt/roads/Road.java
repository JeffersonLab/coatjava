/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.jlab.geom.prim.Point3D;

/**
 *
 * @author veronique
 */
public class Road implements Serializable {
    private static final long serialVersionUID = 1L;
    public Map<Integer, RoadElement> road = new HashMap<>();

    public static Road expand(CompactRoad c) {
        Road road = new Road();
        for (CompactElement ce : c.elements) {
            RoadElement re = new RoadElement();
            re.layer = ce.layer;
            re.strip = ce.strip;
            re.phiBin = ce.phiBin;
            re.point = new Point3D(ce.point[0], ce.point[1], ce.point[2]);
            road.road.put(re.layer, re);
        }
        return road;
    }
}
