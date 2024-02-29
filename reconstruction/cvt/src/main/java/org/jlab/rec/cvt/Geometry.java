package org.jlab.rec.cvt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.clas.tracking.objects.Strip;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.CTOFGeant4Factory;
import org.jlab.detector.geant4.v2.SVT.SVTStripFactory;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.prim.Arc3D;
import org.jlab.geom.prim.Cylindrical3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.groot.base.GStyle;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.graphics.EmbeddedCanvasTabbed;
import org.jlab.groot.group.DataGroup;
import org.jlab.logging.DefaultLogger;
import org.jlab.rec.cvt.bmt.BMTGeometry;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.bmt.CCDBConstantsLoader;
import org.jlab.rec.cvt.measurement.Measurements;
import org.jlab.rec.cvt.svt.SVTGeometry;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.options.OptionParser;

/**
 *
 * @author devita
 */
public class Geometry {
    
    private SVTGeometry       svtGeometry  = null;
    private BMTGeometry       bmtGeometry  = null;
    private CTOFGeant4Factory ctofGeometry = null;
    private Detector          cndGeometry  = null;
    private List<Surface>   outerSurfaces  = null;
    
    private Map<Integer, List<Surface>> targetSurfaces = new LinkedHashMap<>();
    private double targetPosition = 0;  
    private double targetHalfLength = 0;  
    private double targetRadius = 0;  
    
    private List<Material> targetMaterials = null;
    private Surface targetCellSurface = null;
    private Surface scatteringChamberSurface = null;
    private Surface targetShieldSurface = null;

    // tungsten shield
    public static double TSHIELDRMIN    = 51;
    public static double TSHIELDRMAX    = 51.051;
    public static double TSHIELDLENGTH  = 360;
    public static double TSHIELDZPOS    = -50;
    public static double TSHIELDRADLEN  = 6.76/19.3 *10; // X0(g/cm2) / density(g/cm3) * 10; 
    public static double TSHIELDZOVERA  = 0.40252;
    public static double TSHIELDRHO     = 19.3E-3; // g/mm3
    public static double TSHIELDI       = 727;     // eV

    // cryogenic target
    private static final Material CRYOLH2 = new Material("LH2", 8.85, 0.0708E-3, 0.99212, 8904.0, 21.8, Units.MM);
    private static final Material CRYOLD2 = new Material("LD2", 8.85, 0.1638E-3, 0.49650, 7691.0, 21.8, Units.MM);
    private static final Material CRYOTARGETKAPTON = new Material("Kapton", 50E-3, 1.42E-3, 0.501, 285.7, 79.6, Units.MM);
    private static final Material CRYOTARGETRHOACELL = new Material("Rhoacell", 10.4, 0.1E-3, 0.5392, 1000, 93.0, Units.MM);
    
//    // polarized target NOW in CCDB
//    private static double POLTARBATHRADIUS = 30;
//    private static double POLTARRADIUS     = 45;
//    private static final Material POLTARNH3 = new Material("NH3", 7.5, 0.5782E-3, 0.55, 916.6, 47.9, Units.MM);
//    private static final Material POLTARND3 = new Material("ND3", 10.0, 0.6622E-3, 0.5, 893.7, 47.9, Units.MM);
//    private static final Material POLTARCUP = new Material("PCTFE", 0.1, 2.135E-3, 0.5, 134.2, 120.7, Units.MM);
//    private static final Material POLTARLHE = new Material("LHE", 20.0, 0.147E-3, 0.5, 6689.4, 41.8, Units.MM);
//    private static final Material POLTARBATH = new Material("BATH", 0.76, 2.135E-3, 0.5, 134.2, 120.7, Units.MM);
//    private static final Material POLTARAL = new Material("Al", 1.63, 2.7E-3, 0.48181, 88.9, 166, Units.MM);
//    private static final Material POLTARCF = new Material("CarbonFiber", 1.0, 1.75E-3, 0.51342, 246.9, 78, Units.MM);

    // other materials
    public  static final Material SCINTILLATOR = new Material("Scintillator", 1, 1.03E-3, 0.54141, 439.0, 64.7, Units.MM);
    public  static final Material VACUUM = new Material("Vacuum", 1, 0, 1, Double.POSITIVE_INFINITY, 100, Units.MM);
    
    private static boolean LOADED;
    
    private final static Logger LOGGER = Logger.getLogger(Geometry.class.getName());
    
    
    // private constructor for a singleton
    private Geometry() {
    }
    
    // singleton
    private static Geometry instance = null;
    
    /**
     * public access to the singleton
     * 
     * @return the cvt geometry singleton
     */
    public static Geometry getInstance() {
            if (instance == null) {
                    instance = new Geometry();
            }
            return instance;
    }
    
    public synchronized static void initialize(String variation, int run, IndexedTable svtLorentz, IndexedTable bmtVoltage) {
        if(!LOADED) {
            Geometry.getInstance().load(variation, run, svtLorentz, bmtVoltage);
            LOADED = true;
        }
    }
        
    private synchronized void load(String variation, int run, IndexedTable svtLorentz, IndexedTable bmtVoltage) {
        
        // Load target
        ConstantProvider providerTG = GeometryFactory.getConstants(DetectorType.TARGET, run, variation);
        this.targetPosition = providerTG.getDouble("/geometry/target/position",0)*10;
        this.targetHalfLength = providerTG.getDouble("/geometry/target/length",0)*10;
        this.initTarget(providerTG);
        
        ConstantProvider providerCTOF = GeometryFactory.getConstants(DetectorType.CTOF, run, variation);
        ctofGeometry = new CTOFGeant4Factory(providerCTOF);        
        cndGeometry  =  GeometryFactory.getDetector(DetectorType.CND, run, variation);
        
        CCDBConstantsLoader.Load(new DatabaseConstantProvider(run, variation));
        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        SVTStripFactory svtFac = new SVTStripFactory(cp, true);
        svtGeometry  = new SVTGeometry(svtFac, svtLorentz);
        bmtGeometry  = new BMTGeometry(bmtVoltage);
        
        outerSurfaces = Measurements.getOuters();
    }
    
    private void initTarget(ConstantProvider provider) {
        if("LH2".equals(Constants.getInstance().getTargetType()) ||
           "LD2".equals(Constants.getInstance().getTargetType()))
            this.loadCryoTarget();
        else {
            this.loadTarget(provider);
        }
    }
    
    private void loadCryoTarget() {
        targetMaterials = new ArrayList<>();
        if("LH2".equals(Constants.getInstance().getTargetType())) 
            targetMaterials.add(CRYOLH2);
        else if("LD2".equals(Constants.getInstance().getTargetType())) 
            targetMaterials.add(CRYOLD2);
        targetMaterials.add(CRYOTARGETKAPTON);
        
        Point3D  chamberCenter = new Point3D(0, 0, this.getTargetZOffset()-100);
        Point3D  chamberOrigin = new Point3D(39.5, 0, this.getTargetZOffset()-100);
        Vector3D chamberAxis   = new Vector3D(0,0,1);
        Arc3D chamberBase = new Arc3D(chamberOrigin, chamberCenter, chamberAxis, 2*Math.PI);
        Cylindrical3D chamber = new Cylindrical3D(chamberBase, 200);
        scatteringChamberSurface = new Surface(chamber, new Strip(0, 0, 0), Constants.DEFAULTSWIMACC);
        scatteringChamberSurface.addMaterial(CRYOTARGETRHOACELL);
        scatteringChamberSurface.passive=true;
        
        Point3D  shieldCenter = new Point3D(0,           0, this.getTargetZOffset()+TSHIELDZPOS-TSHIELDLENGTH/2);
        Point3D  shieldOrigin = new Point3D(TSHIELDRMAX, 0, this.getTargetZOffset()+TSHIELDZPOS-TSHIELDLENGTH/2);
        Vector3D shieldAxis   = new Vector3D(0,0,1);
        Arc3D shieldBase = new Arc3D(shieldOrigin, shieldCenter, shieldAxis, 2*Math.PI);
        Cylindrical3D shieldCylinder = new Cylindrical3D(shieldBase, TSHIELDLENGTH);
        targetShieldSurface = new Surface(shieldCylinder, new Strip(0, 0, 0), Constants.DEFAULTSWIMACC);
        targetShieldSurface.addMaterial("TungstenShield",
                                  TSHIELDRMAX-TSHIELDRMIN,
                                  TSHIELDRHO,
                                  TSHIELDZOVERA,
                                  TSHIELDRADLEN,
                                  TSHIELDI,
                                  Units.MM);
        targetShieldSurface.passive=true;
    }

    /**
     * Creates target surfaces reading the /geometry/materials/target table from CCDB
     *  Here are the rules:
     *  - surfaces are identified by layer and should be defined for layer = 1, 2, and 3
     *  - multiple sublayers, identified by component, can be added with different materials
     *  - layer=1 component=1 is the target material and should always be present
     *  - the target material is assumed to be a full cylinder (rmin=0) while the other surfaces are "tubes"
     *  - for layer 1, the target material volume is created and any additional sublayer is assumed to be a equivalent to the target cell walls
     *  - for layer 2 and 3, when multiple sublayers are defined, the surface radius and length are set to the largest values among the sublayers
    */
    private void loadTarget(ConstantProvider provider) {
        for(int i=0; i<provider.length("/geometry/materials/target/layer"); i++)  {
            int sector    = provider.getInteger("/geometry/materials/target/sector", i);
            int layer     = provider.getInteger("/geometry/materials/target/layer", i);
            int component = provider.getInteger("/geometry/materials/target/component", i);
            if(layer>0 && layer<4) {
                if(!targetSurfaces.containsKey(layer))
                    targetSurfaces.put(layer, new ArrayList<>());
                double rmin      = provider.getDouble("/geometry/materials/target/rmin", i)*10;
                double rmax      = provider.getDouble("/geometry/materials/target/rmax", i)*10;
                double length    = provider.getDouble("/geometry/materials/target/length", i)*10;
                Point3D  center = new Point3D(0, 0, this.getTargetZOffset()-length/2);
                Vector3D axis   = new Vector3D(0,0,1);
                Surface surface = null;
                if(layer==1 && component==1 && rmin==0) { // this is the target cell, assume the volume is a bulk cylinder
                    Point3D       origin   = new Point3D(rmax, 0, this.getTargetZOffset()-length/2);
                    Arc3D         arc      = new Arc3D(origin, center, axis, 2*Math.PI);
                    Cylindrical3D cylinder = new Cylindrical3D(arc, length);
                    surface = new Surface(center, cylinder.highArc().center(), Constants.DEFAULTSWIMACC);
                    surface.lineVolume = cylinder;
                }
                else {                                    // use min radius for other surfaces
                    Point3D       origin   = new Point3D(rmin, 0, this.getTargetZOffset()-length/2);
                    Arc3D         arc      = new Arc3D(origin, center, axis, 2*Math.PI);
                    Cylindrical3D cylinder = new Cylindrical3D(arc, length);
                    surface = new Surface(cylinder, new Strip(0, 0, 0), Constants.DEFAULTSWIMACC);
                }
                surface.addMaterial("L"+layer+"C"+component,
                                  provider.getDouble("/geometry/materials/target/thickness", i)*10,
                                  provider.getDouble("/geometry/materials/target/density", i)/1000,
                                  provider.getDouble("/geometry/materials/target/ZoverA", i),
                                  provider.getDouble("/geometry/materials/target/X0", i)*10,
                                  provider.getDouble("/geometry/materials/target/I", i),
                                  Units.MM);
                surface.setIndex(component);
                surface.passive=true;
                targetSurfaces.get(layer).add(surface);
            }
            else if(layer!=0) {
                LOGGER.log(Level.WARNING, "Invalid target layer = " + layer + ": will be ignored");
            }
        }
        // reorder surfaces
        for(int layer : targetSurfaces.keySet()) {
            List<Surface> surfaces = targetSurfaces.get(layer);
            Collections.sort(surfaces);
        }
        // check consistency
        double r=0;
        for(int il=0; il<3; il++) {
            int layer = il+1;
            if(!targetSurfaces.containsKey(layer)) {
                LOGGER.log(Level.SEVERE, "Target layer " + layer + " is undefined");
                System.exit(1);
            }
            else if(layer==1 && targetSurfaces.get(layer).get(0).getIndex()!=1) {
                LOGGER.log(Level.SEVERE, "Target cell is undefined");
                System.exit(1);
            }
            else if(layer==1 && targetSurfaces.get(layer).get(0).lineVolume!=null && targetSurfaces.get(layer).get(0).getMaterials().size()>1) {
                LOGGER.log(Level.SEVERE, "Handling of multiple materials with bulk target not implemented yet");
                System.exit(1);
            }
            else {
                for(Surface s : targetSurfaces.get(layer)) {
                    if(s.getRadius()<r) {
                        LOGGER.log(Level.SEVERE, "Radius of target surface for layer=" + layer + " component=" + s.getIndex() + " is smaller than inner surface");
                        System.exit(1);
                    }
                    else {
                        r = s.getRadius();
                    }
                }
            }
        }
        targetCellSurface = this.getTargetSurface(1);
        scatteringChamberSurface = this.getTargetSurface(2);
        targetShieldSurface = this.getTargetSurface(3);
        targetRadius = this.targetCellSurface.getRadius();
        targetMaterials = this.getTargetCellSurface().getMaterials();
    }

    private Surface getTargetSurface(int layer) {
        Surface surface = null;
        // target cell
        if(layer==1) {
            surface = targetSurfaces.get(1).get(0);
            for(int is=1; is<this.targetSurfaces.get(1).size(); is++) {
                for(Material m : this.targetSurfaces.get(1).get(is).getMaterials())
                    surface.addMaterial(m);
            }
        }
        // for other layers calculate cylinder dimensions based on largest dimensions
        else {
            double radius = 0;
            double length = 0;
            for(Surface s : this.targetSurfaces.get(layer)) {
                if(radius<s.getRadius()) radius = s.getRadius();
                if(length<s.getLength()) length = s.getLength();
            }
            Point3D  center = new Point3D(0, 0, this.getTargetZOffset()-length/2);
            Point3D  origin = new Point3D(radius, 0, this.getTargetZOffset()-length/2);
            Vector3D axis   = new Vector3D(0,0,1);
            Arc3D arc = new Arc3D(origin, center, axis, 2*Math.PI);
            Cylindrical3D cylinder = new Cylindrical3D(arc, length);
            surface = new Surface(cylinder, new Strip(0, 0, 0), Constants.DEFAULTSWIMACC);
            for(Surface s : this.targetSurfaces.get(layer)) {
                for(Material m : s.getMaterials())
                    surface.addMaterial(m);
            }
        }
        surface.setIndex(0);
        surface.passive=true;
        return surface;
    }

    public double getTargetZOffset() {
        return targetPosition;
    }

    public double getTargetHalfLength() {
        return targetHalfLength;
    }

    public double getTargetRadius() {
        return targetRadius;
    }
    
    public List<Material> getTargetMaterials() {
        return targetMaterials;
    }

    public Surface getTargetCellSurface() {
        return this.targetCellSurface;
    }
    
    public Surface getScatteringChamber() {
        return scatteringChamberSurface;
    }
    
    public Surface getTargetShield() {
        return targetShieldSurface;
    }
    
    public SVTGeometry getSVT() {
        return svtGeometry;
    }
    

    public BMTGeometry getBMT() {
        return bmtGeometry;
    }

    public CTOFGeant4Factory getCTOF() {
        return ctofGeometry;
    }

    public Detector getCND() {
        return cndGeometry;
    }

//    public List<Surface> getCVTSurfaces() {
//        return cvtSurfaces;
//    }

    public List<Surface> geOuterSurfaces() {
        return outerSurfaces;
    }

    /**
     * Get the list of SVT strips for geometry defined by the selected run and variation
     * @param run
     * @param region
     * @param variation
     * @param lorentz
     * @return list of strips as Line3D
     */
    public static List<Line3D> getSVT(int run, int region, String variation, IndexedTable lorentz) {
        
        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        SVTStripFactory svtFac = new SVTStripFactory(cp, true);
        SVTGeometry geometry  = new SVTGeometry(svtFac, lorentz);

        int lmin= 0;
        int lmax = SVTGeometry.NLAYERS;
        if(region>0) {
            lmin = (region-1)*2;
            lmax = region*2;
        }
        List<Line3D> svt = new ArrayList<>();        
        for(int il=lmin; il<lmax; il++) {
            for(int is=0; is<SVTGeometry.NSECTORS[il]; is++) {
                for(int ic=0; ic<SVTGeometry.NSTRIPS; ic++) {
                    Line3D strip = geometry.getStrip(il+1, is+1, ic+1);
                    if(strip.origin().z()>strip.end().z())
                        svt.add(new Line3D(strip.end(), strip.origin()));
                    else
                        svt.add(strip);
                }
            }
        }
        return svt;
    }
    
    /**
     * Get the list of BMT strips for the selected BMT tile type and the 
     * geometry defined by the selected run and variation
     * @param type
     * @param run
     * @param variation
     * @param voltage
     * @return list of strips as Line3D, corresponding to the actual strip for 
     * Z tiles and for lines connecting the upstream and downstream circular 
     * edges of C tiles at the same local phi
     */
    public static List<Line3D> getBMT(BMTType type, int run, String variation, IndexedTable voltage) {
        
        List<Line3D> bmt = new ArrayList<>();
        
        CCDBConstantsLoader.Load(new DatabaseConstantProvider(run, variation));
        BMTGeometry bmtGeometry  = new BMTGeometry(voltage);
        
        for(int ir=0; ir<BMTGeometry.NLAYERS/2; ir++) {
            int layer = bmtGeometry.getLayer(ir+1, type);
            for(int is=0; is<BMTGeometry.NSECTORS; is++) {
                if(type==BMTType.Z) {
                    for(int ic=0; ic<bmtGeometry.getNStrips(layer); ic++) {
                        bmt.add(bmtGeometry.getZstrip(ir+1, is+1, ic+1));
                    }
                }
                else {
                    Arc3D origin = bmtGeometry.getCstrip(ir+1, is+1, 1);
                    Arc3D end    = bmtGeometry.getCstrip(ir+1, is+1, bmtGeometry.getNStrips(layer));
                    for(int ic=0; ic<1000; ic++) {
                        double t = (double) ic*origin.theta()/1000;
                        bmt.add(new Line3D(origin.point(t), end.point(t)));
                    }                    
                }
            }
        }
        return bmt;
    }
    
    /**
     * Computes the average offset of the origins of the two strip lists
     * @param geo1
     * @param geo2
     * @return the offset (1-2) as a Point3D
     */
    public static Point3D getOffset(List<Line3D> geo1, List<Line3D> geo2) {
        Point3D center = new Point3D(0,0,0);
        if(geo1.size()!=geo2.size())
            return center;
        for(int i=0; i<geo1.size(); i++) {
            Point3D origin1 = geo1.get(i).origin();
            Point3D origin2 = geo2.get(i).origin();
            center.translateXYZ((origin1.x()-origin2.x())/geo1.size(), (origin1.y()-origin2.y())/geo1.size(), (origin1.z()-origin2.z())/geo1.size());
        }
        return center;
    }
    
    /**
     * Draws representative plots of the differences between the two lists of 
     * strips, compensating for a global offset
     * @param geo1
     * @param geo2
     * @param offset applied to the second geometry
     * @return a DataGroup with the relevant plots
     */
    public static DataGroup compareStrips(List<Line3D> geo1, List<Line3D> geo2, Point3D offset) {
        DataGroup dg = new DataGroup(4,2); 
        String[] xyz = {"x", "y", "z", "xy1", "xy2"};
        String[] oe = {"upstream", "downstream"};
        for(int coord=0; coord<xyz.length; coord++) {
            for(int side=0; side<oe.length; side++) {
                GraphErrors delta = new GraphErrors(oe[side]+xyz[coord]);
                delta.setTitleX("#phi (rad)");
                delta.setTitleY(oe[side] + " #Delta" + xyz[coord] + " (mm)");
                delta.setMarkerSize(2);
                delta.setMarkerColor(4);
                if(coord>2) {
                    delta.setMarkerColor(coord-2);
                    delta.setTitleX(oe[side] + " x (mm)");
                    delta.setTitleY(oe[side] + " y (mm)");
                }
                dg.addDataSet(delta, Math.min(3, coord)+side*4);
            }
        }
        for(int i=0; i<Math.min(geo1.size(), geo2.size()); i++) {
            Line3D strip1 = geo1.get(i);
            Line3D strip2 = geo2.get(i);
            strip2.translateXYZ(offset.x(), offset.y(), offset.z());
            dg.getGraph(oe[0]+xyz[0]).addPoint(strip1.origin().toVector3D().phi(), strip1.origin().x()-strip2.origin().x(), 0, 0);
            dg.getGraph(oe[0]+xyz[1]).addPoint(strip1.origin().toVector3D().phi(), strip1.origin().y()-strip2.origin().y(), 0, 0);
            dg.getGraph(oe[0]+xyz[2]).addPoint(strip1.origin().toVector3D().phi(), strip1.origin().z()-strip2.origin().z(), 0, 0);
            dg.getGraph(oe[0]+xyz[3]).addPoint(strip1.origin().x(), strip1.origin().y(), 0, 0);
            dg.getGraph(oe[0]+xyz[4]).addPoint(strip2.origin().x(), strip2.origin().y(), 0, 0);
            dg.getGraph(oe[1]+xyz[0]).addPoint(strip1.end().toVector3D().phi(), strip1.end().x()-strip2.end().x(), 0, 0);
            dg.getGraph(oe[1]+xyz[1]).addPoint(strip1.end().toVector3D().phi(), strip1.end().y()-strip2.end().y(), 0, 0);
            dg.getGraph(oe[1]+xyz[2]).addPoint(strip1.end().toVector3D().phi(), strip1.end().z()-strip2.end().z(), 0, 0);
            dg.getGraph(oe[1]+xyz[3]).addPoint(strip1.end().x(), strip1.end().y(), 0, 0);
            dg.getGraph(oe[1]+xyz[4]).addPoint(strip2.end().x(), strip2.end().y(), 0, 0);
        }
        return dg;
    }

    /**
     * Compares the CVT geometry for two geometry variations selected from 
     * command line, with the option of compensating for the average offset 
     * between the SVT-R1 strip upstream endpoints
     * @param args
     */
    public static void main(String[] args) {
        DefaultLogger.debug();
        OptionParser parser = new OptionParser("Compare CVT geometries from two variations");
        parser.setRequiresInputList(false);
        parser.addOption("-var1",   "default",        "geometry variation 1");
        parser.addOption("-var2",   "rgb_spring2019", "geometry variation 2");
        parser.addOption("-offset", "0",              "compensate for the average offset of SVT-R1 strip upstream end (1)");
        parser.parse(args);
        
        int run = 11;
        String var1 = parser.getOption("-var1").stringValue();
        String var2 = parser.getOption("-var2").stringValue();
        boolean compensateOffset = parser.getOption("-offset").intValue()==1;
        
        ConstantsManager ccdb = new ConstantsManager();
        ccdb.init(Arrays.asList("/calibration/svt/lorentz_angle", "/calibration/mvt/bmt_voltage"));
        ccdb.setVariation(var1);
        IndexedTable svtLorentz = ccdb.getConstants(run, "/calibration/svt/lorentz_angle");
        IndexedTable bmtVoltage = ccdb.getConstants(run, "/calibration/mvt/bmt_voltage");
        
        GStyle.getH1FAttributes().setOptStat("1111");
        GStyle.getAxisAttributesX().setTitleFontSize(24);
        GStyle.getAxisAttributesX().setLabelFontSize(18);
        GStyle.getAxisAttributesY().setTitleFontSize(24);
        GStyle.getAxisAttributesY().setLabelFontSize(18);
        GStyle.getAxisAttributesZ().setLabelFontSize(14);
        GStyle.getAxisAttributesX().setLabelFontName("Arial");
        GStyle.getAxisAttributesY().setLabelFontName("Arial");
        GStyle.getAxisAttributesZ().setLabelFontName("Arial");
        GStyle.getAxisAttributesX().setTitleFontName("Arial");
        GStyle.getAxisAttributesY().setTitleFontName("Arial");
        GStyle.getAxisAttributesZ().setTitleFontName("Arial");
        GStyle.setGraphicsFrameLineWidth(1);
        GStyle.getH1FAttributes().setLineWidth(2);

        EmbeddedCanvasTabbed canvas = new EmbeddedCanvasTabbed("SVT-R1", "SVT-R2", "SVT-R3", "BMT-C", "BMT-Z");
        Point3D offset = new Point3D(0,0,0);
        for(int i=0; i<SVTGeometry.NREGIONS; i++) {
            List<Line3D> svt1 = Geometry.getSVT(run, i+1, var1, svtLorentz);
            List<Line3D> svt2 = Geometry.getSVT(run, i+1, var2, svtLorentz);
            if(i==0 && compensateOffset) offset = Geometry.getOffset(svt1, svt2);
            canvas.getCanvas("SVT-R" + (i+1)).draw(Geometry.compareStrips(svt1, svt2, offset));
        }
        canvas.getCanvas("BMT-Z").draw(Geometry.compareStrips(Geometry.getBMT(BMTType.Z, run, var1, bmtVoltage), Geometry.getBMT(BMTType.Z, run, var2, bmtVoltage), offset));
        canvas.getCanvas("BMT-C").draw(Geometry.compareStrips(Geometry.getBMT(BMTType.C, run, var1, bmtVoltage), Geometry.getBMT(BMTType.C, run, var2, bmtVoltage), offset));
        
        JFrame frame = new JFrame("CVT Geometry");
        frame.setSize(1500,800);
        frame.add(canvas);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);   
    }
}
