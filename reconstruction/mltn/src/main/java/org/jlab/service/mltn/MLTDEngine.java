package org.jlab.service.mltn;

import j4ml.clas12.ejml.ArchiveProvider;
import j4ml.clas12.ejml.EJMLTrackNeuralNetwork;
import j4ml.clas12.network.Clas12TrackFinder;
import j4ml.clas12.tracking.ClusterCombinations;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataBank;
import org.jlab.utils.CLASResources;

/**
 *
 * @author gavalian
 */
public class MLTDEngine extends ReconstructionEngine {
    
    EJMLTrackNeuralNetwork       network = null;
    private String         networkFlavor = "default";
    private Integer        userRunNumber = -1;
    private String       inputBankPrefix = "";
    private String      outputBankPrefix = "ai";
    private String             inputBank = null;
    private String            outputBank = null;

    public MLTDEngine(){
        super("MLTD","gavalian","1.0");
    }
    
    @Override
    public boolean init() {
        inputBankPrefix  = Optional.ofNullable(this.getEngineConfigString("inputBankPrefix")).orElse("");
        outputBankPrefix = Optional.ofNullable(this.getEngineConfigString("outputBankPrefix")).orElse("ai");
        inputBank  = "HitBasedTrkg::"+inputBankPrefix+"Clusters";
        outputBank = outputBankPrefix+"::tracks";
        networkFlavor = Optional.ofNullable(this.getEngineConfigString("flavor")).orElse("default");
        userRunNumber = Integer.valueOf(Optional.ofNullable(this.getEngineConfigString("run")).orElse("-1"));
        return true;
    }

    public boolean load(int run) {
        String path = CLASResources.getResourcePath("etc/ejml/ejmlclas12.network"); 
        if(this.getEngineConfigString("network")!=null) 
            path = this.getEngineConfigString("network");
        System.out.println("[neural-network] info : Loading neural network from " + path);
        network = new EJMLTrackNeuralNetwork();        
        Map<String,String>  files = new HashMap<>();
        files.put("classifier", "trackClassifier.network");
        files.put("fixer", "trackFixer.network");
        ArchiveProvider provider = new ArchiveProvider(path.trim());
        //----- This will find in the archive the last run number closest
        //----- to provided run number that contains trained network.
        //----- it works similar to CCDB, but not exatly, for provided 
        //----- run number it looks for run that has smaller number,
        //----- however it the provided run # it lower than anything 
        //----- existing in the arhive, it will return the closest run 
        //----- number entry.
        int adjustedRun = provider.findEntry(run);
        String directory = String.format("network/%d/%s", adjustedRun, networkFlavor);
        network.initZip(path.trim(),directory, files);
        System.out.println("[neural-network] info : Loading neural network files done...");
        System.out.println("[neural-network] info : Only network is initialized...");
        return true;
    }

    @Override
    public void detectorChanged(int runNumber) {
        if (userRunNumber < 0) {
            load(runNumber);
        }
        else if (network == null) {
            load(userRunNumber);
        }
    }

    @Override
    public boolean processDataEventUser(DataEvent de) {
        if(de.hasBank(inputBank)==true){
            DataBank bank = de.getBank(inputBank);           
            HipoDataBank hipoBank = (HipoDataBank) bank;
            Clas12TrackFinder trackFinder = new Clas12TrackFinder();
            trackFinder.setTrackingNetwork(network);
            trackFinder.process(hipoBank.getBank());            
            writeBank(de,trackFinder.getResults());            
        }
        return true;
    }
    
    public void writeBank(DataEvent event, ClusterCombinations combi){
        DataBank bank = event.createBank(outputBank, combi.getSize());
        for(int i = 0; i < combi.getSize(); i++){
            bank.setByte("id", i, (byte) (i+1));
            bank.setByte("sector", i, (byte) 1);
            bank.setByte("charge", i, (byte) combi.setRow(i).getStatus());
            bank.setFloat("prob", i, (float) combi.setRow(i).getProbability());
            int[] ids = combi.getLabels(i);
            for(int c = 0; c < 6; c++){
                int order = c+1;
                bank.setShort("c"+order, i, (short) ids[c]);
            }
        }
        event.removeBank(outputBank);
        event.appendBank(bank);
    }

}
