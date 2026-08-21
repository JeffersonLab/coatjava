package org.jlab.service.uber;

import org.jlab.clas.reco.UberEngine;

import org.jlab.clas.swimtools.MagFieldsEngine;
import org.jlab.service.raster.RasterEngine;
import org.jlab.rec.cvt.services.CVTEngine;
import org.jlab.rec.cvt.services.CVTSecondPassEngine;
import org.jlab.rec.ft.FTEBEngine;
import org.jlab.rec.ft.cal.FTCALEngine;
import org.jlab.rec.ft.hodo.FTHODOEngine;
import org.jlab.rec.ft.trk.FTTRKEngine;
import org.jlab.rec.rich.RICHEBEngine;
import org.jlab.rec.service.vtx.VTXEngine;
import org.jlab.service.band.BANDEngine;
import org.jlab.service.cnd.CNDCalibrationEngine;
import org.jlab.service.ctof.CTOFEngine;
import org.jlab.service.dc.DCHBClustering;
import org.jlab.service.dc.DCHBPostClusterAI;
import org.jlab.service.dc.DCTBEngine;
import org.jlab.service.eb.EBHBEngine;
import org.jlab.service.eb.EBTBEngine;
import org.jlab.service.ec.ECEngine;
import org.jlab.service.fmt.FMTEngine;
import org.jlab.service.ftof.FTOFHBEngine;
import org.jlab.service.ftof.FTOFTBEngine;
import org.jlab.service.htcc.HTCCReconstructionService;
import org.jlab.service.ltcc.LTCCEngine;
import org.jlab.service.mltn.MLTDEngine;
import org.jlab.service.rtpc.RTPCEngine;
import org.jlab.calibration.service.CalibBanksEngine;
import org.jlab.service.ai.DCClsComboEngine;
import org.jlab.service.ai.DCDenoiseEngine;
import org.jlab.service.dc.DCHBPostClusterConv;
import org.jlab.service.dc.DCHBTrackingAI;
import org.jlab.service.dc.DCTBEngineAI;
import org.jlab.service.eb.EBHBAIEngine;
import org.jlab.service.eb.EBTBAIEngine;

/**
 * A container of engine sequences for shorter YAMLs.
 *
 * @author baltzell
 */
public class Uber {

    public static class First extends UberEngine {
        public First(){
            super("1ST","uber","1.0");
            add(new MagFieldsEngine(),
                new RasterEngine());
        }
    }

    public static class Central extends UberEngine {
        public Central(){
            super("CD","uber","1.0");
            add(new CVTEngine(),
                new CTOFEngine(),
                new CNDCalibrationEngine());
        }
    }

    public static class ForwardTagger extends UberEngine {
        public ForwardTagger(){
            super("FT","uber","1.0");
            add(new FTCALEngine(),
                new FTHODOEngine(),
                new FTTRKEngine(),
                new FTEBEngine());
        }
    }

    public static class HitBasedCV extends UberEngine {
        public HitBasedCV() {
            super("HB","uber","1.0");
            add(new DCHBClustering(),
                new DCHBPostClusterConv(),
                new BANDEngine(),
                new HTCCReconstructionService(),
                new LTCCEngine(),
                new FTOFHBEngine(),
                new ECEngine(),
                new EBHBEngine());
        }
    }

    public static class HitBasedAIClassic extends UberEngine {
        public HitBasedAIClassic() {
            super("HB","uber","1.0");
            add(new DCHBClustering(),
                new MLTDEngine(),
                new DCHBPostClusterAI("HB"),
                new BANDEngine(),
                new HTCCReconstructionService(),
                new LTCCEngine(),
                new FTOFHBEngine(),
                new ECEngine(),
                new EBHBEngine());
        }
    }

    public static class HitBasedAI extends UberEngine {
        public HitBasedAI() {
            super("HB","uber","1.0");
            add(new DCDenoiseEngine(),
                new DCHBClustering(),
                new DCClsComboEngine(),
                new DCHBTrackingAI("HB"),
                new BANDEngine(),
                new HTCCReconstructionService(),
                new LTCCEngine(),
                new FTOFHBEngine(),
                new ECEngine(),
                new EBHBEngine());
        }
    }

    public static class HitBasedAIClassicCV extends UberEngine {
        public HitBasedAIClassicCV() {
            super("HB","uber","1.0");
            add(new DCHBClustering(),
                new MLTDEngine(),
                new DCHBPostClusterConv(),
                new DCHBPostClusterAI("AI"),
                new BANDEngine(),
                new HTCCReconstructionService(),
                new LTCCEngine(),
                new FTOFHBEngine(),
                new ECEngine(),
                new EBHBEngine(),
                new EBHBAIEngine());
        }
    }

    public static class HitBasedAICV extends UberEngine {
        public HitBasedAICV() {
            super("HB","uber","1.0");
            add(new DCDenoiseEngine(),
                new DCHBClustering(),
                new DCHBPostClusterConv(),
                new DCHBTrackingAI("AI"),
                new BANDEngine(),
                new HTCCReconstructionService(),
                new LTCCEngine(),
                new FTOFHBEngine(),
                new ECEngine(),
                new EBHBEngine(),
                new EBHBAIEngine());
        }
    }

    public static class TimeBasedCV extends UberEngine {
        public TimeBasedCV() {
            super("TB","uber","1.0");
            add(new DCTBEngine(),
                new FMTEngine(),
                new CVTSecondPassEngine(),
                new FTOFTBEngine(),
                new EBTBEngine());
        }
    }

    public static class TimeBasedAICV extends UberEngine {
        public TimeBasedAICV() {
            super("TB","uber","1.0");
            add(new DCTBEngine(),
                new DCTBEngineAI(),
                new FMTEngine(),
                new CVTSecondPassEngine(),
                new FTOFTBEngine(),
                new EBTBEngine(),
                new EBTBAIEngine("RECAI"));
        }
    }

    public static class Last extends UberEngine {
        public Last() {
            super("NTH","uber","1.0");
            add(new RICHEBEngine(),
                new RTPCEngine(),
                new VTXEngine(),
                new CalibBanksEngine());
        }
    }
}
