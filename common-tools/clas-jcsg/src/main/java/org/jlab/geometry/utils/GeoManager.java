package org.jlab.geometry.utils;

import java.util.HashMap;
import java.util.Map;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.detector.geant4.v2.CTOFGeant4Factory;
import org.jlab.detector.geant4.v2.Geant4Factory;
import org.jlab.geom.base.ConstantProvider;

/**
 * A manager to access CCDB and cache geometry factories by run number and
 * detector type, with thread safety, e.g., for access from
 * ReconstructionEngine.processDataEvent().
 * 
 * Since there exists no standard inheritance model for the detector's geometry
 * factories, and some even use none at all (,and previous attempts to fix that
 * weren't successful), this is not going to be clean and this manager does not
 * have a nice place to live.
 * 
 * @author baltzell
 */
public class GeoManager {

    public static final DetectorType[] DETECTOR_TYPES = {
        DetectorType.CTOF,
        DetectorType.FTOF
    };

    private volatile Map<Integer,Map<DetectorType,Object>> cache;

    private final String variation;
    //private final String timestamp;

    public GeoManager(String variation) {
        this.variation = variation;
        this.cache = new HashMap<>();
    }

    private Object read(int run, DetectorType type) {
        if (DetectorType.CTOF == type) {
            ConstantProvider cp = GeometryFactory.getConstants(DetectorType.CTOF, run, this.variation);
            return (Object)(new CTOFGeant4Factory(cp));
        }
        else if (DetectorType.FTOF == type) {
            ConstantProvider cp = GeometryFactory.getConstants(DetectorType.CTOF, run, this.variation);
            return (Object)(new CTOFGeant4Factory(cp));
        }
        throw new RuntimeException("Invalid DetectorType:  "+type);
    }

    private synchronized void load(int run, DetectorType type) {
        if (!this.cache.containsKey(run)) {
            this.cache.put(run, new HashMap<>());
        }
        if (!this.cache.get(run).containsKey(type)) {
            this.cache.get(run).put(type, this.read(run,type));
        }
    }

    public Object get(int run, DetectorType type) {
        if (!this.cache.containsKey(run) || !this.cache.get(run).containsKey(type)) {
            load(run, type);
        }
        return cache.get(run).get(type);
    }

    public Geant4Factory getCTOF(int run) {
        return (Geant4Factory) this.get(run, DetectorType.CTOF);
    }

}
