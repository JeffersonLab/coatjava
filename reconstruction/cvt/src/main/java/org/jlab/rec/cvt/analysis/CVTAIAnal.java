package org.jlab.rec.cvt.analysis;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.List;
import java.util.Map;

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
        private EmbeddedCanvas can8 = null;
        
	private final H1F bsttrue = new H1F("bst", "BST TRACK HIT REC LEVEL (bin 1:in cluster, 2: in cross, 3: in seed, 4: in track)", 4, -0.5, 3.5);
        private final H1F bstfalse = new H1F("bst", "BST BG HIT REC LEVEL (bin 1:in cluster, 2: in cross, 3: in seed, 4: in track)", 4, -0.5, 3.5);
        private final H1F bmttrue = new H1F("bmt", "BMT TRACK HIT REC LEVEL (bin 1:in cluster, 2: in cross, 3: in seed, 4: in track)", 4, -0.5, 3.5);
        private final H1F bmtfalse = new H1F("bmt", "BMT BG HIT REC LEVEL (bin 1:in cluster, 2: in cross, 3: in seed, 4: in track)", 4, -0.5, 3.5);
        
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
    
        private final H1F bstvsthTP = new H1F("bstvsth", "BST TRUE POSITIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bstvsthFN = new H1F("bstvsth", "BST FALSE NEGATIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bstvsthFP = new H1F("bstvsth", "BST FALSE POSITIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bstvsthTN = new H1F("bstvsth", "BST TRUE NEGATIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthTP = new H1F("bmtvsth", "BMT TRUE POSITIVES", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthFN = new H1F("bmtvsth", "BMT FALSE NEGATIVES", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthFP = new H1F("bmtvsth", "BMT FALSE POSITIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        private final H1F bmtvsthTN = new H1F("bmtvsth", "BMT TRUE NEGATIVES ", nbins2, mid2-width2, mid2+width2*2*(nbins2-1));
        
        private final H1F bstNNTP = new H1F("bstNNTT", "BST TRUE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bstNNFN = new H1F("bstNNTF", "BST FALSE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bstNNFP = new H1F("bstNNFT", "BST FALSE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bstNNTN = new H1F("bstNNFF", "BST TRUE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNTP = new H1F("bmtNNTT", "BMT TRUE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNFN = new H1F("bmtNNTF", "BMT FALSE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNFP = new H1F("bmtNNFT", "BMT FALSE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtNNTN = new H1F("bmtNNFF", "BMT TRUE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtCNNTP = new H1F("bmtCNNTT", "BMT-C TRUE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtCNNFN = new H1F("bmtCNNTF", "BMT-C FALSE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtCNNFP = new H1F("bmtCNNFT", "BMT-C FALSE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtCNNTN = new H1F("bmtCNNFF", "BMT-C TRUE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtZNNTP = new H1F("bmtZNNTT", "BMT-Z TRUE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtZNNFN = new H1F("bmtZNNTF", "BMT-Z FALSE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtZNNFP = new H1F("bmtZNNFT", "BMT-Z FALSE POSITIVES nb BG Nearest Neighbors", 20, 0, 20);
        private final H1F bmtZNNTN = new H1F("bmtZNNFF", "BMT-Z TRUE NEGATIVES nb BG Nearest Neighbors", 20, 0, 20);
        
        private final H1F bmtTCNNTP = new H1F("bmtTCNNTT", "BMT-C TRUE POSITIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        private final H1F bmtTCNNFN = new H1F("bmtTCNNTF", "BMT-C FALSE NEGATIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        private final H1F bmtTCNNFP = new H1F("bmtTCNNFT", "BMT-C FALSE POSITIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        private final H1F bmtTCNNTN = new H1F("bmtTCNNFF", "BMT-C TRUE NEGATIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        private final H1F bmtTZNNTP = new H1F("bmtTZNNTT", "BMT-Z TRUE POSITIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        private final H1F bmtTZNNFN = new H1F("bmtTZNNTF", "BMT-Z FALSE NEGATIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        private final H1F bmtTZNNFP = new H1F("bmtTZNNFT", "BMT-Z FALSE POSITIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        private final H1F bmtTZNNTN = new H1F("bmtTZNNFF", "BMT-Z TRUE NEGATIVES BG Nearest Neighbors TIMES", 500, 0, 500.0);
        
        private final H1F bmtTCHTP = new H1F("bmtTCHTT", "BMT-C TRUE POSITIVES  TIMES", 500, 0, 500.0);
        private final H1F bmtTCHFN = new H1F("bmtTCHTF", "BMT-C FALSE NEGATIVES  TIMES", 500, 0, 500.0);
        private final H1F bmtTCHFP = new H1F("bmtTCHFT", "BMT-C FALSE POSITIVES  TIMES", 500, 0, 500.0);
        private final H1F bmtTCHTN = new H1F("bmtTCHFF", "BMT-C TRUE NEGATIVES  TIMES", 500, 0, 500.0);
        private final H1F bmtTZHTP = new H1F("bmtTZHTT", "BMT-Z TRUE POSITIVES  TIMES", 500, 0, 500.0);
        private final H1F bmtTZHFN = new H1F("bmtTZHTF", "BMT-Z FALSE NEGATIVES  TIMES", 500, 0, 500.0);
        private final H1F bmtTZHFP = new H1F("bmtTZHFT", "BMT-Z FALSE POSITIVES  TIMES", 500, 0, 500.0);
        private final H1F bmtTZHTN = new H1F("bmtTZHFF", "BMT-Z TRUE NEGATIVES  TIMES", 500, 0, 500.0);
        
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
            
            bstvsthTP.setLineColor(39);
            bstvsthFN.setLineColor(37);
            bstvsthFP.setLineColor(36);
            bstvsthTN.setLineColor(35);
            bmtvsthTP.setLineColor(49);
            bmtvsthFN.setLineColor(47);
            bmtvsthFP.setLineColor(46);
            bmtvsthTN.setLineColor(45);
            
            bstvsthTP.setTitleX(" #theta (deg)");
            bstvsthTP.setTitleY("Efficiency");
            bstvsthFN.setTitleX(" #theta (deg)");
            bstvsthFN.setTitleY("Efficiency");
            bstvsthFP.setTitleX(" #theta (deg)");
            bstvsthFP.setTitleY("Efficiency");
            bstvsthTN.setTitleX(" #theta (deg)");
            bstvsthTN.setTitleY("Efficiency");
            bmtvsthTP.setTitleX(" #theta (deg)");
            bmtvsthTP.setTitleY("Efficiency");
            bmtvsthFN.setTitleX(" #theta (deg)");
            bmtvsthFN.setTitleY("Efficiency");
            bmtvsthFP.setTitleX(" #theta (deg)");
            bmtvsthFP.setTitleY("Efficiency");
            bmtvsthTN.setTitleX(" #theta (deg)");
            bmtvsthTN.setTitleY("Efficiency");
            
            bmtCNNTP.setLineColor(36);
            bmtCNNFP.setLineColor(36);
            bmtCNNFN.setLineColor(36);
            bmtCNNTN.setLineColor(36);
            bmtZNNTP.setLineColor(38);
            bmtZNNFP.setLineColor(38);
            bmtZNNFN.setLineColor(38);
            bmtZNNTN.setLineColor(38);
            
            bmtTCNNTP.setLineColor(36);
            bmtTCNNFP.setLineColor(36);
            bmtTCNNFN.setLineColor(36);
            bmtTCNNTN.setLineColor(36);
            bmtTZNNTP.setLineColor(38);
            bmtTZNNFP.setLineColor(38);
            bmtTZNNFN.setLineColor(38);
            bmtTZNNTN.setLineColor(38);
            
            bmtTCHTP.setLineColor(7);
            bmtTCHFP.setLineColor(7);
            bmtTCHFN.setLineColor(7);
            bmtTCHTN.setLineColor(7);
            bmtTZHTP.setLineColor(9);
            bmtTZHFP.setLineColor(9);
            bmtTZHFN.setLineColor(9);
            bmtTZHTN.setLineColor(9);
            
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
            can8 = new EmbeddedCanvas(); can8.initTimer(updateTime);
                
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


                    bstvsthTP.setBinContent(i, bstTT);
                    bstvsthFN.setBinContent(i, bstTF);
                    bstvsthFP.setBinContent(i, bstFT);
                    bstvsthTN.setBinContent(i, bstFF);
                    bmtvsthTN.setBinContent(i, bmtFF);
                    bmtvsthFP.setBinContent(i, bmtFT);
                    bmtvsthFN.setBinContent(i, bmtTF);
                    bmtvsthTP.setBinContent(i, bmtTT);

                    bstvsthTP.setBinError(i, bstTTE);
                    bstvsthFN.setBinError(i, bstTFE);
                    bstvsthFP.setBinError(i, bstFTE);
                    bstvsthTN.setBinError(i, bstFFE);
                    bmtvsthTN.setBinError(i, bmtFFE);
                    bmtvsthFP.setBinError(i, bmtFTE);
                    bmtvsthFN.setBinError(i, bmtTFE);
                    bmtvsthTP.setBinError(i, bmtTTE);

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
                if(hp.isTruePositive) bstNNTP.fill(hp.getNearestNeighbors().size());
                if(hp.isFalsePositive) bstNNFP.fill(hp.getNearestNeighbors().size());
                if(hp.isTrueNegative) bstNNTN.fill(hp.getNearestNeighbors().size());
                if(hp.isFalseNegative) bstNNFN.fill(hp.getNearestNeighbors().size());
                
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
                if(hp.isTruePositive) {
                    bmtNNTP.fill(hp.getNearestNeighbors().size());
                    if(hp.getDetType()==BMTType.C) {
                        bmtCNNTP.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTCHTP, bmtTCNNTP, ev.BMTTimes);
                    }
                    if(hp.getDetType()==BMTType.Z) {
                        bmtZNNTP.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTZHTP, bmtTZNNTP, ev.BMTTimes);
                    }
                }
                if(hp.isFalsePositive) {
                    bmtNNFP.fill(hp.getNearestNeighbors().size());
                    if(hp.getDetType()==BMTType.C) {
                        bmtCNNFP.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTCHFP, bmtTCNNFP, ev.BMTTimes);
                    }
                    if(hp.getDetType()==BMTType.Z) {
                        bmtZNNFP.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTZHFP, bmtTZNNFP, ev.BMTTimes);
                    }
                }
                if(hp.isFalseNegative) {
                    bmtNNFN.fill(hp.getNearestNeighbors().size());
                    if(hp.getDetType()==BMTType.C) {
                        bmtCNNFN.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTCHFN, bmtTCNNFN, ev.BMTTimes);
                    }
                    if(hp.getDetType()==BMTType.Z) {
                        bmtZNNFN.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTZHFN, bmtTZNNFN, ev.BMTTimes);
                    }
                }
                if(hp.isTrueNegative) {
                    bmtNNTN.fill(hp.getNearestNeighbors().size());
                    if(hp.getDetType()==BMTType.C) {
                        bmtCNNTN.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTCHTN, bmtTCNNTN, ev.BMTTimes);
                    }
                    if(hp.getDetType()==BMTType.Z) {
                        bmtZNNTN.fill(hp.getNearestNeighbors().size());
                        this.FillTimes(hp, bmtTZHTN, bmtTZNNTN, ev.BMTTimes);
                    }
                }
                
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
        for(int k =0; k<4;k++) {
            can1.getCanvasPads().get(k).getAxisX().getAttributes().setShowAxis(false);
            can1.getCanvasPads().get(k).getAxisX().getAttributes().setGrid(false);
        }
        can1.cd(0);
        can1.draw(bsttrue, "");
        can1.cd(1);
        can1.draw(bstfalse, "");
        can1.cd(2);
        can1.draw(bmttrue, "");
        can1.cd(3);
        can1.draw(bmtfalse, "");
        
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
        can4.draw(bstvsthTP, "E");
        can4.cd(1);
        can4.draw(bstvsthFN, "E");
        can4.cd(2);
        can4.draw(bstvsthFP, "E");
        can4.cd(3);
        can4.draw(bstvsthTN, "E");
        can4.update();
        can5.divide(2, 2);
        can5.cd(0);
        can5.draw(bmtvsthTP, "E");
        can5.cd(1);
        can5.draw(bmtvsthFN, "E");
        can5.cd(2);
        can5.draw(bmtvsthFP, "E");
        can5.cd(3);
        can5.draw(bmtvsthTN, "E");
        can5.update();
        can6.divide(2, 2);
        can6.cd(0);
        can6.draw(bstNNTP, "E");
        can6.cd(1);
        can6.draw(bstNNFN, "E");
        can6.cd(2);
        can6.draw(bstNNFP, "E");
        can6.cd(3);
        can6.draw(bstNNTN, "E");
        can6.update();
        can7.divide(2, 2);
        can7.cd(0);
        can7.draw(bmtNNTP, "E");
        can7.draw(bmtCNNTP, "same");
        can7.draw(bmtZNNTP, "same");
        can7.cd(1);
        can7.draw(bmtNNFN, "E");
        can7.draw(bmtCNNFN, "same");
        can7.draw(bmtZNNFN, "same");
        can7.cd(2);
        can7.draw(bmtNNFP, "E");
        can7.draw(bmtCNNFP, "same");
        can7.draw(bmtZNNFP, "same");
        can7.cd(3);
        can7.draw(bmtNNTN, "E");
        can7.draw(bmtCNNTN, "same");
        can7.draw(bmtZNNTN, "same");
        can7.update();
        can8.divide(2, 2);
        for(int k =0; k<4;k++) {
            can8.getCanvasPads().get(k).getAxisY().getAttributes().setLog(true);
        }
        can8.cd(0);
        can8.draw(bmtTCHTP, "");
        can8.draw(bmtTCNNTP, "same");
        can8.draw(bmtTZHTP, "same");
        can8.draw(bmtTZNNTP, "same");
        can8.cd(1);
        can8.draw(bmtTCHFN, "");
        can8.draw(bmtTCNNFN, "same");
        can8.draw(bmtTZHFN, "same");
        can8.draw(bmtTZNNFN, "same");
        can8.cd(2);
        can8.draw(bmtTCHFP, "");
        can8.draw(bmtTCNNFP, "same");
        can8.draw(bmtTZHFP, "same");
        can8.draw(bmtTZNNFP, "same");
        can8.cd(3);
        can8.draw(bmtTCHTN, "");
        can8.draw(bmtTCNNTN, "same");
        can8.draw(bmtTZHTN, "same");
        can8.draw(bmtTZNNTN, "same");
        can8.update();
                
        
    }

    private void addCanvasToPane() {
            tabbedPane.add("REC LEVEL", can1);
            tabbedPane.add("p BST AI MATRIX", can2);
            tabbedPane.add("p BMT AI MATRIX", can3);
            tabbedPane.add("theta BST AI MATRIX", can4);
            tabbedPane.add("theta BMT AI MATRIX", can5);
            tabbedPane.add("BST BG Nearest Neighbors", can6);
            tabbedPane.add("BMT BG Nearest Neighbors", can7);
            tabbedPane.add("BMT BG Nearest Neighbors T", can8);
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

    private void FillTimes(HitPos h, H1F bmtTH, H1F bmtTNN, Map<Integer, Float> BMTTimes) {
        for(HitPos hp : h.getNearestNeighbors()) {
            if(BMTTimes.containsKey(hp.getID())) {
                double t = (double) BMTTimes.get(hp.getID());
                bmtTNN.fill(t);
            } else {
                System.out.println("missing id "+hp.getID());
            }
        }
        double ht = (double) BMTTimes.get(h.getID());
        bmtTH.fill(ht);
    }

}
		


