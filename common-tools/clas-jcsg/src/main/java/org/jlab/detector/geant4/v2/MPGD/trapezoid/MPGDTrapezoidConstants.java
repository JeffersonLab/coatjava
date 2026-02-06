package org.jlab.detector.geant4.v2.MPGD.trapezoid;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Generic constants for trapezoidal MPGD-like trackers loaded from CCDB.
 * Concrete detectors (e.g. URWT) should extend this class and provide: - CCDB
 * base path - global table name - material table name
 */
public class MPGDTrapezoidConstants {

    // ------------------------------------------------------------------------
    //  Logging / verbosity
    // ------------------------------------------------------------------------
    public static final Logger LOGGER
            = Logger.getLogger(MPGDTrapezoidConstants.class.getName());

    public static boolean VERBOSE = false;

    // ------------------------------------------------------------------------
    //  CCDB configuration
    // ------------------------------------------------------------------------
    /**
     * Base CCDB path (e.g. "/test/urwt/").
     */
    protected final String ccdbPath;

    /**
     * Name of the global geometry table (e.g. "urwt_global").
     */
    protected final String globalTableName;

    /**
     * Name of the material table (e.g. "urwt_material_geo").
     */
    protected final String materialTableName;

    /**
     * Short detector name used in volume names (e.g. "uRWT").
     */
    public final String detectorName;
    
    // ------------------------------------------------------------------------
    //  Geometry parameters (from global table)
    // ------------------------------------------------------------------------
    public int NSECTORS;      // number of sectors
    public int NLAYERS;       // number of layers (readout planes)
    public int NCOMPONENTS;   // number of components (e.g. strips) per layer

    public double THOPEN;     // opening angle between endplate planes (deg)
    public double THTILT;     // tilt angle (deg)
    public double THMIN;      // minimum polar angle (deg)
    public double THMAX;      // maximum polar angle (deg)
    public double TGTDET;     // distance from target to first plane (cm)
    public double DZ;         // spacing between regions (cm)
    public double TWIDTH;      // Trapezoid width: distance from a corner to the oblique (non-parallel) side (cm)

    public double PITCH;         // strip pitch (mm)
    public double WIDTH;         // strip width (mm)
    public double STEREOANGLE;   // strip stereo angle (deg)

    // ------------------------------------------------------------------------
    //  Geometry enlargement (used when building volumes)
    // ------------------------------------------------------------------------
    public static final double XENLARGEMENT = 0.15; // cm
    public static final double YENLARGEMENT = 0.15; // cm
    public static final double ZENLARGEMENT = 0.02; // cm

    // ------------------------------------------------------------------------
    //  Material description: (layer, component) -> parameters
    // ------------------------------------------------------------------------
    /**
     * Simple container for material parameters associated to (layer,
     * component). thickness : material thickness (mm) sensitivity: flag or ID
     * for sensitivity (as defined in CCDB)
     */
    public static class LayerComponentInfo {

        public final double thickness;
        public final int sensitivity;

        public LayerComponentInfo(double thickness, int sensitivity) {
            this.thickness = thickness;
            this.sensitivity = sensitivity;
        }
    }

    /**
     * Material structure: outer key = layer index (from CCDB column "Layer")
     * inner key = component index (from CCDB column "Component") value =
     * material parameters (thickness, sensitivity)
     */
    protected final Map<Integer, Map<Integer, LayerComponentInfo>> detectorStructure
            = new LinkedHashMap<>();

    /**
     * @param ccdbPath base CCDB path (e.g. "/test/urwt/")
     * @param globalTableName name of the global table (e.g. "urwt_global")
     * @param materialTableName name of the material table (e.g.
     * "urwt_material_geo")
     */
    protected MPGDTrapezoidConstants(String ccdbPath,
                                     String globalTableName,
                                     String materialTableName,
                                     String detectorName) {
        this.ccdbPath = ccdbPath.endsWith("/") ? ccdbPath : ccdbPath + "/";
        this.globalTableName = globalTableName;
        this.materialTableName = materialTableName;
        this.detectorName = detectorName;
    }

    /**
     * Read-only access to the material structure.
     *
     * @return
     */
    public Map<Integer, Map<Integer, LayerComponentInfo>> getDetectorStructure() {
        return detectorStructure;
    }

    // ------------------------------------------------------------------------
    //  CCDB loading API
    // ------------------------------------------------------------------------
    /**
     * Loads the relevant CCDB tables into the given provider and fills this
     * object's fields.
     *
     * @param cp
     * @return
     */
    public DatabaseConstantProvider load(DatabaseConstantProvider cp) {
        cp.loadTable(ccdbPath + globalTableName);
        cp.loadTable(ccdbPath + materialTableName);
        getConstants(cp);
        return cp;
    }

    /**
     * Reads the already-loaded CCDB tables from the provider and fills geometry
     * and material parameters.This method assumes: - global table has at least
     * one row - material table has at least one row
     *
     * @param cp
     */
    public synchronized void getConstants(DatabaseConstantProvider cp) {

        // --------------------------- Global table ---------------------------
        String globalBase = ccdbPath + globalTableName;

        int nGlobalRows = cp.length(globalBase + "/Nsectors");
        if (nGlobalRows <= 0) {
            throw new IllegalStateException("Table " + globalBase + " has no rows in CCDB");
        }

        int row = 0; // global table is expected to have a single row

        NSECTORS = cp.getInteger(globalBase + "/Nsectors", row);
        NLAYERS = cp.getInteger(globalBase + "/NLayers", row);
        NCOMPONENTS = cp.getInteger(globalBase + "/NComponents", row);
        THOPEN = cp.getDouble(globalBase + "/Thopen", row);
        THTILT = cp.getDouble(globalBase + "/Thtilt", row);
        THMIN = cp.getDouble(globalBase + "/Thmin", row);
        THMAX = cp.getDouble(globalBase + "/Thmax", row);
        TGTDET = cp.getDouble(globalBase + "/Tgt", row);
        DZ = cp.getDouble(globalBase + "/Dz", row);
        TWIDTH = cp.getDouble(globalBase + "/TWidth", row);
        PITCH = cp.getDouble(globalBase + "/Pitch", row);
        WIDTH = cp.getDouble(globalBase + "/Width", row);
        STEREOANGLE = cp.getDouble(globalBase + "/StereoAngle", row);

        if (VERBOSE) {
            System.out.printf(
                    "%s global: Nsectors=%d Nlayers=%d Ncomponents=%d Thopen=%.3f Thtilt=%.3f "
                    + "Thmin=%.3f Thmax=%.3f Tgt=%.3f Dz=%.3f TWidth=%.3f Pitch=%.3f Width=%.3f StereoAngle=%.3f%n",
                    globalTableName,
                    NSECTORS, NLAYERS, NCOMPONENTS,
                    THOPEN, THTILT, THMIN, THMAX,
                    TGTDET, DZ, TWIDTH, PITCH, WIDTH, STEREOANGLE
            );
        }

        // --------------------------- Material table ------------------------
        String matBase = ccdbPath + materialTableName;

        // Use a numeric column (e.g. Layer) to get the number of rows
        int nRows = cp.length(matBase + "/Layer");

        if (VERBOSE) {
            System.out.printf("%s material table rows=%d%n", materialTableName, nRows);
        }

        detectorStructure.clear();

        // Expected schema (all numeric):
        //   Layer       (int)
        //   Component   (int)
        //   thickness   (double) mm
        //   sensitivity (int)
        for (int i = 0; i < nRows; i++) {

            int layer = cp.getInteger(matBase + "/Layer", i);
            int component = cp.getInteger(matBase + "/Component", i);
            double thickness = (cp.getDouble(matBase + "/Thickness", i)) * 0.1;   // convert in cm
            int sensitivity = cp.getInteger(matBase + "/Sensitivity", i);

            if (VERBOSE) {
                System.out.printf(
                        "%s row %d: layer=%d comp=%d thick=%.5f sens=%d%n",
                        materialTableName, i, layer, component, thickness, sensitivity
                );
            }

            LayerComponentInfo info = new LayerComponentInfo(thickness, sensitivity);

            detectorStructure
                    .computeIfAbsent(layer, l -> new LinkedHashMap<>())
                    .put(component, info);
        }
    }
}
