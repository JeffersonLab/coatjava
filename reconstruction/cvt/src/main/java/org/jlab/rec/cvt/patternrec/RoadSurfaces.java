/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.patternrec;

import java.util.HashMap;
import java.util.Map;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.objects.Strip;
import org.jlab.clas.tracking.trackrep.Helix;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.svt.SVTGeometry;

/**
 *
 * @author veronique
 */
public class RoadSurfaces {
    
    public static final int N_PADDLES = 48;
    public static final double WIDTH = 360.0 / N_PADDLES;  // 7.5 degrees
    
    public static Map<Integer, Map<Integer, double[]>> sVTModules;//<layer, <sector, [phi range]>>
    public static Map<Integer, Map<Integer, double[]>> bMTModules;
    public static Map<Integer,Map<Integer, Surface>> roadSurfaces;
    public static void getRoadSurfaces() {
        roadSurfaces = new HashMap<>();
        sVTModules = new HashMap<>();
        Strip strip = new Strip(0, 0, 0);
        for(int ilayer=0; ilayer<Geometry.getInstance().getSVT().NLAYERS; ilayer++) {
            int layer = ilayer+1;
            Map<Integer, Surface> sectorMap = new HashMap<>();
            Map<Integer, double[]> sectorModule = new HashMap<>();
            for(int isector = 0; isector<Geometry.getInstance().getSVT().NSECTORS[ilayer]; isector++) {
                int sector = isector+1;
                Surface s = Geometry.getInstance().getSVT().getSurface(layer, sector, strip) ;
                sectorMap.put(sector, s);
                double[] phiRange ;
                Line3D ln = Geometry.getInstance().getSVT().getModule(layer, sector);
                double phio = Math.atan2(ln.origin().y(), ln.origin().x()); 
                double phie = Math.atan2(ln.end().y(), ln.end().x());
                phiRange = computePhiRange(phio, phie);
                sectorModule.put(sector, phiRange); 
            }
            roadSurfaces.put(layer, sectorMap);
            sVTModules.put(layer, sectorModule);
        }
        for(int ilayer=0; ilayer<6; ilayer++) {
            int layer = ilayer+1;
            Map<Integer, Surface> sectorMap = new HashMap<>();
            for(int isector = 0; isector<3; isector++) {
                int sector = isector+1;
                Surface s = Geometry.getInstance().getBMT().getSurface(layer, sector, strip) ;
                sectorMap.put(sector, s);
            }
            roadSurfaces.put(layer+6, sectorMap);
        }
        System.out.println("ROAD SURFACES LOADED!!!");
    }
    
    public static void mapRoadSurfaces(Helix helix, Map<Integer, Surface> map) {
        map.clear();
        for(int l =0; l<6; l++) { 
            int slayer=l+1;
            Point3D po = helix.getHelixPointAtR(SVTGeometry.getLayerRadius(slayer));
            double phi = normalizePhi(Math.atan2(po.y(), po.x()));
            int sector=-1;
            for(Integer s : sVTModules.get(slayer).keySet()) {
                if(phi>sVTModules.get(slayer).get(s)[0] 
                        && phi<sVTModules.get(slayer).get(s)[1]) {
                    sector = s;
                    break;
                }
            }
            int blayer = l+7;
            Point3D bpo = helix.getHelixPointAtR(Geometry.getInstance().getBMT().getRadiusMidDrift(slayer)); 
            int bsector = Geometry.getInstance().getBMT().getSector(slayer, bpo);
            Map<Integer, Surface> sectorMap1 = roadSurfaces.get(slayer);
            if(sectorMap1.containsKey(sector))
                map.put(slayer, sectorMap1.get(sector));
             Map<Integer, Surface> sectorMap2 = roadSurfaces.get(blayer);
            if(sectorMap2.containsKey(bsector) )
                map.put(blayer, sectorMap2.get(bsector));
        }
        
    }
    
    private static double normalizePhi(double phi) {
        phi %= 2 * Math.PI;
        if (phi < 0) phi += 2 * Math.PI;
        return phi;
    }
    private static double[] computePhiRange(double phio, double phie) {
        double[] phiRange = new double[2];
        phio = normalizePhi(phio); 
        phie = normalizePhi(phie);

        double dphi = normalizePhi(phie - phio); // shortest positive difference

        if (dphi <= Math.PI) {
            // Normal increasing range (no wrap-around)
            phiRange[0] = phio;
            phiRange[1] = phie;
        } else {
            // Wrap-around range across 0 radians
            phiRange[0] = phie;
            phiRange[1] = phio;
        }
        return phiRange;
    }
    
    /**
     * Returns the CTOF paddle number (1..48) for a given phi angle in degrees.
     * Paddle 1 starts at 0° and ends at 7.5°, paddle 2 starts at 7.5°, etc.
     * 
     * @param phiDeg input phi angle in degrees (can be negative or >360)
     * @return paddle number in [1..48]
     */
    public static int getCTOFPaddle(double phiDeg) {
        
        // normalize phi to [0,360)
        double phiNorm = phiDeg % 360.0;
        if (phiNorm < 0) phiNorm += 360.0;

        // determine bin index
        int paddle = (int) Math.floor(phiNorm / WIDTH) + 1;

        // wrap-around safety
        if (paddle > N_PADDLES) paddle = N_PADDLES;
        return paddle;
    }
    
    
}

