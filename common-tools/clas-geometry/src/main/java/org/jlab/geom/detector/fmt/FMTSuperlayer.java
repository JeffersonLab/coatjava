package org.jlab.geom.detector.fmt;

import org.jlab.geom.DetectorId;
import org.jlab.geom.abs.AbstractSuperlayer;


/**
 * A Forward Micromegas Tracker  (FMT) {@link org.jlab.geom.base.Superlayer Superlayer}.
 * <p>
 * Factory: {@link org.jlab.geom.detector.fmt.FMTFactory FMTFactory}<br> 
 * Hierarchy: 
 * <code>
 * {@link org.jlab.geom.detector.fmt.FMTDetector FMTDetector} → 
 * {@link org.jlab.geom.detector.fmt.FMTSector FMTSector} → 
 * <b>{@link org.jlab.geom.detector.fmt.FMTSuperlayer FMTSuperlayer}</b> → 
 * {@link org.jlab.geom.detector.fmt.FMTLayer FMTLayer} → 
 * {@link org.jlab.geom.component.TrackerStrip TrackerStrip}
 * </code>
 * 
 * @author devita
 */
public class FMTSuperlayer extends AbstractSuperlayer<FMTLayer> {

    protected FMTSuperlayer(int sectorId, int superlayerId) {
        super(DetectorId.FMT, sectorId, superlayerId);
    }
    
    /**
     * Returns "FMT Superlayer".
     * @return "FMT Superlayer"
     */
    @Override
    public String getType() {
        return "FMT Superlayer";
    }
}
