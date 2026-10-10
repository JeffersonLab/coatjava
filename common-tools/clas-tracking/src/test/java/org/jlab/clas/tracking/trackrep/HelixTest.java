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

    private static double distanceToAxis(Helix helix, double l,
                                         double ax, double ay, double az,
                                         double ux, double uy, double uz) {
        double un = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= un;
        uy /= un;
        uz /= un;
        double wx = helix.getX(l) - ax;
        double wy = helix.getY(l) - ay;
        double wz = helix.getZ(l) - az;
        double along = wx * ux + wy * uy + wz * uz;
        double dx = wx - along * ux;
        double dy = wy - along * uy;
        double dz = wz - along * uz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    public void full3DCylinderIntersectionUsesDisplacedTiltedAxis() {
        double ax = 2.0;
        double ay = -1.5;
        double az = -300.0;
        double ux = 0.003;
        double uy = -0.004;
        double uz = 1.0;

        for (int turn : new int[] {-1, 1}) {
            Helix helix = new Helix(0.2, 0.4, turn * 0.004, 20.0, 0.6,
                                    turn, 5.0, 0.0, 0.0, Units.MM);
            double expectedL = 70.0;
            double radius = distanceToAxis(helix, expectedL, ax, ay, az, ux, uy, uz);
            double legacyL = helix.getLAtR(radius);
            double actualL = helix.getLAtCylinder3D(
                    ax, ay, az, ux, uy, uz, radius, legacyL);

            assertTrue("turn=" + turn, Double.isFinite(actualL));
            assertEquals(expectedL, actualL, 1.0e-5);
            assertEquals(radius,
                    distanceToAxis(helix, actualL, ax, ay, az, ux, uy, uz), 1.0e-7);
            assertTrue("the beam-axis crossing must miss the aligned cylinder",
                    Math.abs(distanceToAxis(
                            helix, legacyL, ax, ay, az, ux, uy, uz) - radius) > 0.1);
        }
    }

    @Test
    public void full3DCylinderIntersectionIsIndependentOfAxisScaleAndOrigin() {
        Helix helix = helix();
        double expectedL = 80.0;
        double ax = -1.2;
        double ay = 2.3;
        double az = -250.0;
        double ux = -0.002;
        double uy = 0.005;
        double uz = 1.0;
        double radius = distanceToAxis(helix, expectedL, ax, ay, az, ux, uy, uz);
        double seed = helix.getLAtR(radius);

        double nominal = helix.getLAtCylinder3D(
                ax, ay, az, ux, uy, uz, radius, seed);
        double scaledDirection = helix.getLAtCylinder3D(
                ax, ay, az, 100.0 * ux, 100.0 * uy, 100.0 * uz, radius, seed);
        double shiftedOrigin = helix.getLAtCylinder3D(
                ax + 37.0 * ux, ay + 37.0 * uy, az + 37.0 * uz,
                ux, uy, uz, radius, seed);

        assertEquals(expectedL, nominal, 1.0e-5);
        assertEquals(nominal, scaledDirection, 1.0e-10);
        assertEquals(nominal, shiftedOrigin, 1.0e-10);
    }

    @Test
    public void full3DCylinderMatchesClosedFormForBeamAxis() {
        Helix helix = helix();
        double radius = Math.hypot(helix.getX(60.0), helix.getY(60.0));
        double legacy = helix.getLAtR(radius);
        double full = helix.getLAtCylinder3D(
                0.0, 0.0, -500.0, 0.0, 0.0, 1000.0, radius, legacy);

        assertTrue(Double.isFinite(full));
        assertEquals(legacy, full, 0.0);
    }

    @Test
    public void full3DCylinderFallsBackToBracketAtStationarySeed() {
        Helix helix = helix();
        double expectedAbsL = 60.0;
        double radius = Math.hypot(helix.getX(expectedAbsL), helix.getY(expectedAbsL));

        double actualL = helix.getLAtCylinder3D(
                0.0, 0.0, -500.0, 0.0, 0.0, 1000.0, radius, 0.0);

        assertTrue(Double.isFinite(actualL));
        assertEquals(expectedAbsL, Math.abs(actualL), 1.0e-5);
        assertEquals(radius, Math.hypot(
                helix.getX(actualL), helix.getY(actualL)), 1.0e-7);
    }

    @Test
    public void full3DCylinderRejectsDegenerateAxis() {
        Helix helix = helix();
        double result = helix.getLAtCylinder3D(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 150.0, 10.0);
        assertTrue(Double.isNaN(result));
    }
}
