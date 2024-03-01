/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.reco;

import j4np.clas12.chain.EvioTools;
import j4np.utils.io.OptionParser;
import j4np.utils.io.OptionStore;

/**
 *
 * @author gavalian
 */
public class EngineStream {
 
    public static void main(String[] args){
        OptionStore store = new OptionStore("recons-stream");
        store.addCommand("-convert", "converts regular EVIO file to EVIO6 file");
        store.addCommand("-decode", "decodes EVIo events from evio6 file, to hipo5 -> hipo4");
        store.getOptionParser("-convert").addRequired("-o", "output file name");

        store.getOptionParser("-decode")
                .addRequired("-o", "output file name")
                .addOption("-t", "4", "number of threads")
                .addOption("-d", "default", "the schema directory")
                .addOption("-f", "128", "data frame size")
                .addOption("-m", "4", "decoder mode (1-convert to h5, 2-also fit pulses, 3-translate, 4-to hipo4)");
        store.parse(args);
        
        if(store.getCommand().compareTo("-convert")==0){
            OptionParser op = store.getOptionParser("-convert");
            EvioTools.convert(op.getInputList(),op.getOption("-o").stringValue());
        }
        
        if(store.getCommand().compareTo("-decode")==0){

            OptionParser op = store.getOptionParser("-decode");
            String scDir = op.getOption("-d").stringValue();
            
            if(scDir.compareTo("default")==0){
                scDir = System.getenv("CLAS12DIR") + "/etc/bankdefs/hipo4";
            } 
            
            EvioTools.decode(op.getInputList().get(0),
                    op.getOption("-o").stringValue(),
                    scDir,
                    op.getOption("-m").intValue(),
                    op.getOption("-t").intValue(),
                    op.getOption("-f").intValue()
                    );
        }
    }
}
