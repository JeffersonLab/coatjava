package org.jlab.service.urwt;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.geant4.v2.MPGD.URWT.URWTStripFactory;
import org.jlab.groot.data.H1F;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.math.F1D;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataBank;

/**
 *
 * URWT reconstruction engine
 * 
 * @author bondi, devita
 */
public class URWTEngine extends ReconstructionEngine {

    public static Logger LOGGER = Logger.getLogger(URWTEngine.class.getName());

    public URWTStripFactory factory = null;

    public URWTEngine() {
        super("URWT","bondi","1.0");
    }

    @Override
    public boolean init() {

        // init ConstantsManager to read constants from CCDB
        String variationName = Optional.ofNullable(this.getEngineConfigString("variation")).orElse("default");
        
        factory = new URWTStripFactory(11, variationName);
        // register output banks for drop option        
        this.registerOutputBank("URWT::hits");
        this.registerOutputBank("URWT::clusters");
        this.registerOutputBank("URWT::crosses");

        LOGGER.log(Level.INFO, "--> URWTs are ready...");
        return true;
    }

    @Override
    public void detectorChanged(int runNumber) {
        String variationName = Optional.ofNullable(this.getEngineConfigString("variation")).orElse("default");
        factory = new URWTStripFactory(runNumber, variationName);
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        List<URWRStrip>     strips = URWRStrip.getStrips(event, factory, this.getConstantsManager());
        List<URWTCluster> clusters = URWTCluster.createClusters(strips);
        List<URWTCross>    crosses = URWTCross.createCrosses(clusters);
        
        this.writeHipoBanks(event, strips, clusters, crosses);
        
        return true;
    }

    
    private void writeHipoBanks(DataEvent de, 
                                List<URWRStrip>     strips, 
                                List<URWTCluster> clusters, 
                                List<URWTCross>    crosses){
	    
        DataBank bankS = de.createBank("URWT::hits", strips.size());
        for(int h = 0; h < strips.size(); h++){
            bankS.setShort("id",        h, (short) strips.get(h).getId());
            bankS.setByte("sector",     h,  (byte) strips.get(h).getDescriptor().getSector());
            bankS.setByte("layer",      h,  (byte) strips.get(h).getDescriptor().getLayer());
            bankS.setShort("strip",     h, (short) strips.get(h).getDescriptor().getComponent());
            bankS.setFloat("energy",    h, (float) strips.get(h).getEnergy());
            bankS.setFloat("time",      h, (float) strips.get(h).getTime());                
            bankS.setShort("status",    h, (short) strips.get(h).getStatus());
            bankS.setShort("clusterId", h, (short) strips.get(h).getClusterId());
        }
        
        DataBank bankC = de.createBank("URWT::clusters", clusters.size());        
        for(int c = 0; c < clusters.size(); c++){
            bankC.setShort("id",       c, (short) clusters.get(c).getId());
            bankC.setByte("sector",    c,  (byte) clusters.get(c).get(0).getDescriptor().getSector());
            bankC.setByte("layer",     c,  (byte) clusters.get(c).get(0).getDescriptor().getLayer());
            bankC.setShort("strip",    c, (short) clusters.get(c).getMaxStrip());
            bankC.setFloat("energy",   c, (float) clusters.get(c).getEnergy());
            bankC.setFloat("time",     c, (float) clusters.get(c).getTime());
            bankC.setFloat("xo",       c, (float) clusters.get(c).getLine().origin().x());
            bankC.setFloat("yo",       c, (float) clusters.get(c).getLine().origin().y());
            bankC.setFloat("zo",       c, (float) clusters.get(c).getLine().origin().z());
            bankC.setFloat("xe",       c, (float) clusters.get(c).getLine().end().x());
            bankC.setFloat("ye",       c, (float) clusters.get(c).getLine().end().y());
            bankC.setFloat("ze",       c, (float) clusters.get(c).getLine().end().z());
            bankC.setShort("size",     c, (short) clusters.get(c).size());
            bankC.setShort("status",   c, (short) clusters.get(c).getStatus()); 
        }       
        
        DataBank bankX = de.createBank("URWT::crosses", crosses.size());        
        for(int c = 0; c < crosses.size(); c++){
            bankX.setShort("id",       c, (short) crosses.get(c).getId());
            bankX.setByte("sector",    c,  (byte) crosses.get(c).getSector());
            bankX.setByte("region",    c,  (byte) crosses.get(c).getRegion());
            bankX.setFloat("energy",   c, (float) crosses.get(c).getEnergy());
            bankX.setFloat("time",     c, (float) crosses.get(c).getTime());
            bankX.setFloat("x",        c, (float) crosses.get(c).point().x());
            bankX.setFloat("y",        c, (float) crosses.get(c).point().y());
            bankX.setFloat("z",        c, (float) crosses.get(c).point().z());
            bankX.setShort("cluster1", c, (short) crosses.get(c).getCluster1()); 
            bankX.setShort("cluster2", c, (short) crosses.get(c).getCluster2()); 
            bankX.setShort("status",   c, (short) crosses.get(c).getStatus()); 
        }       
        de.appendBanks(bankS,bankC,bankX);
    }

    
    public static void fitGauss(H1F histo) {
        double mean  = histo.getMean();
        double amp   = histo.getBinContent(histo.getMaximumBin());
        double rms   = histo.getRMS();
        double sigma = rms/2;
        double min = mean - 3*rms;
        double max = mean + 3*rms;
        
        F1D f1   = new F1D("f1res","[amp]*gaus(x,[mean],[sigma])", min, max);
        f1.setLineColor(2);
        f1.setLineWidth(2);
        f1.setOptStat("1111");
        f1.setParameter(0, amp);
        f1.setParameter(1, mean);
        f1.setParameter(2, sigma);
            
        if(amp>5) {
            f1.setParLimits(0, amp*0.2,   amp*1.2);
            f1.setParLimits(1, mean*0.5,  mean*1.5);
            f1.setParLimits(2, sigma*0.2, sigma*2);
//            System.out.print("1st...");
            DataFitter.fit(f1, histo, "Q");
            mean  = f1.getParameter(1);
            sigma = f1.getParameter(2);
            f1.setParLimits(0, 0, 2*amp);
            f1.setParLimits(1, mean-sigma, mean+sigma);
            f1.setParLimits(2, 0, sigma*2);
            f1.setRange(mean-2.0*sigma,mean+2.0*sigma);
            DataFitter.fit(f1, histo, "Q");
        }
    }    
    
}
