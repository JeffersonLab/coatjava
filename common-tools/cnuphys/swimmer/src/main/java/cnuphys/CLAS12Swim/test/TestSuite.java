
package cnuphys.CLAS12Swim.test;

import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CommonsMathCLAS12Swimmer;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

/**
 * Entry point for swimmer accuracy and speed comparisons.
 */
public final class TestSuite {

	private TestSuite() {
	}

	public static void main(String[] args) {
		MagneticFields.getInstance().initializeMagneticFields();
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);

		// ------------------------------------------------------------------
		// Random test data (deterministic for regression)
		// ------------------------------------------------------------------
		RandomTestData data = new RandomTestData(2000, // number of samples
				12345L // RNG seed
		);

		// ------------------------------------------------------------------
		// Swimmers
		// ------------------------------------------------------------------
		ICLAS12Swimmer legacy = new CLAS12Swimmer();

		CommonsMathCLAS12Swimmer commons = new CommonsMathCLAS12Swimmer();
		commons.setLegacyComparable(true); // match ~1e-5 miss scale
		// commons.setRecordTrajectory(false); // critical for fair timing

		// ------------------------------------------------------------------
		// Basic swim (fixed path length) test
		// ------------------------------------------------------------------
		BasicSwimTest basicTest = new BasicSwimTest(1000.0, // sMax (cm)
				0.1, // h (cm)
				1e-5 // tolerance
		);

		basicTest.maxMismatchPrint = 10;
		basicTest.warmupIters = 200;
		basicTest.timedIters = 25000;

		basicTest.runCompare(legacy, "CLAS12", commons, "APACHE", data);

		// ------------------------------------------------------------------
		// SwimZ test
		// ------------------------------------------------------------------
		SwimZTest zTest = new SwimZTest(300.0, // zTarget (cm)
				1e-5, // accuracy (cm)
				1000.0, // sMax (cm)
				0.1, // h (cm)
				1e-5 // tolerance
		);

		zTest.maxMismatchPrint = 10;
		zTest.warmupIters = 200;
		zTest.timedIters = 25000;

		zTest.runCompare(legacy, "CLAS12", commons, "APACHE", data);

		// ------------------------------------------------------------------
		// SwimRho test
		// ------------------------------------------------------------------
		SwimRhoTest rhoTest = new SwimRhoTest(100.0, // rhoTarget (cm)
				1e-5, // accuracy (cm)
				1000.0, // sMax (cm)
				0.1, // h (cm)
				1e-5 // tolerance
		);

		rhoTest.maxMismatchPrint = 10;
		rhoTest.warmupIters = 200;
		rhoTest.timedIters = 25000;

		rhoTest.runCompare(legacy, "CLAS12", commons, "APACHE", data);

		// ------------------------------------------------------------------
		// SwimPlane test
		// ------------------------------------------------------------------
		// A slightly tilted plane passing through (0,0,300 cm). The Plane constructor
		// normalizes the normal.
		SwimPlaneTest planeTest = new SwimPlaneTest(0.20, 0.10, 1.00, // plane normal (dimensionless)
				0.0, 0.0, 300.0, // point on plane (cm)
				1e-5, // accuracy (cm)
				1000.0, // sMax (cm)
				0.1, // h (cm)
				1e-5 // tolerance
		);

		planeTest.maxMismatchPrint = 10;
		planeTest.warmupIters = 200;
		planeTest.timedIters = 25000;

		planeTest.runCompare(legacy, "CLAS12", commons, "APACHE", data);

		// sector swimZ test
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITEROTATED);
		legacy = new CLAS12Swimmer();
		commons = new CommonsMathCLAS12Swimmer();
		commons.setLegacyComparable(true); // match ~1e-5 miss scale
		if (legacy.sectorSwimZ(1, 1, 0, 0, 0, 1.0, 30.0, 0.0, 300.0, 1e-5, 1000.0, 0.1, 1e-5) == null) {
			System.out.println();
			System.out.println("SectorSwimZTest skipped: active field probe is not RotatedCompositeProbe.");
			System.out.println(
					"Tip: configure MagneticFields to use the rotated composite field before running this test.");
		} else {
			SectorSwimZTest sectorZTest = new SectorSwimZTest(5, // sector
					300.0, // zTarget (cm)
					1e-5, // accuracy (cm)
					1000.0, // sMax (cm)
					0.1, // h (cm)
					1e-5 // tolerance
			);
			sectorZTest.maxMismatchPrint = 10;
			sectorZTest.warmupIters = 200;
			sectorZTest.timedIters = 5000;

			sectorZTest.runCompare(legacy, "LEGACY", commons, "CM", data);
		}

	}
}
