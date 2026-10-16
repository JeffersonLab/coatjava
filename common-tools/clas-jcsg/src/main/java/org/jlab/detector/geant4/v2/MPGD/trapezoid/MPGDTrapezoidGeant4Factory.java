package org.jlab.detector.geant4.v2.MPGD.trapezoid;

import eu.mihosoft.vrl.v3d.Vector3d;
import org.jlab.detector.geant4.v2.Geant4Factory;
import org.jlab.detector.volume.G4Trap;
import org.jlab.detector.volume.G4World;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.detector.geant4.v2.MPGD.trapezoid.MPGDTrapezoidConstants.SectorDimensions;

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
public class MPGDTrapezoidGeant4Factory extends Geant4Factory {

    /**
     * Detector constants (geometry + materials from CCDB).
     */
    protected final MPGDTrapezoidConstants C;

    /**
     * @param constants detector constants (already configured with CCDB
     * paths/table names)
     */
    protected MPGDTrapezoidGeant4Factory(MPGDTrapezoidConstants constants) {
        this.C = constants;
        this.init();
    }

    // ------------------------------------------------------------------------
    //  Top-level geometry construction
    // ------------------------------------------------------------------------
    /**
     * Initializes the world volume and constructs all regions and sectors.
     *
     * This must be called after the constants have been loaded from CCDB.
     */
    protected final void init() {

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

        SectorDimensions dimPhys = C.getSectorActiveVolumeDimensions(iregion);
        SectorDimensions dimCont = C.getSectorContainerDimensions(iregion);

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

        Vector3d vCenter = C.getCenterCoordinate(isector, iregion);

        Geant4Basic sectorVolume = new G4Trap(
                "region_" + C.detectorName + "_" + (iregion + 1) + "_s" + (isector + 1),
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
                        "rg_" + C.detectorName + "_" + (iregion + 1)
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
