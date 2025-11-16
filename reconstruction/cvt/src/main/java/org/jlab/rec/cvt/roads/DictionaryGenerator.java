package org.jlab.rec.cvt.roads;

import java.io.IOException;
import java.util.Arrays;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.bmt.CCDBConstantsLoader;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.options.OptionParser;


/**
 * 
 * @author ziegler
 */
public final class DictionaryGenerator {
    private String outputPath;
    private double[] xyBeam = new double[2];
    private double targetCen; 
    private double targetLen;
    private double BMag;
    public DictionaryGenerator(String outputpath, String variation, int run, 
            double bmag) {
        this.init(outputpath, variation, run, bmag);
    }
    
    public void init(String outputpath, String variation, int run, double bmag) {
        outputPath = outputpath;
        CCDBConstantsLoader.Load(new DatabaseConstantProvider(11, variation));
        ConstantsManager ccdb = new ConstantsManager();
        ccdb.init(Arrays.asList("/calibration/svt/lorentz_angle","/calibration/mvt/bmt_voltage","/geometry/beam/position"));
        ccdb.setVariation(variation);
        IndexedTable svtLorentz         = ccdb.getConstants(run, "/calibration/svt/lorentz_angle");
        IndexedTable bmtVoltage         = ccdb.getConstants(run, "/calibration/mvt/bmt_voltage");
        IndexedTable beamPos            = ccdb.getConstants(run, "/geometry/beam/position");
        
        //constants init
        Geometry.getInstance().initialize(variation, run, svtLorentz, bmtVoltage);
        double[] xyB = new double[2];
        xyB[0] = beamPos.getDoubleValue("x_offset", 0, 0, 0)*10;
        xyB[1] = beamPos.getDoubleValue("y_offset", 0, 0, 0)*10;
        double targetC = Geometry.getInstance().getTargetZOffset();
        double targetL = Geometry.getInstance().getTargetHalfLength();
        xyBeam = xyB;
        targetCen = targetC;
        targetLen = targetL;
        System.out.println("Target Center (mm) "+targetCen);
        System.out.println("Target Length (mm) "+targetLen);
        BMag = bmag;
    }
    
    public void process() {
        BitPackedRoadMaker rm;
        try {
                rm = new BitPackedRoadMaker(Geometry.getInstance().getSVT(), Geometry.getInstance().getBMT(),
                        xyBeam[0], xyBeam[1], targetCen, targetLen, outputPath,BMag);
                rm.MakeRoads();
            } catch (IOException ex) {
                System.getLogger(DictionaryGenerator.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
    }
    public static void main(String[] args) {
        //DefaultLogger.debug();
        System.out.println("MAKING ROADS!!!");
        // Use your actual binary name here
        OptionParser parser = new OptionParser("cvt-roads-generator");
        parser.setRequiresInputList(false);
        parser.addRequired("-out", "output path");
        parser.addRequired("-var", "variation");
        parser.addRequired("-run", "run");
        parser.addRequired("-b", "Solenoid scale");
        System.out.println("args "+args.length);
        System.out.println("Args length = " + args.length);
        for (int i = 0; i < args.length; i++) {
            System.out.printf("  args[%d] = '%s'%n", i, args[i]);
        }
        try {
            parser.parse(args);
        } catch (Exception e) {
            System.err.println("❌ Option parsing failed: " + e.getMessage());
            System.err.println();
            System.err.println("Usage: cvt-roads-generator -out <output-path> -var <variation> -run <run-number> -b <solenoid magnitude>");
            System.err.println("Example: ./bin/cvt-roads-generator -out test -var rga_fall2018_bg -run 11 -b 5.0");
            return; // avoids Runtime.exit()
        }

        String path = parser.getOption("-out").stringValue();
        String var = parser.getOption("-var").stringValue();
        int run = parser.getOption("-run").intValue();
        double BMag = parser.getOption("-b").doubleValue();
        System.out.printf("✅ Starting dictionary generation with path=%s, variation=%s, run=%d%n",
                path, var, run);

        DictionaryGenerator maker = new DictionaryGenerator(path, var, run, BMag);
        maker.process();

        System.out.println("✅ Dictionary generation completed.");
    }

    
    
}
