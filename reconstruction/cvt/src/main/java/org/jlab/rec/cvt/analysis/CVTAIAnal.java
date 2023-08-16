package org.jlab.rec.cvt.analysis;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.jlab.groot.data.H1F;

import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventType;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.io.task.DataSourceProcessorPane;
import org.jlab.io.task.IDataEventListener;
import org.jlab.rec.cvt.bmt.BMTType;

public class CVTAIAnal implements IDataEventListener {

    JPanel                  mainPanel 	= null;
    DataSourceProcessorPane processorPane 	= null;
 	private JTabbedPane     tabbedPane      = null;
 
	private EmbeddedCanvas can1 = null;
        private EmbeddedCanvas can2 = null;
        private EmbeddedCanvas can3 = null;
        private EmbeddedCanvas can4 = null;
        private EmbeddedCanvas can5 = null;
        private EmbeddedCanvas can6 = null;
        private EmbeddedCanvas can7 = null;
        
	private final H1F bsttrue = new H1F("bst", "BST TRACK HIT REC LEVEL (0:in cluster, 1: in cross, 2: in seed, 3: in track)", 4, -0.5, 3.5);
        private final H1F bstfalse = new H1F("bst", "BST BG HIT REC LEVEL (0:in cluster, 1: in cross, 2: in seed, 3: in track)", 4, -0.5, 3.5);
        private final H1F bmttrue = new H1F("bmt", "BMT TRACK HIT REC LEVEL (0:in cluster, 1: in cross, 2: in seed, 3: in track)", 4, -0.5, 3.5);
        private final H1F bmtfalse = new H1F("bmt", "BMT BG HIT REC LEVEL (0:in cluster, 1: in cross, 2: in seed, 3: in track)", 4, -0.5, 3.5);
        
        int nbins = 13;
        double mid = 0.3;
        double width = 0.05;
    
        private final H1F bstvspTT = new H1F("bstvsp", "BST TRUE POSITIVES ", nbins, mid-width, mid+width*2*(nbins-1));
        private final H1F bstvspTF = new H1F("bstvsp", "BST FALSE NEGATIVES ", nbins, mid-width, mid+width*2*(nbins-1));
        private final H1F bstvspFT = new H1F("bstvsp", "BST FALSE POSITIVES ", nbins, mid-width, mid+width*2*(nbins-1));
        private final H1F bstvspFF = new H1F("bstvsp", "BST TRUE NEGATIVES ", nbins, mid-width, mid+width*2*(nbins-1));
        private final H1F bmtvspTT = new H1F("bmtvsp", "BMT TRUE POSITIVES", nbins, mid-width, mid+width*2*(nbins-1));
        private final H1F bmtvspTF = new H1F("bmtvsp", "BMT FALSE NEGATIVES", nbins, mid-width, mid+width*2*(nbins-1));
        private final H1F bmtvspFT = new H1F("bmtvsp", "BMT FALSE POSITIVES ", nbins, mid-width, mid+width*2*(nbins-1));
        private final H1F bmtvspFF = new H1F("bmtvsp", "BMT TRUE NEGATIVES ", nbins, mid-width, mid+width*2*(nbins-1));
       
        int nbins2 = 9;
        double mid2 = 40;
        double width2 = 4;
    
        private final H1F bstvsthTT = new H1F("bstvsth", "BST TRUE POSITIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bstvsthTF = new H1F("bstvsth", "BST FALSE NEGATIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bstvsthFT = new H1F("bstvsth", "BST FALSE POSITIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bstvsthFF = new H1F("bstvsth", "BST TRUE NEGATIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthTT = new H1F("bmtvsth", "BMT TRUE POSITIVES", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthTF = new H1F("bmtvsth", "BMT FALSE NEGATIVES", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthFT = new H1F("bmtvsth", "BMT FALSE POSITIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthFF = new H1F("bmtvsth", "BMT TRUE NEGATIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        
        private final H1F bstNNTT = new H1F("bstNNTT", "BST TRUE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bstNNTF = new H1F("bstNNTT", "BST FALSE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bstNNFT = new H1F("bstNNTT", "BST FALSE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bstNNFF = new H1F("bstNNTT", "BST TRUE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNTT = new H1F("bmtNNTT", "BMT TRUE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNTF = new H1F("bmtNNTT", "BMT FALSE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNFT = new H1F("bmtNNTT", "BMT FALSE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNFF = new H1F("bmtNNTT", "BMT TRUE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        
        int counter = 0;
        int updateTime = 100;

        Analysis pAnal ;
        Analysis thAnal ;
        
        public CVTAIAnal() {
            //init
            pAnal = new Analysis();
            pAnal.init(nbins);
            thAnal = new Analysis();
            thAnal.init(nbins2);
            
            // create main panel
            mainPanel = new JPanel();	
            mainPanel.setLayout(new BorderLayout());

            tabbedPane 	= new JTabbedPane();

            processorPane = new DataSourceProcessorPane();
            processorPane.setUpdateRate(10);

            mainPanel.add(tabbedPane);
            mainPanel.add(processorPane,BorderLayout.PAGE_END);

            this.processorPane.addEventListener(this);
            bsttrue.setLineColor(4);
            bstfalse.setLineColor(2);
            bmttrue.setLineColor(4);
            bmtfalse.setLineColor(2);
            
            bstvspTT.setLineColor(39);
            bstvspTF.setLineColor(37);
            bstvspFT.setLineColor(36);
            bstvspFF.setLineColor(35);
            bmtvspTT.setLineColor(49);
            bmtvspTF.setLineColor(47);
            bmtvspFT.setLineColor(46);
            bmtvspFF.setLineColor(45);
            
            bstvspTT.setTitleX(" p (GeV)");
            bstvspTT.setTitleY("Efficiency");
            bstvspTF.setTitleX(" p (GeV)");
            bstvspTF.setTitleY("Efficiency");
            bstvspFT.setTitleX(" p (GeV)");
            bstvspFT.setTitleY("Efficiency");
            bstvspFF.setTitleX(" p (GeV)");
            bstvspFF.setTitleY("Efficiency");
            bmtvspTT.setTitleX(" p (GeV)");
            bmtvspTT.setTitleY("Efficiency");
            bmtvspTF.setTitleX(" p (GeV)");
            bmtvspTF.setTitleY("Efficiency");
            bmtvspFT.setTitleX(" p (GeV)");
            bmtvspFT.setTitleY("Efficiency");
            bmtvspFF.setTitleX(" p (GeV)");
            bmtvspFF.setTitleY("Efficiency");
            
            bstvsthTT.setLineColor(39);
            bstvsthTF.setLineColor(37);
            bstvsthFT.setLineColor(36);
            bstvsthFF.setLineColor(35);
            bmtvsthTT.setLineColor(49);
            bmtvsthTF.setLineColor(47);
            bmtvsthFT.setLineColor(46);
            bmtvsthFF.setLineColor(45);
            
            bstvsthTT.setTitleX(" #theta (deg)");
            bstvsthTT.setTitleY("Efficiency");
            bstvsthTF.setTitleX(" #theta (deg)");
            bstvsthTF.setTitleY("Efficiency");
            bstvsthFT.setTitleX(" #theta (deg)");
            bstvsthFT.setTitleY("Efficiency");
            bstvsthFF.setTitleX(" #theta (deg)");
            bstvsthFF.setTitleY("Efficiency");
            bmtvsthTT.setTitleX(" #theta (deg)");
            bmtvsthTT.setTitleY("Efficiency");
            bmtvsthTF.setTitleX(" #theta (deg)");
            bmtvsthTF.setTitleY("Efficiency");
            bmtvsthFT.setTitleX(" #theta (deg)");
            bmtvsthFT.setTitleY("Efficiency");
            bmtvsthFF.setTitleX(" #theta (deg)");
            bmtvsthFF.setTitleY("Efficiency");
            
            createCanvas();
            addCanvasToPane();
            init();

	}


	

	private void createCanvas() {
            can1 = new EmbeddedCanvas(); can1.initTimer(updateTime);
            can2 = new EmbeddedCanvas(); can2.initTimer(updateTime);
            can3 = new EmbeddedCanvas(); can3.initTimer(updateTime);
            can4 = new EmbeddedCanvas(); can4.initTimer(updateTime);
            can5 = new EmbeddedCanvas(); can5.initTimer(updateTime);
            can6 = new EmbeddedCanvas(); can6.initTimer(updateTime);
            can7 = new EmbeddedCanvas(); can7.initTimer(updateTime);
		
                
	}

	private void init() {
		drawPlots();
        
	}
	
        @Override
        public void dataEventAction(DataEvent event) {
            counter++; 
            HipoDataEvent hipo = (HipoDataEvent) event;      

            if (hipo.hasBank("BST::HitsPos") && hipo.hasBank("CVT::STracks")) {
                if(hipo.getBank("CVT::STracks").rows()==1) 
                    process(hipo); 
            }
            if (event.getType() == DataEventType.EVENT_STOP) {
                for(int i =0; i<nbins; i++) {
                    pAnal.allOnTrack[0][i] = pAnal.truePositives[0][i]+pAnal.falseNegatives[0][i];
                    pAnal.allOffTrack[0][i] = pAnal.falsePositives[0][i]+pAnal.trueNegatives[0][i];
                    pAnal.allOnTrack[1][i] = pAnal.truePositives[1][i]+pAnal.falseNegatives[1][i];
                    pAnal.allOffTrack[1][i] = pAnal.falsePositives[1][i]+pAnal.trueNegatives[1][i];
                    //System.out.println("p:BST "+i+"] "+pAnal.truePositives[0][i]+"/"+pAnal.allOnTrack[0][i]);
                    //System.out.println("p:BMT "+i+"] "+pAnal.truePositives[1][i]+"/"+pAnal.allOnTrack[1][i]);
                    double bstTT=100.0*(double)pAnal.truePositives[0][i]/(double)pAnal.allOnTrack[0][i];
                    double bstTF=100.0*(double)pAnal.trueNegatives[0][i]/(double)pAnal.allOffTrack[0][i];
                    double bstFT=100.0*(double)pAnal.falsePositives[0][i]/(double)pAnal.allOffTrack[0][i];
                    double bstFF=100.0*(double)pAnal.falseNegatives[0][i]/(double)pAnal.allOnTrack[0][i];
                    double bmtTT=100.0*(double)pAnal.truePositives[1][i]/(double)pAnal.allOnTrack[1][i];
                    double bmtTF=100.0*(double)pAnal.trueNegatives[1][i]/(double)pAnal.allOffTrack[1][i];
                    double bmtFT=100.0*(double)pAnal.falsePositives[1][i]/(double)pAnal.allOffTrack[1][i];
                    double bmtFF=100.0*(double)pAnal.falseNegatives[1][i]/(double)pAnal.allOnTrack[1][i];

                    //errors:
                    double bstTTE=100.0*calcE((double)pAnal.truePositives[0][i],(double)pAnal.allOnTrack[0][i]);
                    double bstTFE=100.0*calcE((double)pAnal.trueNegatives[0][i],(double)pAnal.allOffTrack[0][i]);
                    double bstFTE=100.0*calcE((double)pAnal.falsePositives[0][i],(double)pAnal.allOffTrack[0][i]);
                    double bstFFE=100.0*calcE((double)pAnal.falseNegatives[0][i],(double)pAnal.allOnTrack[0][i]);
                    double bmtTTE=100.0*calcE((double)pAnal.truePositives[1][i],(double)pAnal.allOnTrack[1][i]);
                    double bmtTFE=100.0*calcE((double)pAnal.trueNegatives[1][i],(double)pAnal.allOffTrack[1][i]);
                    double bmtFTE=100.0*calcE((double)pAnal.falsePositives[1][i],(double)pAnal.allOffTrack[1][i]);
                    double bmtFFE=100.0*calcE((double)pAnal.falseNegatives[1][i],(double)pAnal.allOnTrack[1][i]);


                    bstvspTT.setBinContent(i, bstTT);
                    bstvspTF.setBinContent(i, bstTF);
                    bstvspFT.setBinContent(i, bstFT);
                    bstvspFF.setBinContent(i, bstFF);
                    bmtvspFF.setBinContent(i, bmtFF);
                    bmtvspFT.setBinContent(i, bmtFT);
                    bmtvspTF.setBinContent(i, bmtTF);
                    bmtvspTT.setBinContent(i, bmtTT);

                    bstvspTT.setBinError(i, bstTTE);
                    bstvspTF.setBinError(i, bstTFE);
                    bstvspFT.setBinError(i, bstFTE);
                    bstvspFF.setBinError(i, bstFFE);
                    bmtvspFF.setBinError(i, bmtFFE);
                    bmtvspFT.setBinError(i, bmtFTE);
                    bmtvspTF.setBinError(i, bmtTFE);
                    bmtvspTT.setBinError(i, bmtTTE);

                }
                for(int i =0; i<nbins2; i++) {
                    thAnal.allOnTrack[0][i] = thAnal.truePositives[0][i]+thAnal.falseNegatives[0][i];
                    thAnal.allOffTrack[0][i] = thAnal.falsePositives[0][i]+thAnal.trueNegatives[0][i];
                    thAnal.allOnTrack[1][i] = thAnal.truePositives[1][i]+thAnal.falseNegatives[1][i];
                    thAnal.allOffTrack[1][i] = thAnal.falsePositives[1][i]+thAnal.trueNegatives[1][i];
                    //System.out.println("th:BST "+i+"] "+thAnal.truePositives[0][i]+"/"+thAnal.allOnTrack[0][i]);
                    //System.out.println("th:BMT "+i+"] "+thAnal.truePositives[1][i]+"/"+thAnal.allOnTrack[1][i]);
                    double bstTT=100.0*(double)thAnal.truePositives[0][i]/(double)thAnal.allOnTrack[0][i];
                    double bstTF=100.0*(double)thAnal.trueNegatives[0][i]/(double)thAnal.allOffTrack[0][i];
                    double bstFT=100.0*(double)thAnal.falsePositives[0][i]/(double)thAnal.allOffTrack[0][i];
                    double bstFF=100.0*(double)thAnal.falseNegatives[0][i]/(double)thAnal.allOnTrack[0][i];
                    double bmtTT=100.0*(double)thAnal.truePositives[1][i]/(double)thAnal.allOnTrack[1][i];
                    double bmtTF=100.0*(double)thAnal.trueNegatives[1][i]/(double)thAnal.allOffTrack[1][i];
                    double bmtFT=100.0*(double)thAnal.falsePositives[1][i]/(double)thAnal.allOffTrack[1][i];
                    double bmtFF=100.0*(double)thAnal.falseNegatives[1][i]/(double)thAnal.allOnTrack[1][i];

                    //errors:
                    double bstTTE=100.0*calcE((double)thAnal.truePositives[0][i],(double)thAnal.allOnTrack[0][i]);
                    double bstTFE=100.0*calcE((double)thAnal.trueNegatives[0][i],(double)thAnal.allOffTrack[0][i]);
                    double bstFTE=100.0*calcE((double)thAnal.falsePositives[0][i],(double)thAnal.allOffTrack[0][i]);
                    double bstFFE=100.0*calcE((double)thAnal.falseNegatives[0][i],(double)thAnal.allOnTrack[0][i]);
                    double bmtTTE=100.0*calcE((double)thAnal.truePositives[1][i],(double)thAnal.allOnTrack[1][i]);
                    double bmtTFE=100.0*calcE((double)thAnal.trueNegatives[1][i],(double)thAnal.allOffTrack[1][i]);
                    double bmtFTE=100.0*calcE((double)thAnal.falsePositives[1][i],(double)thAnal.allOffTrack[1][i]);
                    double bmtFFE=100.0*calcE((double)thAnal.falseNegatives[1][i],(double)thAnal.allOnTrack[1][i]);


                    bstvsthTT.setBinContent(i, bstTT);
                    bstvsthTF.setBinContent(i, bstTF);
                    bstvsthFT.setBinContent(i, bstFT);
                    bstvsthFF.setBinContent(i, bstFF);
                    bmtvsthFF.setBinContent(i, bmtFF);
                    bmtvsthFT.setBinContent(i, bmtFT);
                    bmtvsthTF.setBinContent(i, bmtTF);
                    bmtvsthTT.setBinContent(i, bmtTT);

                    bstvsthTT.setBinError(i, bstTTE);
                    bstvsthTF.setBinError(i, bstTFE);
                    bstvsthFT.setBinError(i, bstFTE);
                    bstvsthFF.setBinError(i, bstFFE);
                    bmtvsthFF.setBinError(i, bmtFFE);
                    bmtvsthFT.setBinError(i, bmtFTE);
                    bmtvsthTF.setBinError(i, bmtTFE);
                    bmtvsthTT.setBinError(i, bmtTTE);

                }
            }
        }
           

        @Override
        public void timerUpdate() {
            //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void resetEventListener() {
            counter=0;
            this.init();
       }
    
    
    private int getBin(double var, int nbins, double mid, double width) {
        
        int ii = 99;
        for(int i =0; i<nbins; i++) {
            double nmid = mid+width*(2*i);
            if(var>nmid-width && var<=nmid+width)
                ii=i;
        }
        
        return ii;
    }
   
    private void analyze(Event ev) {
        this.analyzeEvent(ev, ev.BSTHits);
        this.analyzeEvent(ev, ev.BMTHits);
    }
    
    private void analyzeEvent(Event ev, List<HitPos> hpl) {
        for(HitPos hp : hpl) {
            if(hp.gettTrack()==null) continue;
            int d = -1;
            if(hp.getDetType()==BMTType.UNDEFINED) {
                d = 0;
                if(hp.isTruePositive) bstNNTT.fill(hp.getNearestNeighbors().size());
                if(hp.isFalsePositive) bstNNFT.fill(hp.getNearestNeighbors().size());
                if(hp.isTrueNegative) bstNNTF.fill(hp.getNearestNeighbors().size());
                if(hp.isFalseNegative) bstNNFF.fill(hp.getNearestNeighbors().size());
                
                if(hp.getTstatus()==1) {
                    if(hp.getRlevel()==1000) 
                        bsttrue.fill(0);
                    if(hp.getRlevel()==1100) {
                        bsttrue.fill(0);
                        bsttrue.fill(1);
                    }
                    if(hp.getRlevel()==1110) {
                        bsttrue.fill(0);
                        bsttrue.fill(1);
                        bsttrue.fill(2);
                    }
                    if(hp.getRlevel()==1111) {
                        bsttrue.fill(0);
                        bsttrue.fill(1);
                        bsttrue.fill(2);
                        bsttrue.fill(3);
                    }
                }
                if(hp.getTstatus()==0) {
                    if(hp.getRlevel()==1000) 
                        bstfalse.fill(0);
                    if(hp.getRlevel()==1100) {
                        bstfalse.fill(0);
                        bstfalse.fill(1);
                    }
                    if(hp.getRlevel()==1110) {
                        bstfalse.fill(0);
                        bstfalse.fill(1);
                        bstfalse.fill(2);
                    }
                    if(hp.getRlevel()==1111) {
                        bstfalse.fill(0);
                        bstfalse.fill(1);
                        bstfalse.fill(2);
                        bstfalse.fill(3);
                    }
                }
            } else {
                d = 1;
                if(hp.isTruePositive) bmtNNTT.fill(hp.getNearestNeighbors().size());
                if(hp.isFalsePositive) bmtNNFT.fill(hp.getNearestNeighbors().size());
                if(hp.isTrueNegative) bmtNNTF.fill(hp.getNearestNeighbors().size());
                if(hp.isFalseNegative) bmtNNFF.fill(hp.getNearestNeighbors().size());
                
                if(hp.getTstatus()==1) {
                    if(hp.getRlevel()==1000) 
                        bmttrue.fill(0);
                    if(hp.getRlevel()==1100) {
                        bmttrue.fill(0);
                        bmttrue.fill(1);
                    }
                    if(hp.getRlevel()==1110) {
                        bmttrue.fill(0);
                        bmttrue.fill(1);
                        bmttrue.fill(2);
                    }
                    if(hp.getRlevel()==1111) {
                        bmttrue.fill(0);
                        bmttrue.fill(1);
                        bmttrue.fill(2);
                        bmttrue.fill(3);
                    }
                    
                }
                if(hp.getTstatus()==0) {
                    if(hp.getRlevel()==1000) 
                        bmtfalse.fill(0);
                    if(hp.getRlevel()==1100) {
                        bmtfalse.fill(0);
                        bmtfalse.fill(1);
                    }
                    if(hp.getRlevel()==1110) {
                        bmtfalse.fill(0);
                        bmtfalse.fill(1);
                        bmtfalse.fill(2);
                    }
                    if(hp.getRlevel()==1111) {
                        bmtfalse.fill(0);
                        bmtfalse.fill(1);
                        bmtfalse.fill(2);
                        bmtfalse.fill(3);
                    }
                }
            }
            int pbin = this.getBin(hp.gettTrack().p, nbins, mid, width);
            if(hp.isTruePositive) pAnal.truePositives[d][pbin]++;
            if(hp.isFalsePositive) pAnal.falsePositives[d][pbin]++;
            if(hp.isTrueNegative) pAnal.trueNegatives[d][pbin]++;
            if(hp.isFalseNegative) pAnal.falseNegatives[d][pbin]++;
            
            int thbin = this.getBin(hp.gettTrack().theta, nbins2, mid2, width2); 
            if(hp.isTruePositive) thAnal.truePositives[d][thbin]++; 
            if(hp.isFalsePositive) thAnal.falsePositives[d][thbin]++;
            if(hp.isTrueNegative) thAnal.trueNegatives[d][thbin]++;
            if(hp.isFalseNegative) thAnal.falseNegatives[d][thbin]++;
        }
    }
    
    private void process(DataEvent event) {
        
	if(!event.hasBank("BST::HitsPos"))
            return;
        if(!event.hasBank("BMT::HitsPos"))
            return;
        Event ev = new Event();
        ev.processEvent(event);
        this.analyze(ev);
    }
    
   


    private void drawPlots() {
        can1.divide(2, 2);
        can1.cd(0);
        can1.draw(bsttrue, "E");
        can1.cd(1);
        can1.draw(bstfalse, "E");
        can1.cd(2);
        can1.draw(bmttrue, "E");
        can1.cd(3);
        can1.draw(bmtfalse, "E");
        
        can1.update();
        
        can2.divide(2, 2);
        can2.cd(0);
        can2.draw(bstvspTT, "E");
        can2.cd(1);
        can2.draw(bstvspTF, "E");
        can2.cd(2);
        can2.draw(bstvspFT, "E");
        can2.cd(3);
        can2.draw(bstvspFF, "E");
        can2.update();
        can3.divide(2, 2);
        can3.cd(0);
        can3.draw(bmtvspTT, "E");
        can3.cd(1);
        can3.draw(bmtvspTF, "E");
        can3.cd(2);
        can3.draw(bmtvspFT, "E");
        can3.cd(3);
        can3.draw(bmtvspFF, "E");
        can3.update();
        
        can4.divide(2, 2);
        can4.cd(0);
        can4.draw(bstvsthTT, "E");
        can4.cd(1);
        can4.draw(bstvsthTF, "E");
        can4.cd(2);
        can4.draw(bstvsthFT, "E");
        can4.cd(3);
        can4.draw(bstvsthFF, "E");
        can4.update();
        can5.divide(2, 2);
        can5.cd(0);
        can5.draw(bmtvsthTT, "E");
        can5.cd(1);
        can5.draw(bmtvsthTF, "E");
        can5.cd(2);
        can5.draw(bmtvsthFT, "E");
        can5.cd(3);
        can5.draw(bmtvsthFF, "E");
        can5.update();
        can6.divide(2, 2);
        can6.cd(0);
        can6.draw(bstNNTT, "E");
        can6.cd(1);
        can6.draw(bstNNTF, "E");
        can6.cd(2);
        can6.draw(bstNNFT, "E");
        can6.cd(3);
        can6.draw(bstNNFF, "E");
        can6.update();
        can7.divide(2, 2);
        can7.cd(0);
        can7.draw(bmtNNTT, "E");
        can7.cd(1);
        can7.draw(bmtNNTF, "E");
        can7.cd(2);
        can7.draw(bmtNNFT, "E");
        can7.cd(3);
        can7.draw(bmtNNFF, "E");
        can7.update();
        
    }

    private void addCanvasToPane() {
            tabbedPane.add("REC LEVEL", can1);
            tabbedPane.add("p BST AI MATRIX", can2);
            tabbedPane.add("p BMT AI MATRIX", can3);
            tabbedPane.add("theta BST AI MATRIX", can4);
            tabbedPane.add("theta BMT AI MATRIX", can5);
            tabbedPane.add("BST BG Nearest Neighbors", can6);
            tabbedPane.add("BMT BG Nearest Neighbors", can7);
    }

    public static void main(String[] args) {
            JFrame frame = new JFrame("ANALYSIS");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            Dimension screensize = null;
            screensize = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize((int) (screensize.getHeight() * .75 * 1.618), (int) (screensize.getHeight() * .75));
            CVTAIAnal viewer = new CVTAIAnal();
            //frame.add(viewer.getPanel());
            frame.add(viewer.mainPanel);
            frame.setVisible(true);
    }

    private double calcE(double a, double b) {
        double dE = Math.sqrt(a)/b*Math.sqrt(a/b+1);
        return dE;
    }

    

}
		


