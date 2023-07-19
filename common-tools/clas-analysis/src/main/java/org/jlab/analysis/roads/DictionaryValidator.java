package org.jlab.analysis.roads;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import org.jlab.analysis.roads.Dictionary.TestMode;
import org.jlab.clas.physics.Particle;
import org.jlab.detector.base.DetectorLayer;
import org.jlab.groot.base.GStyle;
import org.jlab.groot.data.H2F;
import org.jlab.groot.graphics.EmbeddedCanvasTabbed;
import org.jlab.groot.group.DataGroup;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.logging.DefaultLogger;
import org.jlab.utils.benchmark.ProgressPrintout;

import org.jlab.utils.options.OptionParser;

public class DictionaryValidator {

    private Dictionary             dictionary = null;
    private Map<String, DataGroup> dataGroups = new LinkedHashMap<>();
    private EmbeddedCanvasTabbed   canvas     = null;
            
    private final String[] charges = {"neg", "pos"};
    private final String[] Charges = {"Negative", "Positive"};
    private final String fontName = "Arial";
    
    public DictionaryValidator(){
        this.initGraphics();
    }

    public void analyzeHistos() {
        // calculate road finding efficiencies
        for(int i=0; i<Charges.length; i++) {
            this.effHisto(this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_ptheta_found"), 
                          this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_ptheta_missing"), 
                          this.dataGroups.get("Efficiency").getH2F("hi_ptheta_" + charges[i]));
            this.effHisto(this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_phitheta_found"), 
                          this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_phitheta_missing"), 
                          this.dataGroups.get("Efficiency").getH2F("hi_phitheta_" + charges[i]));
            this.effHisto(this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_vztheta_found"), 
                          this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_vztheta_missing"), 
                          this.dataGroups.get("Efficiency").getH2F("hi_vztheta_" + charges[i]));
            this.effHisto(this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_pcaldc_found"), 
                          this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_pcaldc_missing"), 
                          this.dataGroups.get("Efficiency").getH2F("hi_pcaldc_" + charges[i]));
            if(this.dataGroups.get(Charges[i] + " Tracks").getColumns()==6) {
                this.effHisto(this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_ftofdc_found"), 
                              this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_ftofdc_missing"), 
                              this.dataGroups.get("Efficiency").getH2F("hi_ftofdc_" + charges[i]));
                this.effHisto(this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_pcalftof_found"), 
                              this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_pcalftof_missing"), 
                              this.dataGroups.get("Efficiency").getH2F("hi_pcalftof_" + charges[i]));
            }
            System.out.println(Charges[i] + " particles found/missed: " + this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_ptheta_found").integral() + "/" +
                                                                        + this.dataGroups.get(Charges[i] + " Tracks").getH2F("hi_ptheta_missing").integral());
        }
    }
    
    public void createHistos(TestMode mode, int wireBin, int stripBin, int sectorDependence) {
       // roads in dictionary
        this.dataGroups.put("Dictionary", this.getHistos(mode, wireBin, stripBin, sectorDependence, charges, Charges));
        // matched roads
        this.dataGroups.put("Matched Roads", this.getHistos(mode, wireBin, stripBin, sectorDependence, charges, Charges));        
        // tracks
        String[] types = {"found", "missing"};
        for(int i=0; i<charges.length; i++) {
            this.dataGroups.put(Charges[i] + " Tracks", this.getHistos(mode, wireBin, stripBin, sectorDependence, types, types));
        }
        // efficiencies
        this.dataGroups.put("Efficiency", this.getHistos(mode, wireBin, stripBin, sectorDependence, charges, Charges));
        
    }    

    private DataGroup getHistos(TestMode mode, int wireBin, int stripBin, int sectorDependence, String[] names, String[] titles) {
        double phiRange = sectorDependence==0 ? 30 : 180;
        double pRange     = 10;
        double thetaRange = 55;
        double zRange     = 25;
        // roads in dictionary
        int ncol = mode.contains(TestMode.DCFTOFPCALU) ? 6 : 4;
        DataGroup dg  = new DataGroup(ncol,names.length);
        for(int i=0; i<names.length; i++) {
            H2F hi_ptheta = new H2F("hi_ptheta_" + names[i], titles[i], 100, 0.0, pRange, 100, 0.0, thetaRange);     
            hi_ptheta.setTitleX("p (GeV)");
            hi_ptheta.setTitleY("#theta (deg)");
            H2F hi_phitheta = new H2F("hi_phitheta_" + names[i], titles[i], 100, -phiRange, phiRange, 100, 0.0, thetaRange);     
            hi_phitheta.setTitleX("#phi (deg)");
            hi_phitheta.setTitleY("#theta (deg)");
            H2F hi_vztheta = new H2F("hi_vztheta_" + names[i], titles[i], 100, -zRange, zRange, 100, 0.0, thetaRange);     
            hi_vztheta.setTitleX("vz (cm)");
            hi_vztheta.setTitleY("#theta (deg)");
            H2F hi_ftofdc = new H2F("hi_ftofdc_" + names[i], titles[i], 120/wireBin, 0.0, 120.0, 70/stripBin, 0.0, 70.0);    
            hi_ftofdc.setTitleX("DC-R3 wire");
            hi_ftofdc.setTitleY("FTOF paddle");
            H2F hi_pcaldc = new H2F("hi_pcaldc_" + names[i], titles[i], 120/wireBin, 0.0, 120.0, 70/stripBin, 0.0, 70.0);    
            hi_pcaldc.setTitleX("DC-R3 wire");
            hi_pcaldc.setTitleY("PCAL strip");
            H2F hi_pcalftof = new H2F("hi_pcalftof_" + names[i], titles[i], 70/stripBin, 0.0, 70.0, 70/stripBin, 0.0, 70.0);    
            hi_pcalftof.setTitleX("FTOF paddle");
            hi_pcalftof.setTitleY("PCAL strip");
            dg.addDataSet(hi_ptheta,   0 + ncol*i);
            dg.addDataSet(hi_phitheta, 1 + ncol*i);
            dg.addDataSet(hi_vztheta,  2 + ncol*i);
            dg.addDataSet(hi_pcaldc,   3 + ncol*i);
            if(ncol==6) {
                dg.addDataSet(hi_ftofdc,   4 + ncol*i);
                dg.addDataSet(hi_pcalftof, 5 + ncol*i);
            }
        }
        return dg;
    }
    
    private void effHisto(H2F found, H2F miss, H2F eff) {
        for(int ix=0; ix< found.getDataSize(0); ix++) {
            for(int iy=0; iy< found.getDataSize(1); iy++) {
                double fEntry = found.getBinContent(ix, iy);
                double mEntry = miss.getBinContent(ix, iy);
                double effValue = 0;
                if(fEntry+mEntry>0) effValue = fEntry/(fEntry+mEntry);
                eff.setBinContent(ix, iy, effValue);
            }   
        }
    }
    
    private void fillHistos(String groupName, String histoName, Road road) {
        this.dataGroups.get(groupName).getH2F("hi_ptheta_"   + histoName).fill(road.getParticle().p(), Math.toDegrees(road.getParticle().theta()));
        this.dataGroups.get(groupName).getH2F("hi_phitheta_" + histoName).fill(road.getPhi(), Math.toDegrees(road.getParticle().theta()));
        this.dataGroups.get(groupName).getH2F("hi_vztheta_"  + histoName).fill(road.getParticle().vz(), Math.toDegrees(road.getParticle().theta()));
        this.dataGroups.get(groupName).getH2F("hi_pcaldc_"   + histoName).fill(road.getKey().get(5), road.getStrip(DetectorLayer.PCAL_U));
        if(this.dataGroups.get(groupName).getColumns()==6) {
            this.dataGroups.get(groupName).getH2F("hi_ftofdc_" + histoName).fill(road.getKey().get(5),road.getPaddle(DetectorLayer.FTOF1B));
            this.dataGroups.get(groupName).getH2F("hi_pcalftof_" + histoName).fill(road.getPaddle(DetectorLayer.FTOF1B), road.getStrip(DetectorLayer.PCAL_U));
        }
    }
    
    private Particle findRoad(ArrayList<Byte> wires, int dcSmear, int pcalUSmear, int pcalVWSmear) {
        Particle foundRoad = null;
        if(dcSmear>0 || pcalUSmear>0 || pcalVWSmear>0) {
            for(int k1=-dcSmear; k1<=dcSmear; k1++) {
            for(int k2=-dcSmear; k2<=dcSmear; k2++) {
            for(int k3=-dcSmear; k3<=dcSmear; k3++) {
            for(int k4=-dcSmear; k4<=dcSmear; k4++) {
            for(int k5=-dcSmear; k5<=dcSmear; k5++) {
            for(int k6=-dcSmear; k6<=dcSmear; k6++) {
            for(int k7=-pcalUSmear;   k7<=pcalUSmear;  k7++) {
            for(int k8=-pcalUSmear;   k8<=pcalUSmear;  k8++) {
            for(int k9=-pcalVWSmear;  k9<=pcalVWSmear; k9++) {
            for(int k10=-pcalVWSmear; k10<=pcalVWSmear; k10++) {
                ArrayList<Byte> wiresCopy = new ArrayList<>();
                wiresCopy.add((byte) (wires.get(0)  + k1));
                wiresCopy.add((byte) (wires.get(1)  + k2));
                wiresCopy.add((byte) (wires.get(2)  + k3));
                wiresCopy.add((byte) (wires.get(3)  + k4));
                wiresCopy.add((byte) (wires.get(4)  + k5));
                wiresCopy.add((byte) (wires.get(5)  + k6));
                wiresCopy.add((byte) (wires.get(6)  + k7));
                wiresCopy.add((byte) 0); //panel 2
                wiresCopy.add((byte) (wires.get(8)  + k8));
                wiresCopy.add((byte) (wires.get(9)  + k9));
                wiresCopy.add((byte) (wires.get(10) + k10));
                wiresCopy.add((byte) 0); //htcc
                wiresCopy.add((byte) (wires.get(12)));
                if(this.dictionary.containsKey(wiresCopy)) {
                    foundRoad=this.dictionary.get(wiresCopy);
                    break;
                }
            }}}}}}}}}}
        }
        else {
            if(this.dictionary.containsKey(wires)) foundRoad=this.dictionary.get(wires);
        } 
        return foundRoad;
    }
    
    public EmbeddedCanvasTabbed getCanvas() {
        return canvas;
    }

    public void init(String filename, TestMode mode, int wireBin, int stripBin, int sectorDependence) {
        this.dictionary = new Dictionary();
        this.dictionary.readDictionary(filename, mode, wireBin, stripBin, sectorDependence);
        this.createHistos(mode, wireBin, stripBin, sectorDependence);
        this.plotRoads();
    }
        
    private void initGraphics() {
        GStyle.getAxisAttributesX().setTitleFontSize(18);
        GStyle.getAxisAttributesX().setLabelFontSize(14);
        GStyle.getAxisAttributesY().setTitleFontSize(18);
        GStyle.getAxisAttributesY().setLabelFontSize(14);
        GStyle.getAxisAttributesZ().setLabelFontSize(14);
        GStyle.getAxisAttributesX().setLabelFontName(this.fontName);
        GStyle.getAxisAttributesY().setLabelFontName(this.fontName);
        GStyle.getAxisAttributesZ().setLabelFontName(this.fontName);
        GStyle.getAxisAttributesX().setTitleFontName(this.fontName);
        GStyle.getAxisAttributesY().setTitleFontName(this.fontName);
        GStyle.getAxisAttributesZ().setTitleFontName(this.fontName);
        GStyle.setGraphicsFrameLineWidth(1);
        GStyle.getH1FAttributes().setLineWidth(2);
    }
        
    public void plotHistos() {
        this.analyzeHistos();
        for(String key : this.dataGroups.keySet()) {
            if( this.canvas == null)
                this.canvas = new EmbeddedCanvasTabbed(key);
            else
                this.canvas.addCanvas(key);
            this.canvas.getCanvas(key).draw(this.dataGroups.get(key));
            this.canvas.getCanvas(key).setGridX(false);
            this.canvas.getCanvas(key).setGridY(false);
            this.canvas.getCanvas(key).setFont(this.fontName);
            this.canvas.getCanvas(key).setTitleSize(18);
            if(key.equals("Dictionary")) {
                this.canvas.getCanvas(key).getPad(0).getAxisZ().setLog(true);
                this.canvas.getCanvas(key).getPad(5).getAxisZ().setLog(true); 
            }
            else if(key.equals("Matched Roads")){
                this.canvas.getCanvas(key).getPad(0).getAxisZ().setLog(true);
                this.canvas.getCanvas(key).getPad(5).getAxisZ().setLog(true);                 
            }
        }
    }
    
    public void plotRoads() {
        for(ArrayList<Byte> key : this.dictionary.keySet()) {
            Road road = this.dictionary.getRoad(key);
            int icharge = 0;
            if(road.getParticle().charge()>0) icharge = 1;
            this.fillHistos("Dictionary", charges[icharge], road);
        }
    }
    
    /**
     * Test selected dictionary on input event file
     * @param fileName: input event hipo file
     * @param wireBin: dc wire binning
     * @param pcalBin: pcal strip binning
     * @param sectorDependence: sector-dependence mode (0=false, 1=true)
     * @param smearing: smaring in matching
     * @param mode: test mode
     * @param maxEvents: max number of events to process
     * @param pidSelect: pid for track selection
     * @param chargeSelect: charge for track selection
     * @param thrs: momentum threshold for track selection
     * @param vzmin: minimum track vz
     * @param vzmax: maximum track vz
     */
    public void processFile(String fileName, int wireBin, int pcalBin, int sectorDependence, int smearing,
                            TestMode mode, int maxEvents, int pidSelect, int chargeSelect, double thrs,
                            double vzmin, double vzmax) {
        // testing dictionary on event file
        
        System.out.println("\nTesting dictionary on file " + fileName);
        
        ProgressPrintout progress = new ProgressPrintout();

        HipoDataSource reader = new HipoDataSource();
        reader.open(fileName);
        int nevent = -1;
        while(reader.hasEvent() == true) {
            if(maxEvents>0) {
                if(nevent>= maxEvents) break;
            }
            DataEvent event = reader.getNextEvent();
            nevent++;
            
            ArrayList<Road> particles = Road.getRoads(event, chargeSelect, pidSelect, thrs, vzmin, vzmax);
            for(Road part : particles) {
                part.setBinning(wireBin, pcalBin, sectorDependence);
                if(!part.isValid(mode)) continue;
                int ichPart   = (part.getParticle().charge()+1)/2;
                Road road = null;
                for(ArrayList<Byte> key : part.getKeys(mode, smearing)) {
                    if(this.dictionary.containsKey(key)) {
                        road = this.dictionary.getRoad(key);
                        break;
                    }
                }
                if(road != null) {
                    int ichRoad = (road.getParticle().charge()+1)/2;
                    this.fillHistos("Matched Roads", charges[ichRoad], road);
                    this.fillHistos(Charges[ichPart] + " Tracks", "found", part);
                }
                else {
//                    System.out.println(part);
                    this.fillHistos(Charges[ichPart] + " Tracks", "missing", part);
                }
            }
            progress.updateStatus();
        }
        progress.showStatus();
    }
    

    

    public static void main(String[] args) {
        
        DefaultLogger.debug();

        OptionParser parser = new OptionParser("dict-validator");
        parser.setRequiresInputList(false);
        parser.addRequired("-dict"   , "dictionary file name");
        parser.addRequired("-i"      , "event file for dictionary test");
        parser.addOption("-pid"      , "0", "select particle PID for new dictionary, 0: no selection,");
        parser.addOption("-charge"   , "0", "select particle charge for new dictionary, 0: no selection");
        parser.addOption("-wire"     , "1", "dc wire bin size in road finding");
        parser.addOption("-strip"    , "2", "pcal strip bin size in road finding");
        parser.addOption("-sector"   , "0", "sector dependent roads, 0=false, 1=true)");
        parser.addOption("-smear"    , "1", "smearing in wire/paddle/strip matching");
        parser.addOption("-mode"     , "0", "select test mode, " + TestMode.getOptionsString());
        parser.addOption("-threshold", "1", "select roads momentum threshold in GeV");
        parser.addOption("-vzmin"  , "-10", "minimum vz (cm)");
        parser.addOption("-vzmax"  ,  "10", "maximum vz (cm)");
        parser.addOption("-n"        ,"-1", "maximum number of events to process for validation");
        parser.parse(args);
        
        String dictionaryFileName = null;
        if(parser.hasOption("-dict")==true) dictionaryFileName = parser.getOption("-dict").stringValue();
        
        String testFileName = null;
        if(parser.hasOption("-i")==true) testFileName = parser.getOption("-i").stringValue();
            
        
        int pid        = parser.getOption("-pid").intValue();
        int charge     = parser.getOption("-charge").intValue();
        if(Math.abs(charge)>1) {
            System.out.println("\terror: invalid charge selection");
            System.exit(1);
        }
        int wireBin  = parser.getOption("-wire").intValue();
        if(wireBin<0) {
            System.out.println("\terror: invalid dc wire binning, value should be >0");
            System.exit(1);
        }
        int stripBin  = parser.getOption("-strip").intValue();
        if(stripBin<0) {
            System.out.println("\terror: invalid pcal strip binning, value should be >0");
            System.exit(1);
        }
        int sector     = parser.getOption("-sector").intValue();
        if(sector<0 || sector>1) {
            System.out.println("\terror: invalid sector-dependence option, allowed values are 0=false or 1=true");
            System.exit(1);
        }
        int smear  = parser.getOption("-smear").intValue();
        if(smear<0) {
            System.out.println("\terror: invalid smearing, value should be >0");
            System.exit(1);
        }
        TestMode mode  = TestMode.getTestMode(parser.getOption("-mode").intValue());
        if(mode == TestMode.UDF) {
            System.out.println("\terror: invalid test mode, " + TestMode.getOptionsString());
            System.exit(1);
        }
        int maxEvents  = parser.getOption("-n").intValue();
        
        double thrs    = parser.getOption("-threshold").doubleValue();
        double vzmin   = parser.getOption("-vzmin").doubleValue();
        double vzmax   = parser.getOption("-vzmax").doubleValue();
        
        System.out.println();
        System.out.println("Dictionary file name set to: " + dictionaryFileName);
        System.out.println("Event file set to:           " + testFileName);
        System.out.println();
        System.out.println("PID selection set to:                           " + pid);
        System.out.println("Charge selection set to:                        " + charge);
        System.out.println("Momentum threshold set to:                      " + thrs);
        System.out.println("Vertex range set to:                            " + vzmin + ":" + vzmax);
        System.out.println("Wire binning set to:                            " + wireBin);
        System.out.println("Pcal binning set to:                            " + stripBin);
        System.out.println("Sector dependence set to:                       " + sector);
        System.out.println("Smearing for wire/paddle/strip matching set to: " + smear);
        System.out.println("Test mode set to:                               " + mode);
        System.out.println("Maximum number of events to process set to:     " + maxEvents);
        
        DictionaryValidator validator = new DictionaryValidator();
        validator.init(dictionaryFileName, mode, wireBin, stripBin, sector);                
    //        tm.printDictionary();
        validator.processFile(testFileName,wireBin,stripBin,sector,smear,mode,maxEvents,pid,charge,thrs, vzmin, vzmax);
        validator.plotHistos();

        JFrame frame = new JFrame("Roads Validation");
        Dimension screensize = null;
        screensize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize((int) (screensize.getWidth() * 0.8), (int) (screensize.getHeight() * 0.6));
        frame.add(validator.getCanvas());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    
}