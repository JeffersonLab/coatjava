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

public class CVTAIAnal implements IDataEventListener {

    JPanel                  mainPanel 	= null;
    DataSourceProcessorPane processorPane 	= null;
 	private JTabbedPane     tabbedPane      = null;
 
	private EmbeddedCanvas can1 = null;
        private EmbeddedCanvas can2 = null;
        private EmbeddedCanvas can3 = null;
        private EmbeddedCanvas can4 = null;
        private EmbeddedCanvas can5 = null;
        
	private H1F bsttrue = new H1F("bst", "selected ", 4, -0.5, 3.5);
        private H1F bstfalse = new H1F("bst", "selected ", 4, -0.5, 3.5);
        private H1F bmttrue = new H1F("bmt", "selected ", 4, -0.5, 3.5);
        private H1F bmtfalse = new H1F("bmt", "selected ", 4, -0.5, 3.5);
        
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
            
            bstvspTT.setTitleX("Generated p (GeV)");
            bstvspTT.setTitleY("Efficiency");
            bstvspTF.setTitleX("Generated p (GeV)");
            bstvspTF.setTitleY("Efficiency");
            bstvspFT.setTitleX("Generated p (GeV)");
            bstvspFT.setTitleY("Efficiency");
            bstvspFF.setTitleX("Generated p (GeV)");
            bstvspFF.setTitleY("Efficiency");
            bmtvspTT.setTitleX("Generated p (GeV)");
            bmtvspTT.setTitleY("Efficiency");
            bmtvspTF.setTitleX("Generated p (GeV)");
            bmtvspTF.setTitleY("Efficiency");
            bmtvspFT.setTitleX("Generated p (GeV)");
            bmtvspFT.setTitleY("Efficiency");
            bmtvspFF.setTitleX("Generated p (GeV)");
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
		//can1.divide(1, 2); 
                
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
                    pAnal.allOnTrack[1][i] = pAnal.truePositives[0][i]+pAnal.falseNegatives[1][i];
                    pAnal.allOffTrack[1][i] = pAnal.falsePositives[0][i]+pAnal.trueNegatives[1][i];
                    
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
                    thAnal.allOnTrack[1][i] = thAnal.truePositives[0][i]+thAnal.falseNegatives[1][i];
                    thAnal.allOffTrack[1][i] = thAnal.falsePositives[0][i]+thAnal.trueNegatives[1][i];
                    
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
            
            int pbin = this.getBin(hp.gettTrack().p, nbins, mid, width);
            if(hp.isTruePositive) pAnal.truePositives[0][pbin]++;
            if(hp.isFalsePositive) pAnal.falsePositives[0][pbin]++;
            if(hp.isTrueNegative) pAnal.trueNegatives[0][pbin]++;
            if(hp.isFalseNegative) pAnal.falseNegatives[0][pbin]++;
            
            int thbin = this.getBin(hp.gettTrack().theta, nbins2, mid2, width2); 
            if(hp.isTruePositive) thAnal.truePositives[0][thbin]++;
            if(hp.isFalsePositive) thAnal.falsePositives[0][thbin]++;
            if(hp.isTrueNegative) thAnal.trueNegatives[0][thbin]++;
            if(hp.isFalseNegative) thAnal.falseNegatives[0][thbin]++;
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
        
    }

    private void addCanvasToPane() {
            tabbedPane.add("", can1);
            tabbedPane.add("", can2);
            tabbedPane.add("", can3);
            tabbedPane.add("", can4);
            tabbedPane.add("", can5);

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
		


