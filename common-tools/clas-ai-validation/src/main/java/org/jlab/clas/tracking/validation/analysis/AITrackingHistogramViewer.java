/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.analysis;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jlab.groot.data.TDirectory;
import org.jlab.groot.ui.TBrowser;

/**
 *
 * @author veronique
 */


/**
 * Opens the histogram file produced by AITrackingPerformanceAnalysis.
 */
public final class AITrackingHistogramViewer {

    private AITrackingHistogramViewer() {
    }

    public static void main(String[] args) {

        String histogramFile =
                args.length > 0
                        ? args[0]
                        : chooseHistogramFile();

        if (histogramFile == null) {
            return;
        }

        TDirectory directory =
                new TDirectory();

        directory.readFile(
                histogramFile);

        new TBrowser(
                directory);
    }

    private static String chooseHistogramFile() {

        JFileChooser chooser =
                new JFileChooser();

        chooser.setDialogTitle(
                "Open AI tracking histogram file");

        chooser.setFileFilter(
                new FileNameExtensionFilter(
                        "HIPO histogram files",
                        "hipo"));

        int result =
                chooser.showOpenDialog(null);

        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        return chooser.getSelectedFile()
                .getAbsolutePath();
    }
}