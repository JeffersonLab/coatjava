/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.clas.tracking.trackrep.Helix;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.svt.SVTGeometry;

/**
 *
 * @author veronique
 */
public class RoadMaker {
    public static final int N_PADDLES = 48;
    public static final double WIDTH = 360.0 / N_PADDLES;  // 7.5 degrees
    private String targetDirName;
    private SVTGeometry sgeo;
    private BMTGeometry bgeo;
    static double xb;
    static double yb;
    static double zt;
    static double zl;
    static double p_min = 0.35;
    static double p_max = 2.5;
    static double p_width = 0.01;
    static double theta_min = 35;
    static double theta_max = 100;
    static double theta_width = 1;
    static double phi_min = 0;
    static double phi_max = 360;
    static double phi_width = 1;
    static double z_width = 0.5;
    static double BMag = -1.0;
    public RoadMaker(SVTGeometry s_geo, BMTGeometry b_geo, 
            double x_b, double y_b, double z_t, double z_length,
            String path, double bmag) {
        sgeo = s_geo;
        bgeo = b_geo;
        xb = x_b; 
        yb = y_b; 
        zt = z_t;
        zl = z_length;
        targetDirName = path;
        BMag = bmag;
    }
    
    static int np  = (int) Math.max(Math.ceil((p_max - p_min) / p_width),1); 
    static int nth = (int) Math.max(Math.ceil((theta_max - theta_min) / theta_width),1); 
    static int nph = (int) Math.max(Math.ceil((phi_max - phi_min) / phi_width),1); 
    static int nz  = (int) Math.max(Math.ceil((2 * zl) / z_width),1);
    
    public void MakeRoads() throws IOException {
        RoadSurfaces.getRoadSurfaces();
        Map<Integer, Surface> surfaceMap = new HashMap<>();
        
        System.out.println("MAKING ROADS....");
         // Prepare output per paddle
        Map<Integer, DataOutputStream> paddleStreams = new HashMap<>();
        Map<Integer, Set<String>> paddleRoadIds = new HashMap<>();  // only store road IDs
        
        for (int p = 1; p <= N_PADDLES; p++) {
            String fileName = String.format("svt_roads_paddle_%02d.bin.gz", p);
            paddleStreams.put(p, new DataOutputStream(
                    new BufferedOutputStream(
                            new GZIPOutputStream(new FileOutputStream(fileName)))));
            paddleRoadIds.put(p, new HashSet<>());
        }
        
        long totalRoadsWritten = 0;
        int index=0;
        long start = System.currentTimeMillis();
        // --- Nested loops over charge, momentum, theta, z, phi ---
        for (int ii = 0; ii < 2; ii++) {
            int q = (int)Math.pow(-1, ii); 
            for (int i = 0; i < np; i++) {
                double p = p_min + i * p_width; 
                for (int j = 0; j < nth; j++) {
                    double theta = theta_min + j * theta_width; 
                    for (int k = 0; k < nph; k++) {
                        double phi = phi_min + k * phi_width; 
                        for (int iz = 0; iz < nz; iz++) { 
                            double z = zt - zl + iz * z_width; 
                            double d0 = 0;
                            double x = -d0 * Math.sin(Math.toRadians(phi)) + xb;
                            double y =  d0 * Math.cos(Math.toRadians(phi)) + yb;
                            // --- construct the helix ---
                            double pt = p * Math.sin(Math.toRadians(theta));
                            double pz = p * Math.cos(Math.toRadians(theta));
                            double px = pt * Math.cos(Math.toRadians(phi));
                            double py = pt * Math.sin(Math.toRadians(phi));
                            Helix helix = new Helix(x, y, z, px, py, pz, q,
                                    BMag, xb, yb, Units.MM);
                            
                            // --- construct road from surfaces ---
                            CompactRoad croad = makeCompactRoadFromHelix(helix, q, p, theta, phi, z, surfaceMap);
                            if (croad == null) 
                                continue; 

                            int paddle = getPaddleForRoad(croad, helix);  // exactly one paddle
                            String roadId = croad.getIdentifier();  // unique identifier
                            
                            // --- write immediately if not seen ---
                            if (paddleRoadIds.get(paddle).add(roadId)) {
                                DataOutputStream dos = paddleStreams.get(paddle);

                                // Write road header
                                dos.writeInt(croad.q);
                                dos.writeFloat((float)croad.p);
                                dos.writeFloat((float)croad.theta);
                                dos.writeFloat((float)croad.phi);
                                dos.writeFloat((float)croad.z);
                                dos.writeInt(croad.elements.size());

                                // Write all elements
                                for (CompactElement e : croad.elements) {
                                    dos.writeInt(e.sector);
                                    dos.writeInt(e.layer);
                                    dos.writeInt(e.strip);
                                    dos.writeInt(e.phiBin);
                                    dos.writeDouble(e.point[0]);
                                    dos.writeDouble(e.point[1]);
                                    dos.writeDouble(e.point[2]);
                                }

                                totalRoadsWritten++;
                                
                                // Optional: flush every 10k roads
                                if (totalRoadsWritten % 10000 == 0) {
                                    dos.flush();
                                    //System.gc();
                                }
                            }
                            // Immediately clear memory
                            croad.elements.clear();
                            croad = null;
                            helix = null;
                            if (index % 50000 == 0) {
                                double progress = 100.0 * index / (2 * np * nth * nph * nz);
                                    double elapsed = (System.currentTimeMillis() - start) / 1000.0;

                                    Runtime runtime = Runtime.getRuntime();
                                    long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                                    long maxMemory  = runtime.maxMemory() / (1024 * 1024);

                                    System.out.printf("Progress: %6.1f%% (%d/%d)  Elapsed: %.1fs  Memory: %dMB / %dMB%n",
                                        progress, index, 2 * np * nth * nph * nz, elapsed, usedMemory, maxMemory);
                            }
                            index++;
                        }
                    }
                }
            }
        }
        
       // --- close all streams ---
        for (DataOutputStream dos : paddleStreams.values()) dos.close();

        System.out.printf("✅ Finished writing %d unique roads to %d paddle binary files%n",
                totalRoadsWritten, N_PADDLES);
        

    // --- move files to target directory ---
    
    File targetDir = new File(targetDirName);
    if (!targetDir.exists()) targetDir.mkdirs();  // create directory if missing

    for (int p = 1; p <= N_PADDLES; p++) {
        String fileName = String.format("svt_roads_paddle_%02d.bin.gz", p);
        File source = new File(fileName);
        File dest = new File(targetDir, fileName);
        if (source.renameTo(dest)) {
            System.out.printf("Moved %s → %s%n", source.getName(), dest.getAbsolutePath());
        } else {
            System.err.printf("⚠️ Failed to move %s%n", source.getName());
        }
    }

    System.out.printf("✅ Finished writing %d unique roads to %d paddle binary files%n",
        totalRoadsWritten, N_PADDLES);

    }
    
    private CompactRoad makeCompactRoadFromHelix(Helix helix, int q, double p, double theta, double phi, double z,
            Map<Integer, Surface> surfaceMap) {
        RoadSurfaces.mapRoadSurfaces(helix, surfaceMap);
        CompactRoad croad = new CompactRoad();
        croad.q = q;
        croad.p = (float)p;
        croad.theta = (float)theta;
        croad.phi = (float)phi;
        croad.z = (float)z;
        
        for (int il = 0; il < 12; il++) {
            if (surfaceMap.containsKey(il + 1)) {
                Surface surface = surfaceMap.get(il + 1);
                Point3D pos = null;
                int strip;
                int phiBin;
                if(il<6) {
                          pos = helix.getHelixPointAtPlane(
                            surface.finitePlaneCorner1.x(),
                            surface.finitePlaneCorner1.y(),
                            surface.finitePlaneCorner2.x(),
                            surface.finitePlaneCorner2.y(), 10); 
                          
                          strip = sgeo.calcNearestStrip(pos.x(), pos.y(), pos.z(), il + 1, surface.getSector());
                          phiBin = getPhiBin(Math.toDegrees(Math.atan2(pos.y(), pos.x())));

                } else {
                       double R = surface.getRadius()+surface.getThickness()/2; 
                       pos = helix.getHelixPointAtR(R);
                       strip = bgeo.getStrip(surface.getLayer(), surface.getSector(), pos)  ; 
                       phiBin = getPhiBin(Math.toDegrees(Math.atan2(pos.y(), pos.x())));
                }
                if(strip>0)
                    if(il<6) {
                        croad.elements.add(new CompactElement(surface.getSector(), 
                                surface.getLayer(), strip, phiBin, pos.x(), pos.y(), pos.z()));
                    } else {
                        croad.elements.add(new CompactElement(surface.getSector(), 
                                surface.getLayer()+6, strip, phiBin, pos.x(), pos.y(), pos.z()));
                    }
                    //free up memory
                pos=null;
                
            }
        }
        return croad;
    }
    private int getPaddleForRoad(CompactRoad croad, Helix helix) {
        double radius = PatternRec.CTOFRadius + PatternRec.CTOFThkn / 2.0;
            Point3D point = helix.getHelixPointAtR(radius);
            double phiDeg = Math.toDegrees(Math.atan2(point.y(), point.x()));
            int paddle = getCTOFPaddle(phiDeg);
            return paddle;
    }
    
    private CompactRoad copyCompactRoad(CompactRoad src) {
        CompactRoad dst = new CompactRoad();
        dst.q       = src.q;
        dst.p       = src.p;
        dst.theta   = src.theta;
        dst.phi     = src.phi;
        dst.z       = src.z;
        
        dst.elements = new ArrayList<>(src.elements.size());
        for (CompactElement e : src.elements) {
            dst.elements.add(new CompactElement(
                e.sector, e.layer, e.strip, e.phiBin,
                e.point[0], e.point[1], e.point[2]
            ));
        }
        return dst;
    }

    public static int getPhiBin(double phiDeg) {
        final double BIN_WIDTH = phi_width;
        final int N_BINS = (int) (360.0 / BIN_WIDTH); // 180 bins

        // Normalize phi to [0, 360)
        phiDeg = phiDeg % 360.0;
        if (phiDeg < 0) phiDeg += 360.0;
        // Find bin index [0..179]
        int bin = (int) Math.floor(phiDeg / BIN_WIDTH);
        if (bin >= N_BINS) bin = N_BINS - 1;  // safety
        return bin;
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
    
    public List<CompactRoad> readRoadsBinary(String fileName) throws IOException {
        List<CompactRoad> roads = new ArrayList<>();
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(fileName))))) {

            while (dis.available() > 0) {
                int q = dis.readInt();
                double p = (double) dis.readFloat();
                double theta = (double) dis.readFloat();
                double phi = (double) dis.readFloat();
                double ztar = (double) dis.readFloat();
                int nElements = dis.readInt();

                CompactRoad road = new CompactRoad();
                
                road.q = q;
                road.p = p;
                road.theta=theta;
                road.phi=phi;
                road.z = ztar;
                
                for (int i = 0; i < nElements; i++) {
                    int sector = dis.readInt();
                    int layer = dis.readInt();
                    int strip = dis.readInt();
                    int phiBin = dis.readInt();
                    double x = dis.readDouble();
                    double y = dis.readDouble();
                    double z = dis.readDouble();
                    road.elements.add(new CompactElement(sector,layer, strip, phiBin, x, y, z));
                }
                roads.add(road);
            }
        }
        return roads;
    }

}
