package org.jlab.detector.geant4.v2.MPGD.trapezoid;

import eu.mihosoft.vrl.v3d.Vector3d;
import org.jlab.detector.geant4.v2.Geant4Factory;
import org.jlab.detector.volume.G4Trap;
import org.jlab.detector.volume.G4World;
import org.jlab.detector.volume.Geant4Basic;

import java.util.Map;

/**
 * Generic Geant4 factory for trapezoidal MPGD-like trackers.
 *
 * It builds: - a world volume - NREGIONS = NLAYERS/2 (by convention) - NSECTORS
 * per region
 *
 * The exact geometry parameters and material stack are taken from an
 * {@link MPGDTrapezoidConstants} instance.
 *
 * Concrete detectors should: - extend this class - pass the appropriate
 * constants + detector name in the constructor
 */
public abstract class MPGDTrapezoidGeant4Factory extends Geant4Factory {

    /**
     * Detector constants (geometry + materials from CCDB).
     */
    protected final MPGDTrapezoidConstants C;

    /**
     * Short detector name used in volume names (e.g. "uRWT").
     */
    protected final String detectorName;

    public static record SectorDimensions(
            double halfThickness,
            double halfHeight,
            double halfLargeBase,
            double halfSmallBase,
            double tiltRad
            ) {

    }

    /**
     * @param constants detector constants (already configured with CCDB
     * paths/table names)
     * @param detectorName short detector name used in volume names
     */
    protected MPGDTrapezoidGeant4Factory(MPGDTrapezoidConstants constants,
            String detectorName) {
        this.C = constants;
        this.detectorName = detectorName;
    }

    // ------------------------------------------------------------------------
    //  Top-level geometry construction
    // ------------------------------------------------------------------------
    /**
     * Initializes the world volume and constructs all regions and sectors.
     *
     * This must be called after the constants have been loaded from CCDB.
     */
    protected void init() {

        // World volume for this detector
        motherVolume = new G4World("root");

        int NREGIONS = (int) Math.round(C.NLAYERS / 2.0);
        for (int iregion = 0; iregion < NREGIONS; iregion++) {
            for (int isector = 0; isector < C.NSECTORS; isector++) {
                Geant4Basic sectorVolume = createSector(isector, iregion);
                sectorVolume.setMother(motherVolume);
            }
        }
    }

    // ------------------------------------------------------------------------
    //  Sector geometry helpers
    // ------------------------------------------------------------------------
    /**
     * Computes the total thickness (mm) of a sector by summing the thickness of
     * all material volumes.
     *
     * @return
     */
    public double getSectorThickness() {
        return C.getDetectorStructure().values()
                .stream()
                .flatMap(componentMap -> componentMap.values().stream())
                .mapToDouble(info -> info.thickness)
                .sum();
    }

    /**
     *
     * @param region
     * @return
     */
    public SectorDimensions getSectorDimensionsPhysical(int region) {

        double baseDistance = C.TGTDET + region * C.DZ;

        double sectorHeight = baseDistance
                * (Math.tan(Math.toRadians(C.THMAX - C.THTILT))
                + Math.tan(Math.toRadians(C.THTILT - C.THMIN)));

        double halfThickness = this.getSectorThickness() / 2.0;
        double halfHeight = sectorHeight / 2.0;

        // Distance from target to the bottom base along the tilted axis
        double W2TGT = (C.TGTDET + region * C.DZ)
                / Math.cos(Math.toRadians(C.THTILT - C.THMIN));

        double YMIN = W2TGT * Math.sin(Math.toRadians(C.THMIN)); // distance from beamline (Y)        
        double h = sectorHeight * Math.cos(Math.toRadians(C.THTILT));
        double halfSmallBase = 0.5 * (YMIN * Math.tan(Math.toRadians(C.THOPEN) / 2));

        double halfLargeBase = halfSmallBase + sectorHeight * Math.tan(Math.toRadians(C.THOPEN / 2.0));

        double tiltRad = Math.toRadians(C.THTILT);

        double twidth_Check = 2 * halfLargeBase * Math.sin(Math.toRadians(C.THOPEN));

        if (MPGDTrapezoidConstants.VERBOSE) {
            System.out.printf("C.TWIDT=%.3f vs %.3f", C.TWIDTH, twidth_Check);

            System.out.printf("YMIN=%.3f", YMIN);

            System.out.printf(
                    "SectorDimensionsPhysical [%s] region=%d : height=%.3f | halfT=%.3f halfH=%.3f "
                    + "halfLarge=%.3f halfSmall=%.3f tilt(deg)=%.3f%n",
                    detectorName, region, sectorHeight,
                    halfThickness, halfHeight,
                    halfLargeBase, halfSmallBase,
                    C.THTILT
            );
        }

        return new SectorDimensions(halfThickness, halfHeight, halfLargeBase, halfSmallBase, tiltRad);
    }

    /**
     *
     * @param region
     * @return
     */
    public SectorDimensions getSectorDimensionsContainer(int region) {

        SectorDimensions phys = getSectorDimensionsPhysical(region);

        double halfThickness = phys.halfThickness() + MPGDTrapezoidConstants.ZENLARGEMENT;
        double halfHeight = phys.halfHeight() + MPGDTrapezoidConstants.YENLARGEMENT;
        double halfLargeBase = phys.halfLargeBase() + MPGDTrapezoidConstants.XENLARGEMENT;
        double halfSmallBase = phys.halfSmallBase() + MPGDTrapezoidConstants.XENLARGEMENT;

        return new SectorDimensions(halfThickness, halfHeight, halfLargeBase, halfSmallBase, phys.tiltRad());
    }

    /**
     * Computes the sector height (longitudinal extension in the RZ plane) for a
     * given region.
     *
     * @param region
     * @return
     */
    public double getSectorHeight(int region) {

        double baseDistance = C.TGTDET + region * C.DZ;

        double sectorHeight = baseDistance
                * (Math.tan(Math.toRadians(C.THMAX - C.THTILT))
                + Math.tan(Math.toRadians(C.THTILT - C.THMIN)));

        if (MPGDTrapezoidConstants.VERBOSE) {
            System.out.printf(
                    "SectorHeight [%s] region=%d : baseDistance=%.3f THMIN=%.3f THMAX=%.3f THTILT=%.3f -> height=%.3f%n",
                    detectorName,
                    region,
                    baseDistance,
                    C.THMIN, C.THMAX, C.THTILT,
                    sectorHeight
            );
        }

        return sectorHeight;
    }

    /**
     * Computes the barycenter coordinates of a given sector/region in the
     * CLAS12 coordinate system.
     *
     * @param isector
     * @param iregion
     * @return
     */
    public Vector3d getCenterCoordinate(int isector, int iregion) {

        Vector3d vCenter = new Vector3d(0, 0, 0);

        // Distance from target to the bottom base along the tilted axis
        double W2TGT = (C.TGTDET + iregion * C.DZ)
                / Math.cos(Math.toRadians(C.THTILT - C.THMIN));

        double YMIN = W2TGT * Math.sin(Math.toRadians(C.THMIN)); // distance from beamline (Y)
        double ZMIN = W2TGT * Math.cos(Math.toRadians(C.THMIN)); // Z of the bottom base

        SectorDimensions dimCont = this.getSectorDimensionsContainer(iregion);
        double sectorHeight = 2 * dimCont.halfHeight();

        vCenter.x = 0.0;
        vCenter.y = (sectorHeight / 2.0) * Math.cos(Math.toRadians(C.THTILT)) + YMIN;
        vCenter.z = -(sectorHeight / 2.0) * Math.sin(Math.toRadians(C.THTILT)) + ZMIN;

        // Rotate to the correct sector around Z (assumes 6 sectors, 60° apart)
        vCenter.rotateZ(-Math.toRadians(90.0 - isector * 60.0));

        return vCenter;
    }

    // ------------------------------------------------------------------------
    //  Sector + material volume construction
    // ------------------------------------------------------------------------
    /**
     * Builds a single sector for a given region and returns its volume.
     *
     * @param isector
     * @param iregion
     * @return
     */
    public Geant4Basic createSector(int isector, int iregion) {

        SectorDimensions dimPhys = this.getSectorDimensionsPhysical(iregion);
        SectorDimensions dimCont = this.getSectorDimensionsContainer(iregion);

        Geant4Basic sectorVolume = createSectorVolume(isector, iregion, dimCont);
        populateSectorWithDetectorStructure(sectorVolume, isector, iregion, dimPhys);

        return sectorVolume;
    }

    /**
     * Creates the main trapezoidal sector volume and places it in the world.
     *
     * @param isector
     * @param iregion
     * @param dimSect
     * @return
     */
    protected Geant4Basic createSectorVolume(int isector,
            int iregion,
            SectorDimensions dimSect) {

        double sectorDZ = dimSect.halfThickness();
        double sectorDY = dimSect.halfHeight();
        double sectorDX1 = dimSect.halfLargeBase();
        double sectorDX0 = dimSect.halfSmallBase();
        double sectorTtilt = dimSect.tiltRad();

        Vector3d vCenter = this.getCenterCoordinate(isector, iregion);

        Geant4Basic sectorVolume = new G4Trap(
                "region_" + detectorName + "_" + (iregion + 1) + "_s" + (isector + 1),
                sectorDZ, -sectorTtilt, Math.toRadians(90.0),
                sectorDY, sectorDX0, sectorDX1, 0.0,
                sectorDY, sectorDX0, sectorDX1, 0.0
        );

        sectorVolume.rotate("yxz", 0.0, sectorTtilt, Math.toRadians(90.0 - isector * 60.0));
        sectorVolume.translate(vCenter.x, vCenter.y, vCenter.z);
        sectorVolume.setId(isector + 1, iregion + 1, 0, 0);

        return sectorVolume;
    }

    /**
     * Fills the given sector volume with material sub-volumes according to the
     * CCDB material structure (layer/component stack).Stacking is done along
     * the local Z direction (thickness).
     *
     * @param sectorVolume
     * @param isector
     * @param iregion
     * @param dimSect
     */
    protected void populateSectorWithDetectorStructure(Geant4Basic sectorVolume,
            int isector,
            int iregion,
            SectorDimensions dimSect) {

        double halfThickness = dimSect.halfThickness();
        double halfHeight = dimSect.halfHeight();
        double halfLargeBase = dimSect.halfLargeBase();
        double halfSmallBase = dimSect.halfSmallBase();
        double tiltRad = dimSect.tiltRad();

        double totalThickness = 2.0 * halfThickness;
        double accumulatedThickness = 0.0;

        for (Map.Entry<Integer, Map<Integer, MPGDTrapezoidConstants.LayerComponentInfo>> layerEntry
                : C.getDetectorStructure().entrySet()) {

            int LayerId = layerEntry.getKey();
            Map<Integer, MPGDTrapezoidConstants.LayerComponentInfo> componentMap
                    = layerEntry.getValue();

            for (Map.Entry<Integer, MPGDTrapezoidConstants.LayerComponentInfo> componentEntry
                    : componentMap.entrySet()) {

                int materialComponentId = componentEntry.getKey();
                MPGDTrapezoidConstants.LayerComponentInfo info = componentEntry.getValue();

                double thick = info.thickness;

                // Protect against zero or negative thickness: skip such entries
                if (thick <= 0.0) {
                    System.err.printf(
                            "WARNING: skipping material volume with non-positive thickness: "
                            + "layer=%d comp=%d thick=%f%n",
                            LayerId, materialComponentId, thick
                    );
                    continue;
                }

                // place from "front" to "back" along local Z
                double localZ = -totalThickness / 2.0 + accumulatedThickness + thick / 2.0;
                double localY = -localZ * Math.tan(Math.toRadians(C.THTILT));

                Geant4Basic matVolume = new G4Trap(
                        "matVolume",
                        thick / 2.0, -tiltRad, Math.toRadians(90.0),
                        halfHeight, halfSmallBase, halfLargeBase, 0.0,
                        halfHeight, halfSmallBase, halfLargeBase, 0.0
                );

                matVolume.setName(
                        "rg_" + detectorName + "_" + (iregion + 1)
                        + "_s" + (isector + 1)
                        + "_l" + (LayerId + iregion * 2)
                        + "_matC" + materialComponentId
                );

                matVolume.setMother(sectorVolume);
                matVolume.setPosition(0.0, localY, localZ);

                accumulatedThickness += thick;
            }
        }
    }
}
