package org.jlab.clas.reco;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.clara.engine.EngineData;
import org.jlab.clara.engine.EngineDataType;
import java.util.Arrays;
import org.jlab.coda.jevio.EvioException;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.json.JSONObject;
import org.jlab.utils.ClaraYaml;

/**
 *
 * @author gavalian, kenjo, baltzell
 */
public class EngineProcessor {

    public static final String ENGINE_CLASS_BG = "org.jlab.service.bg.BackgroundEngine";
    public static final String ENGINE_CLASS_PP = "org.jlab.service.postproc.PostprocEngine";
    
    private final Map<String,ReconstructionEngine>  processorEngines = new LinkedHashMap<>();
    private static final Logger LOGGER = Logger.getLogger(EngineProcessor.class.getPackage().getName());
    private SchemaFactory banksToKeep = null;
    private final List<String> schemaExempt = Arrays.asList("RUN::config","DC::tdc");

    protected boolean updateDictionary = true;
    
    protected CLASDecoder4 decoder = new CLASDecoder4();

    int eventsRead = 0;
    
    public EngineProcessor(){}

    public EngineProcessor(OptionParser parser) {
        init(parser);
    }

    private ReconstructionEngine findEngine(String clazz) {
        for (String k : processorEngines.keySet()) {
            if (processorEngines.get(k).getClass().getName().equals(clazz)) {
                return processorEngines.get(k);
            }
        }
        return null;
    }

    protected void parseYaml(String filename) {
        ClaraYaml yaml = new ClaraYaml(filename);
        if (yaml.schemaDirectory() != null) {
            setBanksToKeep(yaml.schemaDirectory());
        }
        for (JSONObject service : yaml.services()) {
            JSONObject cfg = yaml.filter(service.getString("name"));
            if (cfg.length() > 0) {
                addEngine(service.getString("name"),service.getString("class"),cfg.toString());
            } else {
                addEngine(service.getString("name"),service.getString("class"));
            }
        }
    }

    protected void setBackgroundFiles(String filenames) {
        if (findEngine(ENGINE_CLASS_BG) == null) {
            LOGGER.info("Adding BackgroundEngine for -B option.");
            addEngine("BG",ENGINE_CLASS_BG);
        }
        findEngine(ENGINE_CLASS_BG).engineConfigMap.put("filename", filenames);
        findEngine(ENGINE_CLASS_BG).init();
    }

    protected void setPreloadFiles(String filenames, boolean restream, boolean rebuild) {
        if (findEngine(ENGINE_CLASS_PP) == null) {
            LOGGER.info("Adding PostprocEngine for -P option.");
            addEngine("BG",ENGINE_CLASS_PP);
        }
        findEngine(ENGINE_CLASS_PP).engineConfigMap.put("preloadFile", filenames);
        findEngine(ENGINE_CLASS_PP).engineConfigMap.put("restream", String.valueOf(restream));
        findEngine(ENGINE_CLASS_PP).engineConfigMap.put("rebuild", String.valueOf(rebuild));
        findEngine(ENGINE_CLASS_PP).init();
    }

    protected void updateDictionary(HipoDataSource source, HipoDataSync sync){
        SchemaFactory fsync = sync.getWriter().getSchemaFactory();
        SchemaFactory fsrc  = source.getReader().getSchemaFactory();
        List<String> schemaList = fsync.getSchemaKeys();
        for(int s = 0; s < schemaList.size(); s++){
            if(schemaExempt.contains(schemaList.get(s))==false){
                fsrc.remove(schemaList.get(s));
                fsrc.addSchema(fsync.getSchema(schemaList.get(s)).getCopy());
            } else {
                LOGGER.log(Level.INFO, "[dictrionary-update] schema {0} is not being updated\n", 
                        schemaList.get(s));
            }
        }
    }

    protected void setBanksToKeep(String schemaDirectory) {
        if (!Files.isDirectory((new File(schemaDirectory)).toPath())) {
            LOGGER.log(Level.SEVERE, "Invalid schema directory, aborting:  "+schemaDirectory);
            System.exit(1);
        }
        LOGGER.log(Level.INFO, "Using schema directory:  "+schemaDirectory);
        banksToKeep = new SchemaFactory();
        banksToKeep.initFromDirectory(schemaDirectory);
    }

    private void removeBanks(DataEvent event) {
        if (banksToKeep != null) {
            for (String bankName : event.getBankList()) {
                if (!banksToKeep.hasSchema(bankName)) {
                    event.removeBank(bankName);
                }
            }
        }
    }

    public void initDefault(){

        String[] names = new String[]{
            "DECO","MAGFIELDS",
            "DCCR","DCHB","FTOFHB","EC","HTCC","EBHB",
            "DCTB","FTOFTB","EBTB","VTX"
        };

        String[] services = new String[]{
            "org.jlab.clas.reco.DecoderEngine",
            "org.jlab.clas.swimtools.MagFieldsEngine",
            "org.jlab.service.dc.DCHBClustering",
            "org.jlab.service.dc.DCHBPostClusterConv",
            "org.jlab.service.ftof.FTOFHBEngine",
            "org.jlab.service.ec.ECEngine",
            "org.jlab.service.htcc.HTCCReconstructionService",
            "org.jlab.service.eb.EBHBEngine",
            "org.jlab.service.dc.DCTBEngine",
            "org.jlab.service.ftof.FTOFTBEngine",
            "org.jlab.service.eb.EBTBEngine",
            "org.jlab.rec.service.vtx.VTXEngine"
        };

        for(int i = 0; i < names.length; i++){
            this.addEngine(names[i], services[i]);
        }
    }
    public void initAll(){

        String[] names = new String[]{
            "DECO","MAGFIELDS",
            "FTCAL", "FTHODO", "FTTRK", "FTEB",
            "URWT", "DCCR", "DCHB","FTOFHB","EC","RASTER",
            "CVTFP","CTOF","CND","BAND",
            "HTCC","LTCC","EBHB",
            "DCTB","FMT","FTOFTB","CVT","EBTB",
            "RICHEB","RTPC","AHDC","ATOF","ALERT", "MC","VTX"
        };

        String[] services = new String[]{
            "org.jlab.clas.reco.DecoderEngine",
            "org.jlab.clas.swimtools.MagFieldsEngine",
            "org.jlab.rec.ft.cal.FTCALEngine",
            "org.jlab.rec.ft.hodo.FTHODOEngine",
            "org.jlab.rec.ft.trk.FTTRKEngine",
            "org.jlab.rec.ft.FTEBEngine",
            "org.jlab.service.urwt.URWTEngine",
            "org.jlab.service.dc.DCHBClustering",
            "org.jlab.service.dc.DCHBPostClusterConv",
            "org.jlab.service.ftof.FTOFHBEngine",
            "org.jlab.service.ec.ECEngine",
            "org.jlab.service.raster.RasterEngine",
            "org.jlab.rec.cvt.services.CVTEngine",
            "org.jlab.service.ctof.CTOFEngine",
            "org.jlab.service.cnd.CNDCalibrationEngine",
            "org.jlab.service.band.BANDEngine",
            "org.jlab.service.htcc.HTCCReconstructionService",
            "org.jlab.service.ltcc.LTCCEngine",
            "org.jlab.service.eb.EBHBEngine",
            "org.jlab.service.dc.DCTBEngine",
            "org.jlab.service.fmt.FMTEngine",
            "org.jlab.service.ftof.FTOFTBEngine",
            "org.jlab.rec.cvt.services.CVTSecondPassEngine",
            "org.jlab.service.eb.EBTBEngine",
            "org.jlab.rec.rich.RICHEBEngine",
            "org.jlab.service.rtpc.RTPCEngine",
            "org.jlab.service.ahdc.AHDCEngine",
            "org.jlab.service.atof.ATOFEngine",
            "org.jlab.service.alert.ALERTEngine",
            "org.jlab.service.mc.TruthMatch",
            "org.jlab.rec.service.vtx.VTXEngine"
        };
        if(names.length!=services.length)
            LOGGER.log(Level.SEVERE, "initAll : the list of services does not match the list of service names...  ");
        for(int i = 0; i < names.length; i++){
            this.addEngine(names[i], services[i]);
        }
    }

     public void initCaloDebug(){

        String[] names = new String[]{
            "EC","EB"
        };

        String[] services = new String[]{
            "org.jlab.service.ec.ECEngine",
            "org.jlab.service.eb.EBEngine",
        };

        for(int i = 0; i < names.length; i++){
            this.addEngine(names[i], services[i]);
        }
    }

    /**
     * add a reconstruction engine to the chain
     * @param name name of the engine in the chain
     * @param engine engine class
     */
    public void addEngine(String name, ReconstructionEngine engine){
        engine.init();
        this.processorEngines.put(name, engine);
    }

    /**
     * Adding engine to the map the order of the services matters, since they will
     * be executed in order added.
     * @param name name for the service
     * @param clazz class name including the package name
     * @param jsonConf string in json format with engine configuration
     */
    public void addEngine(String name, String clazz, String jsonConf) {
        Class c;
        try {
            c = Class.forName(clazz);
            if( ReconstructionEngine.class.isAssignableFrom(c)==true){
                ReconstructionEngine engine = (ReconstructionEngine) c.newInstance();
                if(jsonConf != null && !jsonConf.equals("null")) {
                    EngineData input = new EngineData();
                    input.setData(EngineDataType.JSON.mimeType(), jsonConf);
                    engine.configure(input);
                }
                else {
                    engine.init();
                }
                this.processorEngines.put(name == null ? engine.getName() : name, engine);
            } else {
                LOGGER.log(Level.SEVERE, ">>>> ERROR: class is not a reconstruction engine : {0}", clazz);
            }

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Adding engine to the map the order of the services matters, since they will
     * be executed in order added.
     * @param name name for the service
     * @param clazz class name including the package name
     */
    public void addEngine(String name, String clazz) {
        this.addEngine(name, clazz, null);
    }

    /**
     * Add reconstruction engine to the chain
     * @param clazz Engine class.
     */
    public void addEngine(String clazz) {
        this.addEngine(null, clazz, null);
    }

    /**
     * Initialize all the engines in the chain.
     */
    public final void init(){
        System.out.println("\n\n\n ");
        for(Map.Entry<String,ReconstructionEngine> entry : this.processorEngines.entrySet()){
            System.out.println(String.format("   >>>>>> (*) initializing : %8s : %s",entry.getKey(),
                    entry.getValue().getClass().getName()));
            entry.getValue().init();
        }
        System.out.println("\n\n");
    }

    /**
     * process a single event through the chain.
     * @param event
     */
    public void processEvent(DataEvent event){
        for(Map.Entry<String,ReconstructionEngine> engine : this.processorEngines.entrySet()){
            try {
                engine.getValue().processDataEvent(event);
            } catch (Exception e){
                LOGGER.log(Level.SEVERE, "[Exception] >>>>> engine : {0}\n\n", engine.getKey());
                e.printStackTrace();
            }
        }
    }

    public void processFile(String file, String output){
        this.processFile(file, output, -1, -1);
    }

    public void processEvent(DataEvent event, HipoDataSync writer) {
        processEvent(event);
        removeBanks(event);
        writer.writeEvent(event);
    }

    public void processFile(HipoDataSource reader, HipoDataSync writer, int skipEvents, int maxEvents) {
        if (updateDictionary==true) updateDictionary(reader, writer);
        ProgressPrintout progress = new ProgressPrintout();
        eventsRead = 0;
        while (reader.hasEvent()) {
            DataEvent event = reader.getNextEvent();
            eventsRead++;
            if (skipEvents <= 0 || eventsRead > skipEvents) processEvent(event, writer);
            if (maxEvents > 0 && eventsRead > maxEvents+skipEvents) break;
            progress.updateStatus();
        }
        progress.showStatus();
    }

    public void processFile(EvioSource reader, HipoDataSync writer, int skipEvents, int maxEvents) {
        ProgressPrintout progress = new ProgressPrintout();
        eventsRead = 0;
        while (reader.hasEvent()) {
            eventsRead++;
            try {
                ByteBuffer bb = reader.getEventBuffer(eventsRead, true);
                if (skipEvents <= 0 || eventsRead > skipEvents) {
                    EvioDataEvent evio = new EvioDataEvent(bb.array(), ByteOrder.LITTLE_ENDIAN);
                    Event hipo = decoder.getDecodedEvent(evio, -1, eventsRead, null, null);
                    HipoDataEvent hipo2 = new HipoDataEvent(hipo, decoder.getSchemaFactory());
                    processEvent(hipo2, writer);
                }
                if (maxEvents > 0 && eventsRead > maxEvents+skipEvents) break;
            } catch (EvioException ex) {
                System.getLogger(EngineProcessor.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
            progress.updateStatus();
        }
        progress.showStatus();
    }

    /**}
     * process entire file through engine chain.
     * @param input input file name to process
     * @param output output filename
     * @param nskip number of events to skip
     * @param nevents number of events to process
     */
    public void processFile(String input, String output, int nskip, int nevents) {
        HipoDataSync writer = new HipoDataSync();
        writer.setCompressionType(2);
        if (input.endsWith(".hipo")  ||  input.endsWith(".h5") || input.endsWith(".h4")) {
            HipoDataSource reader = new HipoDataSource();
            reader.open(input);
            writer.open(output);
            processFile(reader, writer, nskip, nevents);
        } else {
            LOGGER.info(() -> "No HIPO file extension found, assuming this is an EVIO file:  "+input);
            EvioSource reader = new EvioSource();
            reader.open(input);
            writer.open(output);
            processFile(reader, writer, nskip, nevents);
        }
        writer.close();
    }

    /**
     * display services registered with the processor.
     */
    public void show(){
        System.out.println("----->>> EngineProcessor:");
        for(Map.Entry<String,ReconstructionEngine> entry : this.processorEngines.entrySet()){
            System.out.println(String.format("%-24s | %s", entry.getKey(),entry.getValue().getClass().getName()));
        }
    }

    protected final void init(int config) {
        if (config > 2) initCaloDebug();
        else if(config == 2) initAll();
        else initDefault();
    }

    protected static OptionParser parser() {
        OptionParser parser = new OptionParser("recon-util");
        parser.addRequired("-o","output.hipo");
        parser.addRequired("-i","input.evio/hipo");
        parser.addOption("-c","0","use default configuration [0 - no, 1 - yes/default, 2 - all services] ");
        parser.addOption("-s","-1","number of events to skip");
        parser.addOption("-n","-1","number of events to process");
        parser.addOption("-y","0","yaml file");
        parser.addOption("-u","true","update dictionary from writer ? ");
        parser.addOption("-S",null,"schema directory");
        parser.addOption("-B",null,"background file");
        parser.addOption("-P",null,"preload file for post-processing");
        parser.addOption("-R","0","rebuild scalers");
        parser.addOption("-H","0","restream helicity");
        parser.setRequiresInputList(false);
        return parser;
    }

    protected final void init(OptionParser parser) {
        parser.syncLogLevel(LOGGER);
        if (parser.getOption("-u").stringValue().contains("false"))
            updateDictionary = false;

        // read services and schema from YAML:
        if (!parser.getOption("-y").stringValue().equals("0")) {
            parseYaml(parser.getOption("-y").stringValue());
        }
        // builtin configuration:
        else if (parser.getOption("-c").intValue() > 0) {
            init(parser.getOption("-c").intValue());
        }
        // user-defined services:
        else {
            for(String engine : parser.getInputList()) {
                System.out.println("Adding reconstruction engine " + engine);
                addEngine(engine);
            }
        }

        // command-line schema overrides YAML:
        if (parser.getOption("-S").stringValue() != null)
            setBanksToKeep(parser.getOption("-S").stringValue());

        // command-line filename for background merging overrides YAML:
        if (parser.getOption("-B").stringValue() != null)
            setBackgroundFiles(parser.getOption("-B").stringValue());
        
        // command-line filename for post-processing overrides YAML:
        if (parser.getOption("-P").stringValue() != null) {
            setPreloadFiles(parser.getOption("-P").stringValue(),
                parser.getOption("-H").intValue()!=0,
                parser.getOption("-R").intValue()!=0);
        }
    }

    public static void main(String[] args) {
        
        OptionParser parser = EngineProcessor.parser();
        parser.parse(args);

        EngineProcessor proc = new EngineProcessor(parser);

        proc.processFile(parser.getOption("-i").stringValue(),
                         parser.getOption("-o").stringValue(),
                         parser.getOption("-s").intValue(),
                         parser.getOption("-n").intValue());
    }

}
