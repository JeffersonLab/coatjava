package org.jlab.detector.decode;

import java.util.List;
import java.util.TreeSet;

import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicityState;

import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;

import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

public class CLASDecoder4 extends CLASDecoder {

    public static void main(String[] args){

        OptionParser parser = CLASDecoder4U.getOptionParser();
        parser.addOption("-d", "0","debug mode, set >0 for more verbose output");
        parser.addOption("-m", "run","translation tables source (use -m devel for development tables)");
        parser.addOption("-b", "16","record buffer size in MB");
        parser.parse(args);
        List<String> inputList = parser.getInputList();

        if(inputList.isEmpty()==true){
            parser.printUsage();
            System.out.println("\n >>>> error : no input file is specified....\n");
            System.exit(1);
        }

        String modeDevel = parser.getOption("-m").stringValue();
        boolean developmentMode = false;

        if(modeDevel.compareTo("run")!=0&&modeDevel.compareTo("devel")!=0){
            parser.printUsage();
            System.out.println("\n >>>> error : mode has to be set to \"run\" or \"devel\" ");
            System.exit(1);
        }

        if(modeDevel.compareTo("devel")==0){
            developmentMode = true;
        }

        String outputFile = parser.getOption("-o").stringValue();
        int compression = parser.getOption("-c").intValue();
        int  recordsize = parser.getOption("-b").intValue();
        int debug = parser.getOption("-d").intValue();

        CLASDecoder decoder = new CLASDecoder(developmentMode);

        decoder.setDebugMode(debug);

        HipoWriterSorted writer = new HipoWriterSorted();
        writer.setCompressionType(compression);
        writer.getSchemaFactory().initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));

        Bank  rawRunConf  = new Bank(writer.getSchemaFactory().getSchema("RUN::config"));
        Bank  helicityAdc = new Bank(writer.getSchemaFactory().getSchema("HEL::adc"));

        int nrun = parser.getOption("-r").intValue();
        Double torus = parser.getOption("-t").getValue() == null ? null : parser.getOption("-t").doubleValue();
        Double solenoid = parser.getOption("-s").getValue() == null ? null : parser.getOption("-s").doubleValue();

        writer.open(outputFile);
        ProgressPrintout progress = new ProgressPrintout();
        System.out.println("INPUT LIST SIZE = " + inputList.size());
        int nevents = parser.getOption("-n").intValue();
        int counter = 0;

        if(nrun>0){
            decoder.setRunNumber(nrun,true);
        }

        if (parser.getOption("-x").getValue() != null)
            decoder.detectorDecoder.setTimestamp(parser.getOption("-x").stringValue());
        if (parser.getOption("-v").getValue() != null)
            decoder.detectorDecoder.setVariation(parser.getOption("-v").stringValue());

        // Store all helicity readings, ordered by timestamp:
        TreeSet<HelicityState> helicityReadings = new TreeSet<>();

        for(String inputFile : inputList){
            EvioSource reader = new EvioSource();
            reader.open(inputFile);
           
            while(reader.hasEvent()==true){
                EvioDataEvent event = (EvioDataEvent) reader.getNextEvent();
                
                Event  decodedEvent = decoder.getDecodedEvent(event, nrun, counter, torus, solenoid);
                
                decodedEvent.read(rawRunConf);
                decodedEvent.read(helicityAdc);

                helicityReadings.add(HelicityState.createFromFadcBank(helicityAdc, rawRunConf,
                    decoder.detectorDecoder.scalerManager));

                Event taggedEvent = decoder.createTaggedEvent(decodedEvent, "RAW::epics","RAW::scaler","RUN::scaler","HEL::scaler");
                if (!taggedEvent.isEmpty())
                    writer.addEvent(taggedEvent, 1);
                
                writer.addEvent(decodedEvent,0);
                
                counter++;
                progress.updateStatus();
                if(counter%25000==0){
                    System.gc();
                }
                if(nevents>0){
                    if(counter>=nevents) break;
                }
            }

        }

        // add the helicity flips into new tag-1 events:
        HelicitySequence.writeFlips(writer, helicityReadings);

        writer.close();
    }

}
