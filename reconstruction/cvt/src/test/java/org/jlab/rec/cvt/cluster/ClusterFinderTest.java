package org.jlab.rec.cvt.cluster;

import java.util.Arrays;
import java.util.List;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.hit.Strip;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ClusterFinderTest {

    private static Hit hit(int strip, double energy) {
        Strip sensorStrip = new Strip(strip, energy, 0);
        sensorStrip.setLine(new Line3D(0, strip, 0, 1, strip, 0));
        sensorStrip.setNormal(new Vector3D(0, 0, 1));
        return new Hit(DetectorType.BST, BMTType.UNDEFINED, 1, 1, sensorStrip);
    }

    @Test
    public void includesHighestRepresentableStrip() {
        Hit edge = hit(ClusterFinder.NSTRIP, 10);
        List<Cluster> clusters = ClusterFinder.findClusters(Arrays.asList(edge));

        assertEquals(1, clusters.size());
        assertEquals(1, clusters.get(0).size());
        assertEquals(ClusterFinder.NSTRIP, clusters.get(0).get(0).getStrip().getStrip());
        assertEquals(1, edge.getAssociatedClusterID());
    }

    @Test
    public void poolExhaustionFallsBackToAnAllocatedMap() {
        ClusterFinder.HitMapPool localPool = new ClusterFinder.HitMapPool();
        for (int i = 0; i < 65; i++) {
            Hit[][][] map = localPool.poll();
            assertEquals(ClusterFinder.NSTRIP, map.length);
        }
    }
}
