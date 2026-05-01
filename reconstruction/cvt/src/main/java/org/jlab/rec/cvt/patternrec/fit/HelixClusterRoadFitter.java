/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.patternrec.fit;

import java.util.*;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.cross.CrossMaker;
import org.jlab.rec.cvt.patternrec.fit.HelixClusterFitter.FitResult;

/**
 *
 * @author veronique
 */

public class HelixClusterRoadFitter {

    CrossMaker cm = new CrossMaker();
    
    public FitResult fitRoad(Set<Cluster> seed, int polarity, double xB, double yB) {

        List<Cluster> clusters = new ArrayList<>(seed);
        clusters.sort(Comparator.comparingInt(Cluster::getLayer));

        List<Cross> crosses = get3DPointsFromCLusters(seed, xB, yB);
        List<HelixClusterFitter.Segment> segs = new ArrayList<>();

        double targetC = Geometry.getInstance().getTargetZOffset();
        double targetL = Geometry.getInstance().getTargetHalfLength();
        // Beam line segment
        segs.add(new HelixClusterFitter.Segment(
            new HelixClusterFitter.Vec3(xB, yB, targetC - targetL),
            new HelixClusterFitter.Vec3(xB, yB, targetC + targetL)
        ));

        // Cluster segments
        for (Cluster c : clusters) {
            Point3D p0 = c.getLine().origin();
            Point3D p1 = c.getLine().end();
            segs.add(new HelixClusterFitter.Segment(
                new HelixClusterFitter.Vec3(p0.x(), p0.y(), p0.z()),
                new HelixClusterFitter.Vec3(p1.x(), p1.y(), p1.z())
            ));
        }
        Integer turningSign = 1;

        FitResult fit = HelixClusterFitter.fitRoadAsCVTHelix(
            crosses, segs, targetC, targetL,
            xB, yB, polarity, turningSign
        );

        //HelixClusterFitter.debugFitResult("Seed " + seed.toString(), fit);
        return fit;
    }

    public List<Cross> get3DPointsFromCLusters(Set<Cluster> seed, double xB, double yB){
        List<Cluster> clusters = new ArrayList<>(seed);
        clusters.sort(Comparator.comparingInt(Cluster::getLayer));

        List<Cross> crosses = new ArrayList<>();
        Cross cent = new Cross(DetectorType.UNDEFINED, BMTType.UNDEFINED, 0, 0, 0);
        cent.setPoint(new Point3D(xB, yB, 0));
        crosses.add(cent);
        crosses.addAll(cm.findCrosses(clusters).get(0));
        return crosses;
    }
}