/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.clas.tracking.trackrep.Helix;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import static org.jlab.rec.cvt.roads.Constants.N_PADDLES;
import org.jlab.rec.cvt.svt.SVTGeometry;
import static org.jlab.rec.cvt.roads.Constants.CTOFRADIUS;
import static org.jlab.rec.cvt.roads.Constants.CTOFTHKN;
import static org.jlab.rec.cvt.roads.Constants.WIDTH;
/**
 *
 * @author veronique
 */


public class BitPackedRoadMaker {
    
    private final String targetDirName;
    private final SVTGeometry sgeo;
    private final BMTGeometry bgeo;
    public static double xb, yb, zt, zl, BMag;
    public static final double p_min = 0.35, p_max = 2.5, p_width = 0.01;
    public static final double theta_min = 35, theta_max = 120, theta_width = 1;
    public static final double phi_min = 0, phi_max = 360, phi_width = 1;
    
    
    public static int np  = (int) Math.max(Math.ceil((p_max - p_min) / p_width),1); 
    public static int nth = (int) Math.max(Math.ceil((theta_max - theta_min) / theta_width),1); 
    public static int nph = (int) Math.max(Math.ceil((phi_max - phi_min) / phi_width),1); 
    
    public static double z_width = 5.0;
    private static int nz  ;

    public BitPackedRoadMaker(SVTGeometry s_geo, BMTGeometry b_geo,
                              double x_b, double y_b, double z_t, double z_length,
                              String path, double bmag) {
        this.sgeo = s_geo;
        this.bgeo = b_geo;
        xb = x_b; yb = y_b; zt = z_t; zl = z_length/2;
        targetDirName = path;
        BMag = bmag;
        double z_min = zt-zl;
        double z_max = zt+zl; 
        if(zl>0) z_width=zl/2;
        nz  = (int) Math.max(Math.ceil((z_max-z_min) / z_width),1);
    }

    
    public void MakeRoads() throws IOException {
        RoadSurfaces.getRoadSurfaces();
        Map<Integer, Surface> surfaceMap = new HashMap<>();

        System.out.println("MAKING ROADS WITH BIT PACKING...");

        Map<Integer, BitIO.BitOutputStream> paddleStreams = new HashMap<>();
        Map<Integer, GZIPOutputStream> gzipStreams = new HashMap<>();
        Map<Integer, Set<Long>> paddleStripCombos = new HashMap<>(); // stores numeric hashes
        Map<Integer, ByteArrayOutputStream> memStreams = new HashMap<>();
        //Map<Integer, Map<Long, CompactRoad>> paddleSavedRoads = new HashMap<>();
        
        // Prepare streams & combo sets
        for (int p = 1; p <= N_PADDLES; p++) {
            paddleStripCombos.put(p, new HashSet<>());
        //    paddleSavedRoads.put(p, new HashMap<>());
            String fileName = String.format("svt_roads_paddle_%02d.bin.gz", p);
            GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(fileName));
            gzipStreams.put(p, gzip);
            paddleStreams.put(p, new BitIO.BitOutputStream(gzip));
            
        }

        long totalRoadsWritten = 0;
        int index = 0;
        long start = System.currentTimeMillis();
        printHeapUsage("After initializing streams");
        
        for (int ii = 0; ii < 2; ii++) {
            int q = (int) Math.pow(-1, ii);
            for (int i = 0; i < np; i++) {
                double pVal = p_min + i * p_width;
                for (int j = 0; j < nth; j++) {
                    double theta = theta_min + j * theta_width;
                    for (int k = 0; k < nph; k++) {
                        double phi = phi_min + k * phi_width;
                        for (int iz = 0; iz < nz; iz++) {
                            double z = zt - zl + iz * z_width;

                            double d0 = 0;
                            double x = -d0 * Math.sin(Math.toRadians(phi)) + xb;
                            double y =  d0 * Math.cos(Math.toRadians(phi)) + yb;

                            double pt = pVal * Math.sin(Math.toRadians(theta));
                            double pz = pVal * Math.cos(Math.toRadians(theta));
                            double px = pt * Math.cos(Math.toRadians(phi));
                            double py = pt * Math.sin(Math.toRadians(phi));

                            Helix helix = new Helix(x, y, z, px, py, pz, q, BMag, xb, yb, Units.MM);
                            CompactRoad croad = makeCompactRoadFromHelix(helix, surfaceMap);
                            if (croad == null) continue;

                            int paddle = getPaddleForRoad(croad, helix);
                            croad.paddle=paddle;

                            
                            // --- Write the unique road ---
                            BitIO.BitOutputStream bos = paddleStreams.get(paddle);
                            
                            int nElements = croad.elements.size();
                            bos.writeBits(nElements, 4);
                            for (CompactElement e : croad.elements) {
                                bos.writeBits(e.layer - 1, 4);
                                bos.writeBits(e.sector - 1, 5);
                                bos.writeBits(e.strip - 1, 11);
                            }
                            bos.padToByte();

                            totalRoadsWritten++;
                            index++;

                            if (index % 1_000_000 == 0) {
                                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                                System.out.printf("Progress: %6.1f%%  Roads: %d  Time: %.1fs%n",
                                        100.0 * index / (2.0 * np * nth * nph *nz), totalRoadsWritten, elapsed);
                                for (GZIPOutputStream gz : gzipStreams.values()) gz.flush();
                                System.gc();
                                printHeapUsage("Progress checkpoint");
                            }

                            // free temporary objects
                            croad.elements.clear();
                            croad = null;
                            helix = null;
                        }
                    }
                }
            }
        }

        // ---- flush & close streams ----
        for (int p = 1; p <= N_PADDLES; p++) {
            BitIO.BitOutputStream bos = paddleStreams.get(p);
            if (bos != null) bos.close();
        }

        System.out.printf("✅ Finished writing %d unique roads (by strip combination) across %d paddles%n",
                          totalRoadsWritten, N_PADDLES);

        File targetDir = new File(targetDirName);
        if (!targetDir.exists()) targetDir.mkdirs();

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
    }


    private void printHeapUsage(String label) {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        long max = runtime.maxMemory();
        System.out.printf("[MEMORY] %s: Used = %.2f MB, Free = %.2f MB, Total = %.2f MB, Max = %.2f MB%n",
                label, used / 1e6, free / 1e6, total / 1e6, max / 1e6);
    }

    private static int quantize(double v) {
        // compress to ±2048 mm range in 0.125 mm steps
        int qv = (int)Math.round(v * 8);
        return Math.max(Math.min(qv, 32767), -32768);
    }


    public class BitOutputStream {
        private final OutputStream out;
        private int currentByte = 0;
        private int numBitsFilled = 0;

        public BitOutputStream(OutputStream out) { this.out = out; }

        public void writeBits(int value, int numBits) throws IOException {
            for (int i = numBits - 1; i >= 0; i--) {
                currentByte = (currentByte << 1) | ((value >>> i) & 1);
                numBitsFilled++;
                if (numBitsFilled == 8) flushByte();
            }
        }

        private void flushByte() throws IOException {
            out.write(currentByte);
            numBitsFilled = 0;
            currentByte = 0;
        }

        public void flush() throws IOException {
            while (numBitsFilled != 0) writeBits(0, 1);
            out.flush();
        }
    }

    private CompactRoad makeCompactRoadFromHelix(Helix helix,
            Map<Integer, Surface> surfaceMap) {
        RoadSurfaces.mapRoadSurfaces(helix, surfaceMap);
        CompactRoad croad = new CompactRoad();
        
        for (int il = 0; il < 6; il++) {
            if (surfaceMap.containsKey(il + 1)) {
                Surface surface = surfaceMap.get(il + 1);
                Point3D pos = null;
                int strip;
                //skip BMT for now
                
                pos = helix.getHelixPointAtPlane(
                surface.finitePlaneCorner1.x(),
                surface.finitePlaneCorner1.y(),
                surface.finitePlaneCorner2.x(),
                surface.finitePlaneCorner2.y(), 10); 

                strip = sgeo.calcNearestStrip(pos.x(), pos.y(), pos.z(), il + 1, surface.getSector());

                
                if(strip>0 && strip<257){
                    croad.elements.add(new CompactElement(surface.getSector(), 
                            surface.getLayer(), strip));
                } 
                    //free up memory
                pos=null;
                
            }
        }
        return croad;
    }
    
    private int getPaddleForRoad(CompactRoad croad, Helix helix) {
        double radius = CTOFRADIUS +CTOFTHKN / 2.0;
            Point3D point = helix.getHelixPointAtR(radius);
            double phiDeg = Math.toDegrees(Math.atan2(point.y(), point.x()));
            int paddle = getCTOFPaddle(phiDeg);
            return paddle;
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
    
    
    public void MakeRoadsTest() throws IOException {
        RoadSurfaces.getRoadSurfaces();
        Map<Integer, Surface> surfaceMap = new HashMap<>();

        System.out.println("TESTING ROADS IN MEMORY...");

        long totalRoadsWritten = 0;
        long start = System.currentTimeMillis();
        int index = 0;

        for (int ii = 0; ii < 2; ii++) {
            int q = (int) Math.pow(-1, ii);

            for (int i = 0; i < np; i++) {
                double pVal = p_min + i * p_width;

                for (int j = 0; j < nth; j++) {
                    double theta = theta_min + j * theta_width;

                    for (int k = 0; k < nph; k++) {
                        double phi = phi_min + k * phi_width;

                        for (int iz = 0; iz < nz; iz++) {
                            double z = zt - zl + iz * z_width;

                            Helix helix = new Helix(
                                xb, yb, z,
                                pVal * Math.sin(Math.toRadians(theta)) * Math.cos(Math.toRadians(phi)),
                                pVal * Math.sin(Math.toRadians(theta)) * Math.sin(Math.toRadians(phi)),
                                pVal * Math.cos(Math.toRadians(theta)),
                                q, BMag, xb, yb, Units.MM
                            );

                            CompactRoad croad = makeCompactRoadFromHelix(helix, surfaceMap);
                            
                            if (croad == null) continue;

                            // --- Write this road to a fresh memory stream ---
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            BitIO.BitOutputStream bos = new BitIO.BitOutputStream(baos);
                           
                            bos.writeBits(croad.elements.size(), 4); // 0-15 elements

                            // ---- Bit-pack elements ----
                            for (CompactElement e : croad.elements) {
                                bos.writeBits(e.layer - 1, 4);
                                bos.writeBits(e.sector - 1, 5);
                                bos.writeBits(e.strip - 1, 11);
                            }

                            // --- Ensure byte-alignment and flush ---
                            bos.padToByte();
                            //bos.close();

                            // --- Read back the same road ---
                            byte[] roadBytes = baos.toByteArray();
                            BitIO.BitInputStream bis = new BitIO.BitInputStream(new ByteArrayInputStream(roadBytes));

                            int nElements = (int) bis.readBits(4);

                            boolean failed = (nElements != croad.elements.size());

                            int[] layer = new int[nElements], sector = new int[nElements], strip = new int[nElements];

                            for (int ei = 0; ei < nElements; ei++) {
                                layer[ei] = (int) bis.readBits(4) + 1;
                                sector[ei] = (int) bis.readBits(5) + 1;
                                strip[ei] = (int) bis.readBits(11) + 1;
                            }

                            if (failed) {
                                System.out.println(" ROAD " + totalRoadsWritten + ":" + croad.toString());

                                for (int ei = 0; ei < nElements; ei++) {
                                    System.out.printf(" Verify Road Element %d: sector=%d, layer=%d, strip=%d%n",
                                            ei + 1, sector[ei], layer[ei], strip[ei]);
                                }
                            }

                            // --- Clean up ---
                            layer = null;
                            sector = null;
                            strip = null;
                            croad.elements.clear();
                            totalRoadsWritten++;
                            index++;

                            if (index % 5000 == 0) {
                                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                                System.out.printf("Progress: %6.1f%%  Roads: %d  Time: %.1fs%n",
                                        100.0 * index / (2.0 * np * nth * nph * nz), totalRoadsWritten, elapsed);
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("✅ Finished testing %d roads in memory%n", totalRoadsWritten);
    }

}
