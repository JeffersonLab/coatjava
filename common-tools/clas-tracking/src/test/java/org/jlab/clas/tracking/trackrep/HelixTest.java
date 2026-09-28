package org.jlab.clas.tracking.trackrep;

import org.jlab.clas.tracking.kalmanfilter.Units;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HelixTest {

    private static Helix helix() {
        return new Helix(0.2, 0.4, 0.004, 20.0, 0.6,
                         1, 5.0, 0.0, 0.0, Units.MM);
    }

    @Test
    public void full3DPlaneIntersectionIncludesLongitudinalTilt() {
        Helix helix = helix();
        double expectedL = 80.0;
        double x = helix.getX(expectedL);
        double y = helix.getY(expectedL);
        double z = helix.getZ(expectedL);
        double nx = 0.8;
        double ny = 0.6;
        double nz = 0.02;

        double offset = 50.0;
        double px = x + nz * offset;
        double py = y;
        double pz = z - nx * offset;

        double actualL = helix.getLAtPlane3D(px, py, pz, nx, ny, nz, px, py);
        double projectedL = helix.getLAtPlane3D(px, py, pz, nx, ny, 0.0, px, py);
        double residual = nx * (helix.getX(actualL) - px)
                        + ny * (helix.getY(actualL) - py)
                        + nz * (helix.getZ(actualL) - pz);

        assertTrue(Double.isFinite(actualL));
        assertEquals(expectedL, actualL, 1.0e-5);
        assertEquals(0.0, residual, 1.0e-7);
        assertTrue("2-D approximation must differ for a tilted plane",
                   Math.abs(projectedL - actualL) > 0.1);
    }

    @Test
    public void full3DPlaneIntersectionHandlesBothSignsAndRotations() {
        for (int turn : new int[] {-1, 1}) {
            Helix helix = new Helix(0.2, 0.4, turn * 0.004, 20.0, 0.6,
                                    turn, 5.0, 0.0, 0.0, Units.MM);
            for (double nz : new double[] {-0.02, 0.02}) {
                for (double angle : new double[] {0.3, 1.7}) {
                    double expectedL = 70.0;
                    double x = helix.getX(expectedL);
                    double y = helix.getY(expectedL);
                    double z = helix.getZ(expectedL);
                    double nx = Math.cos(angle);
                    double ny = Math.sin(angle);

                    double offset = 5.0;
                    double px = x + nz * offset;
                    double py = y;
                    double pz = z - nx * offset;
                    double actualL = helix.getLAtPlane3D(
                            px, py, pz, nx, ny, nz, px, py);
                    double residual = nx * (helix.getX(actualL) - px)
                                    + ny * (helix.getY(actualL) - py)
                                    + nz * (helix.getZ(actualL) - pz);

                    assertTrue("turn=" + turn + " nz=" + nz + " angle=" + angle,
                               Double.isFinite(actualL));
                    assertEquals(expectedL, actualL, 1.0e-4);
                    assertEquals(0.0, residual, 1.0e-7);
                }
            }
        }
    }

    @Test
    public void full3DMatchesLegacyForIdealBarrelPlane() {
        Helix helix = helix();
        double expectedL = 60.0;
        double px = helix.getX(expectedL);
        double py = helix.getY(expectedL);
        double pz = helix.getZ(expectedL);
        double nx = 0.7;
        double ny = Math.sqrt(1.0 - nx * nx);

        double full = helix.getLAtPlane3D(px, py, pz, nx, ny, 0.0, px, py);
        double projected = helix.getLAtPlane3D(
                px, py, pz, nx, ny, 0.0, px, py);

        assertTrue(Double.isFinite(full));
        assertEquals(expectedL, full, 1.0e-7);
        assertEquals(projected, full, 0.0);
    }
}
