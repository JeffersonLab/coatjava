package org.jlab.geom.detector.mvtx;

import org.jlab.geom.DetectorId;
import org.jlab.geom.abs.AbstractSector;


/**
 * A Barrel Silicon Tracker (MVTX) {@link org.jlab.geom.base.Sector Sector}.
 * <p>
 * Factory: {@link org.jlab.geom.detector.mvtx.MVTXFactory MVTXFactory}<br> 
 * Hierarchy: 
 * <code>
 * {@link org.jlab.geom.detector.mvtx.MVTXDetector MVTXDetector} → 
 * <b>{@link org.jlab.geom.detector.mvtx.MVTXSector MVTXSector}</b> → 
 * {@link org.jlab.geom.detector.mvtx.MVTXLayer MVTXLayer} → 
 * {@link org.jlab.geom.component.SiStrip SiStrip}
 * </code>
 * 
 * @author jnhankins
 */
public class MVTXSector extends AbstractSector<MVTXSuperlayer> {
    
    protected MVTXSector(int sectorId) {
        super(DetectorId.MVTX, sectorId);
    }
    
    /**
     * Returns "MVTX Sector".
     * @return "MVTX Sector"
     */
    @Override
    public String getType() {
        return "MVTX Sector";
    }
}
