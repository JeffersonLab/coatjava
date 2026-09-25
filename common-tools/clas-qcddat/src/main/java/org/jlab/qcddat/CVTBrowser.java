package org.jlab.qcddat;

import javafx.application.Application;
/**
 *
 * @author veronique
 */
public class CVTBrowser {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Viewer <input.hipo>");
            System.err.println("Example: java Viewer file.hipo");
            System.exit(1);
        }

        String inputFile = args[0];

        CVTViewer.configure(inputFile);
        Application.launch(CVTViewer.class);
    }
}
