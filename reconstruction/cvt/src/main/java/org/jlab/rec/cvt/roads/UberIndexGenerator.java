/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.bmt.CCDBConstantsLoader;
import static org.jlab.rec.cvt.roads.Constants.N_PADDLES;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.options.OptionParser;
/**
 *
 * @author veronique
 */
public class UberIndexGenerator {
    
    private RoadIndexManager rim;
    private RoadSeeder seeder;
    private Map<Integer, byte[]> paddleBytes = new HashMap<>();
    public void process(String roadsDir) throws IOException {

        for (int p = 1; p <= 48; p++) {
            Path path = Paths.get(String.format("%s/svt_roads_paddle_%02d.bin.gz", roadsDir, p));
            if (!Files.exists(path)) continue;
            InputStream fis = Files.newInputStream(path);
            GZIPInputStream gis = new GZIPInputStream(fis) ;
            byte[] bytes = gis.readAllBytes();
            paddleBytes.put(p, bytes);
        }
        // build compact index (or load it from disk with rim.loadCompactIndex())
        rim = new RoadIndexManager();

        rim.buildUberIndex(roadsDir);
        rim.saveUberIndex(roadsDir);
        
    }
    

    public static void main(String[] args) throws IOException {
        System.out.println("MAKING ROADS INDEX FILE!!!");
        // Use your actual binary name here
        OptionParser parser = new OptionParser("cvt-roads-generator");
        parser.setRequiresInputList(false);
        parser.addRequired("-out", "output path");
        parser.addRequired("-var", "variation");
        parser.addRequired("-run", "run");
        try {
            parser.parse(args);
        } catch (Exception e) {
            System.err.println("❌ Option parsing failed: " + e.getMessage());
            System.err.println();
            System.err.println("Usage: cvt-roads-indexer -out <output-path> -var <variation> -run <run-number> ");
            System.err.println("Example: ./bin/cvt-roads-indexer -out /Users/veronique/Work/Roads/ -var rga_fall2018_bg -run 11");
            return; // avoids Runtime.exit()
        }

        String path = parser.getOption("-out").stringValue();
        String var = parser.getOption("-var").stringValue();
        int run = parser.getOption("-run").intValue();
        CCDBConstantsLoader.Load(new DatabaseConstantProvider(11, var));
        ConstantsManager ccdb = new ConstantsManager();
        ccdb.init(Arrays.asList("/calibration/svt/lorentz_angle","/calibration/mvt/bmt_voltage","/geometry/beam/position"));
        ccdb.setVariation(var);
        IndexedTable svtLorentz         = ccdb.getConstants(run, "/calibration/svt/lorentz_angle");
        IndexedTable bmtVoltage         = ccdb.getConstants(run, "/calibration/mvt/bmt_voltage");
        
        //constants init
        Geometry.getInstance().initialize(var, run, svtLorentz, bmtVoltage);
        UberIndexGenerator uig = new UberIndexGenerator();
        uig.process(path);
    }
}
