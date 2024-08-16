/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.service.mltn;

import j4np.hipo5.data.Bank;
import j4np.hipo5.data.CompositeNode;
import j4np.hipo5.data.Event;
import j4np.hipo5.data.Schema;
import j4np.hipo5.data.SchemaFactory;
import j4np.instarec.core.TrackFinderNetwork;
import j4np.instarec.core.Tracks;
import java.util.Optional;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import org.jlab.utils.CLASResources;

/**
 *
 * @author gavalian
 */
public class MLIREngine extends ReconstructionEngine {

    private String         networkFlavor = "default";
    private Integer           networkRun = 5038;
    private String       inputBankPrefix = "";
    private String      outputBankPrefix = "ai";
    private String             inputBank = null;
    private String            outputBank = null;
    private Schema            schemaHBCL = null;
    private SchemaFactory        factory = new SchemaFactory();   
    TrackFinderNetwork       trackFinder = new TrackFinderNetwork();
    private org.jlab.jnp.hipo4.data.SchemaFactory       factory4 = new org.jlab.jnp.hipo4.data.SchemaFactory();
    
    public MLIREngine(){
        super("MLIR","gavalian","0.273");
    }
    
    @Override
    public boolean init() {
        
        //Set bank names
        inputBankPrefix  = Optional.ofNullable(this.getEngineConfigString("inputBankPrefix")).orElse("");
        outputBankPrefix = Optional.ofNullable(this.getEngineConfigString("outputBankPrefix")).orElse("ai");
        inputBank  = "HitBasedTrkg::"+inputBankPrefix+"Clusters";
        outputBank = outputBankPrefix+"::tracks";
        
        networkFlavor = Optional.ofNullable(this.getEngineConfigString("flavor")).orElse("default");
        String runNumber = Optional.ofNullable(this.getEngineConfigString("run")).orElse("2");
        networkRun = Integer.parseInt(runNumber);
        
        String path = CLASResources.getResourcePath("etc/ejml/clas12default.network"); 
        if(this.getEngineConfigString("network")!=null) 
            path = this.getEngineConfigString("network");
        System.out.println("[neural-network] info : Loading neural network from " + path);
        
        
        
        
        String dictDir = CLASResources.getResourcePath("etc/bankdefs/hipo4");
        factory.initFromDirectory(dictDir);
        factory4.initFromDirectory(dictDir);
        
        //trackFinder.init(factory);
        trackFinder.init(path, networkRun);
        
        schemaHBCL = factory.getSchema("HitBasedTrkg::Clusters");
        
        //----- This will find in the archive the last run number closest
        //----- to provided run number that contains trained network.
        //----- it works similar to CCDB, but not exatly, for provided 
        //----- run number it looks for run that has smaller number,
        //----- however it the provided run # it lower than anything 
        //----- existing in the arhive, it will return the closest run 
        //----- number entry.
      
        return true;
    }

    @Override
    public boolean processDataEvent(DataEvent de) {
        
        if(de.hasBank(inputBank)==true){
                        
            int capacity = de.getEventBuffer().capacity();
            
            System.out.println("capacity = " + capacity);
            
            Event event = new Event(capacity+1024);
            event.initFrom(de.getEventBuffer().array());
            //event.scanShow();
            
            int size = event.scanLength(schemaHBCL.getGroup(), schemaHBCL.getItem());
            int length = schemaHBCL.getEntryLength();
            
            Bank b = new Bank(schemaHBCL,size/length+2);
            
            event.read(b);
            
            //b.show();
            
            Tracks recTracks = trackFinder.processBank(b);
            DataBank bank = de.createBank("instarec::tracks", recTracks.getRows());
            convert(recTracks.dataNode(),bank);
            de.appendBank(bank);
            //recTracks.show();
            //System.out.println(" row size = " + length + " buffer size = "+ size);
            
            //trackFinder.processEvent(event);
            //bank.show();
            //( (HipoDataEvent) de).getHipoEvent().initFrom(event.getEventBuffer().array(),event.bufferLength());
        }
        return true;
    }
    
    public void convert(CompositeNode n, DataBank b){
        int nentries = n.getEntries();        
        int    nrows = n.getRows();
        String[] names = b.getDescriptor().getEntryList();
        for(int row = 0; row < nrows; row++){
            for(int entry = 0; entry < nentries; entry++){
                int type = n.getEntryType(entry);
                
                switch(type){
                    case 1: b.setByte(names[entry], row,  (byte) n.getInt(entry, row) ); break;
                    case 2: b.setShort(names[entry], row, n.getShort(entry, row) ); break;
                    case 3: b.setInt(names[entry], row, n.getInt(entry, row) ); break;
                    case 4: b.setFloat(names[entry], row, (float) n.getDouble(entry, row) ); break;
                    default: break;
                }
            }
        }
    }
}
