package org.jlab.geom.detector.mvtx;

import java.util.ArrayList;
import java.util.List;
import org.jlab.geom.DetectorHit;
import org.jlab.geom.DetectorId;
import org.jlab.geom.abs.AbstractLayer;
import org.jlab.geom.component.SiPixel;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Path3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Shape3D;


/**
 * A MAPS VerTeX tracker (MVTX) {@link org.jlab.geom.base.Layer Layer}.
 * <p>
 * Factory: {@link org.jlab.geom.detector.mvtx.MVTXFactory MVTXFactory}<br> 
 * Hierarchy: 
 * <code>
 * {@link org.jlab.geom.detector.mvtx.MVTXDetector MVTXDetector} → 
 * {@link org.jlab.geom.detector.mvtx.MVTXSector MVTXSector} → 
 * <b>{@link org.jlab.geom.detector.mvtx.MVTXLayer MVTXLayer}</b> → 
 * {@link org.jlab.geom.component.SiPixel SiPixel}
 * </code>
 * 
 * @author jnhankins
 */
public class MVTXLayer extends AbstractLayer<SiPixel> {
    
    protected MVTXLayer(int sectorId, int superlayerId, int layerId) {
        super(DetectorId.MVTX, sectorId, superlayerId, layerId, true);
    }
    
    @Override
    public List<DetectorHit> getHits(Path3D path) {
        List<DetectorHit> hits = new ArrayList();
        Point3D hitPosition = new Point3D();
        
        // For each line in the path
        for(int i=0; i<path.size()-1; i++) {
            Line3D line = path.getLine(i);
            
            // Check to see if the boundary was hit
            Shape3D boundary = getBoundary();
            if (boundary.hasIntersectionSegment(line)) {
                int closestComponentId = -1;
                double closestDist = Double.POSITIVE_INFINITY;
                List<SiPixel> strips = getAllComponents();
                for (int componentId=0; componentId<strips.size(); componentId++) {
                    Line3D hitL = strips.get(componentId).getLine().distance(line);
                    double dist = strips.get(componentId).getLine().distance(line).length();
                    hitPosition.set(hitL.midpoint().x(), 
                            hitL.midpoint().y(),
                            hitL.midpoint().z());
                    if(closestDist > dist) {
                        closestDist = dist;
                        closestComponentId = componentId;
                    }
                }
                hits.add(
                    new DetectorHit(
                        getDetectorId(),
                        getSectorId(),
                        getSuperlayerId(),
                        getLayerId(),
                        closestComponentId,
                        hitPosition));
                return hits;
            }
        }
        
        return hits;
    }

    /**
     * Returns "MVTX Layer".
     * @return "MVTX Layer"
     */
    @Override
    public String getType() {
        return "MVTX Layer";
    }
}
