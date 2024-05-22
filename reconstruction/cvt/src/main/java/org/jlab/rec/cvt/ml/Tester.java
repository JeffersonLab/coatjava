/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import cnuphys.magfield.MagneticFields;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.swimtools.MagFieldsEngine;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.CLASResources;
import org.jlab.utils.system.ClasUtilsFile;

/**
 *
 * @author ziegler
 */
public class Tester {
    public static void main(String[] args) {
        System.setProperty("CLAS12DIR", "/Users/ziegler/BASE/Tracking/CVT-Issues/AI/coatjava/coatjava");
        String mapDir = CLASResources.getResourcePath("etc")+"/data/magfield";
        System.out.println(mapDir);
        try {
            MagneticFields.getInstance().initializeMagneticFields(mapDir,
                    "Symm_torus_r2501_phi16_z251_24Apr2018.dat","Symm_solenoid_r601_phi1_z1201_13June2018.dat");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        String var = "fall2018_bg";
        String dir = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
        //SchemaFactory schemaFactory = new SchemaFactory();
        //schemaFactory.initFromDirectory(dir);
        MagFieldsEngine enmf = new MagFieldsEngine();
        enmf.setVariation(var); 
        enmf.init();
       
        CVTInitializer eni = new CVTInitializer();
        eni.init();
         
        CVTClustering enc = new CVTClustering();
        enc.setVariation(var);
        enc.init();
        
        ConvResolver enr = new ConvResolver();
        enr.setVariation(var);
        enr.init();
        
        MLTracking ent = new MLTracking();
        ent.setVariation(var);
        ent.init();
        
        SchemaFactory schemaFactory = new SchemaFactory();
        schemaFactory.initFromDirectory(dir);
        if(schemaFactory.hasSchema("cvtml::clusters")) {
            System.out.println("cvtml::clusters BANK FOUND........");
        } else {
            System.out.println("cvtml::clusters BANK NOT FOUND........");
        }
        if(schemaFactory.hasSchema("cvtml::hits")) {
            System.out.println("cvtml::hits BANK FOUND........");
        } else {
            System.out.println("cvtml::hits BANK NOT FOUND........");
        }
        HipoDataSync writer = new HipoDataSync(schemaFactory);
        writer.setCompressionType(2);
        String outputFileName = "/Users/ziegler/BASE/Files/CVTDEBUG/AI/MLSample_test1.hipo";
        checkFile(outputFileName);
        writer.open(outputFileName);
        long t1 = 0;
        List<String> inputList = new ArrayList<>();
        inputList.add("/Users/ziegler/BASE/Files/CVTDEBUG/AI/skim1_rgbbg50na.hipo");
        
        int counter = 0;
        for(String inputFile :  inputList) {
            HipoDataSource reader = new HipoDataSource();
            reader.open(inputFile);
            reader.getReader().getSchemaFactory().addSchema(schemaFactory.getSchema("cvtml::hits"));
            reader.getReader().getSchemaFactory().addSchema(schemaFactory.getSchema("cvtml::clusters"));
            while (reader.hasEvent() && counter<1001) {

                counter++;
                DataEvent event = reader.getNextEvent();
                if (counter > 0) {
                    t1 = System.currentTimeMillis();
                }
                enmf.processDataEvent(event);
                enc.processDataEvent(event);
                enr.processDataEvent(event);
                ent.processDataEvent(event);
                writer.writeEvent(event);
                if(counter%1000==0) 
                    System.out.println("PROCESSED "+counter+" EVENTS ");
                
            }
            
            double t = System.currentTimeMillis() - t1;
            System.out.println(t1 + " TOTAL  PROCESSING TIME = " + (t / (float) counter));
        }
        writer.close();

    }

    private static void checkFile(String testCalOutPuthipo) {
        File file = new File(testCalOutPuthipo);
        if (file.exists()) {
            // Delete the file
            if (file.delete()) {
                System.out.println("File deleted successfully.");
            } else {
                System.out.println("Failed to delete the file.");
            }
        } else {
            System.out.println("File does not exist.");
        }
        
    }
}

