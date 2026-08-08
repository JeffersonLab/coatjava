package cnuphys.CLAS12Swim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cnuphys.magfield.ZeroProbe;

public class CLAS12SwimmerTest {

	private static final double POSITION_TOLERANCE = 1.0e-7;

	@Test
	public void chargedParticleFollowsStraightLineInZeroField() {
		CLAS12Swimmer swimmer = new CLAS12Swimmer(new ZeroProbe());
		double pathLength = 100.0;
		double theta = 60.0;
		double phi = 30.0;

		CLAS12SwimResult result = swimmer.swim(1, 1.0, 2.0, 3.0, 1.0, theta, phi,
				pathLength, 0.01, 1.0e-9);

		assertTrue(result.statusString(), result.isSuccess());
		assertEquals(pathLength, result.getPathLength(), POSITION_TOLERANCE);

		double sinTheta = Math.sin(Math.toRadians(theta));
		double[] finalState = result.getFinalU();
		assertEquals(1.0 + pathLength * sinTheta * Math.cos(Math.toRadians(phi)), finalState[0],
				POSITION_TOLERANCE);
		assertEquals(2.0 + pathLength * sinTheta * Math.sin(Math.toRadians(phi)), finalState[1],
				POSITION_TOLERANCE);
		assertEquals(3.0 + pathLength * Math.cos(Math.toRadians(theta)), finalState[2], POSITION_TOLERANCE);
	}

	@Test
	public void neutralParticleReachesTargetZExactly() {
		CLAS12Swimmer swimmer = new CLAS12Swimmer(new ZeroProbe());

		CLAS12SwimResult result = swimmer.swimZ(0, 0.0, 0.0, 0.0, 1.0, 60.0, 0.0,
				50.0, 1.0e-8, 200.0, 0.01, 1.0e-9);

		assertTrue(result.statusString(), result.isSuccess());
		assertEquals(50.0, result.getFinalU()[2], POSITION_TOLERANCE);
		assertEquals(100.0, result.getPathLength(), POSITION_TOLERANCE);
	}

	@Test
	public void momentumBelowThresholdIsRejected() {
		CLAS12Swimmer swimmer = new CLAS12Swimmer(new ZeroProbe());

		CLAS12SwimResult result = swimmer.swim(1, 0.0, 0.0, 0.0, 1.0e-6, 45.0, 0.0,
				100.0, 0.01, 1.0e-9);

		assertFalse(result.isSuccess());
		assertEquals(CLAS12Swimmer.BELOW_MIN_MOMENTUM, result.getStatus());
		assertEquals("BELOW_MIN_MOMENTUM", result.statusString());
	}

	@Test
	public void fixedStepSwimUsesRequestedStepSize() {
		CLAS12Swimmer swimmer = new CLAS12Swimmer(new ZeroProbe());

		CLAS12SwimResult result = swimmer.swimFixed(1, 0.0, 0.0, 0.0, 1.0, 90.0, 0.0,
				10.0, 2.0);

		assertTrue(result.statusString(), result.isSuccess());
		assertEquals(10.0, result.getPathLength(), POSITION_TOLERANCE);
		assertEquals(10.0, result.getFinalU()[0], POSITION_TOLERANCE);
		assertEquals(6, result.getNStep());
	}

	@Test
	public void planeArrayOverloadDelegatesToPlaneSwim() {
		CLAS12Swimmer swimmer = new CLAS12Swimmer(new ZeroProbe());

		CLAS12SwimResult result = swimmer.swimPlane(1, 0.0, 0.0, 0.0, 1.0, 60.0, 0.0,
				new double[] {0.0, 0.0, 1.0}, new double[] {0.0, 0.0, 25.0},
				1.0e-7, 100.0, 0.01, 1.0e-9);

		assertTrue(result.statusString(), result.isSuccess());
		assertEquals(25.0, result.getFinalU()[2], POSITION_TOLERANCE);
	}
}
