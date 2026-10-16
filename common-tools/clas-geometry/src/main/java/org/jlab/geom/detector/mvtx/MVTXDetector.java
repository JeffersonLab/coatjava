package org.jlab.geom.detector.mvtx;

import org.jlab.geom.DetectorId;
import org.jlab.geom.abs.AbstractDetector;

/**
 * The Maps VerTeX detector (MVTX) {@link org.jlab.geom.base.Detector Detector}.
 * <p>
 * Factory: {@link org.jlab.geom.detector.mvtx.MVTXFactory MVTXFactory}<br> 
 * Hierarchy: 
 * <code>
 * <b>{@link org.jlab.geom.detector.mvtx.MVTXDetector MVTXDetector}</b> → 
 * {@link org.jlab.geom.detector.mvtx.MVTXSector MVTXSector} → 
 * {@link org.jlab.geom.detector.mvtx.MVTXLayer MVTXLayer} → 
 * {@link org.jlab.geom.component.SiPixel SiPixel}
 * </code>
 * 
 * @author jnhankins
 */
public class MVTXDetector extends AbstractDetector<MVTXSector> {
    
    protected MVTXDetector() {
        super(DetectorId.MVTX);
    }
    
    /**
     * Returns "MVTX Detector".
     * @return "MVTX Detector"
     */
    @Override
    public String getType() {
        return "MVTX Detector";
    }
}
