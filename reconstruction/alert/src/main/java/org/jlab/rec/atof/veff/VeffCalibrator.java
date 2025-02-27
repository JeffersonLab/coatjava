package org.jlab.rec.atof.veff;

import java.util.ArrayList;
import org.jlab.rec.atof.cluster.ATOFCluster;
import org.jlab.rec.atof.constants.Parameters;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;

/**
 *
 * @author npilleux
 */
public class VeffCalibrator {

    private final ArrayList<VeffCalibration> calibs = new ArrayList<>();

    public ArrayList<VeffCalibration> getCalibs() {
        return calibs;
    }

    public void setCalibs(ArrayList<VeffCalibration> calibs) {
        this.calibs.clear();
        if (calibs != null) {
            this.calibs.addAll(calibs);
        }
    }

    public boolean computeCalib(ArrayList<ATOFCluster> Clusters) {
        calibs.clear();
        for (int i_c = 0; i_c < Clusters.size(); i_c++) {
            ATOFCluster cluster = Clusters.get(i_c);
            ArrayList<ATOFHit> WedgeHits = cluster.getWedgeHits();
            if (WedgeHits.size() < 1) {
                continue;
            }
            double zFromWedge = cluster.getMaxWedgeHit().getZ();
            double Lup = Parameters.LENGTH_ATOF / 2 + zFromWedge;
            double Ldown = Parameters.LENGTH_ATOF / 2 - zFromWedge;
            ArrayList<BarHit> BarHits = cluster.getBarHits();
            for (int i_b = 0; i_b < BarHits.size(); i_b++) {
                BarHit barhit = BarHits.get(i_b);
                double uphit_time = barhit.getHitUp().getTime();
                double downhit_time = barhit.getHitDown().getTime();
                calibs.add(new VeffCalibration(barhit.computeModuleIndex(),(Lup - Ldown),(uphit_time - downhit_time),i_c,i_b));     
            }
        }
        return true;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        /*
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/rec_ahdc_alert_020797_small_updated_for_test.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);
        VeffCalibration en = new VeffCalibration();
        en.init();
        String fileName_veff = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/veff_test.csv";
        try (
                PrintWriter writer = new PrintWriter(new FileWriter(fileName_veff))) {
            int event_number = 0;
            writer.printf("event_number,i_cluster,i_bar,module,Ldiff,tdiff%n");
            while (reader.hasEvent()) {
                {
                    event_number++;
                    DataEvent event = (DataEvent) reader.getNextEvent();
                    en.processDataEvent(event, writer, event_number);
                    System.out.print("------ \n");
                }

            }
            JFrame frame = new JFrame("tsum");
            frame.setSize(2500, 800);
            EmbeddedCanvas canvas = new EmbeddedCanvas();
            canvas.cd(0);
            canvas.draw(en.h_veff);
            frame.add(canvas);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            writer.flush();
        } catch (IOException e) {
            System.err.println("An error occurred while writing the file: " + e.getMessage());
        }
         */
    }
}

