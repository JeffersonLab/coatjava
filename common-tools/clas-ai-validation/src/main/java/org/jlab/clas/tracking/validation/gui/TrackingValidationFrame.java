/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt
 * to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java
 * to edit this template
 */
package org.jlab.clas.tracking.validation.gui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jlab.clas.tracking.validation.MatchResult;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.service.AIValidationEngine;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

/**
 * Standalone Swing browser for AI tracking-validation events.
 *
 * The frame uses the same {@link AIValidationEngine} processing path as the
 * command-line test in AIValidationEngine.main(). Previously viewed events are
 * cached so that Previous does not reopen or reread the HIPO file.
 *
 * @author veronique
 */
public final class TrackingValidationFrame extends JFrame {

    private final TrackingValidationPanel validationPanel =
            new TrackingValidationPanel();

    private final JButton openButton =
            new JButton("Open HIPO...");

    private final JButton previousButton =
            new JButton("Previous");

    private final JButton nextButton =
            new JButton("Next");

    private final JLabel fileLabel =
            new JLabel("No file selected");

    private final JLabel positionLabel =
            new JLabel("No event");

    private final List<LoadedValidation> history =
            new ArrayList<>();

    private HipoDataSource source;
    private AIValidationEngine engine;
    private File currentFile;
    private int currentIndex = -1;
    private long fileEventNumber;
    private boolean loading;
    private boolean endOfFile = true;

    public TrackingValidationFrame() {

        super("CLAS12 AI Tracking Validation");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1450, 920);
        setLocationByPlatform(true);

        JPanel buttonPanel =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        buttonPanel.add(openButton);
        buttonPanel.add(previousButton);
        buttonPanel.add(nextButton);

        JPanel navigation =
                new JPanel(new BorderLayout(12, 0));

        navigation.setBorder(
                BorderFactory.createEmptyBorder(4, 6, 4, 6));

        navigation.add(buttonPanel, BorderLayout.WEST);
        navigation.add(fileLabel, BorderLayout.CENTER);
        navigation.add(positionLabel, BorderLayout.EAST);

        JPanel content =
                new JPanel(new BorderLayout(4, 4));

        content.add(navigation, BorderLayout.NORTH);
        content.add(validationPanel, BorderLayout.CENTER);

        setContentPane(content);

        openButton.addActionListener(event -> chooseFile());
        previousButton.addActionListener(event -> showPreviousEvent());
        nextButton.addActionListener(event -> showNextEvent());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closeSource();
            }
        });

        updateNavigationState();
    }

    /** Opens a file chooser and loads the selected HIPO file. */
    public void chooseFile() {

        File initialDirectory =
                currentFile == null
                        ? new File(System.getProperty("user.dir"))
                        : currentFile.getParentFile();

        JFileChooser chooser =
                new JFileChooser(initialDirectory);

        chooser.setDialogTitle("Open CLAS12 HIPO file");
        chooser.setFileFilter(
                new FileNameExtensionFilter(
                        "HIPO files (*.hipo)",
                        "hipo"));
        chooser.setAcceptAllFileFilterUsed(true);

        if (chooser.showOpenDialog(this)
                == JFileChooser.APPROVE_OPTION) {

            openFile(chooser.getSelectedFile());
        }
    }

    /**
     * Opens a file and loads its first event.
     *
     * @param file HIPO file
     */
    public void openFile(File file) {

        if (file == null) {
            return;
        }

        if (!file.isFile()) {
            showError(
                    "The selected file does not exist:\n"
                    + file.getAbsolutePath(),
                    null);
            return;
        }

        closeSource();
        history.clear();
        currentIndex = -1;
        fileEventNumber = 0L;
        currentFile = file;
        loading = false;
        endOfFile = false;

        validationPanel.clearEvent();

        source = new HipoDataSource();
        engine = new AIValidationEngine();

        try {
            if (!engine.init()) {
                throw new IllegalStateException(
                        "AIValidationEngine initialization failed");
            }

            source.open(file.getAbsolutePath());

            fileLabel.setText(file.getName());
            fileLabel.setToolTipText(file.getAbsolutePath());
            setTitle(
                    "CLAS12 AI Tracking Validation - "
                    + file.getName());

            positionLabel.setText("Opening first event...");
            loadNextEvent();

        } catch (RuntimeException exception) {
            closeSource();
            engine = null;
            endOfFile = true;
            fileLabel.setText("No file selected");
            fileLabel.setToolTipText(null);
            positionLabel.setText("No event");
            updateNavigationState();

            showError(
                    "Unable to open HIPO file:\n"
                    + file.getAbsolutePath(),
                    exception);
        }
    }

    /**
     * Displays an already validated event without opening a file.
     *
     * @param event validation event
     * @param results truth-match results for that event
     */
    public void setEvent(
            ValidationEvent event,
            List<MatchResult> results) {

        closeSource();
        engine = null;
        history.clear();
        currentIndex = -1;
        fileEventNumber = 0L;
        currentFile = null;
        endOfFile = true;

        if (event != null) {
            LoadedValidation loaded =
                    new LoadedValidation(
                            event,
                            results,
                            1L,
                            false);

            history.add(loaded);
            currentIndex = 0;
            validationPanel.setEvent(
                    loaded.event,
                    loaded.results);
        } else {
            validationPanel.clearEvent();
        }

        fileLabel.setText("Programmatic event");
        updatePositionLabel();
        updateNavigationState();
    }

    private void showPreviousEvent() {

        if (loading || currentIndex <= 0) {
            return;
        }

        currentIndex--;
        showCachedEvent();
    }

    private void showNextEvent() {

        if (loading) {
            return;
        }

        int nextCachedIndex =
                currentIndex + 1;

        if (nextCachedIndex < history.size()) {
            currentIndex = nextCachedIndex;
            showCachedEvent();
            return;
        }

        loadNextEvent();
    }

    private void showCachedEvent() {

        LoadedValidation loaded =
                history.get(currentIndex);

        validationPanel.setEvent(
                loaded.event,
                loaded.results);

        updatePositionLabel();
        updateNavigationState();
    }

    /**
     * Reads the next HIPO event and runs exactly the same validation engine as
     * AIValidationEngine.main(). The work is done outside the Swing event
     * dispatch thread.
     */
    private void loadNextEvent() {

        if (loading
                || source == null
                || engine == null
                || endOfFile) {

            updateNavigationState();
            return;
        }

        loading = true;
        positionLabel.setText("Loading event...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        updateNavigationState();

        SwingWorker<LoadedValidation, Void> worker =
                new SwingWorker<>() {

            @Override
            protected LoadedValidation doInBackground() {

                if (!source.hasEvent()) {
                    return null;
                }

                DataEvent dataEvent =
                        source.getNextEvent();

                long eventNumberInFile =
                        ++fileEventNumber;

                boolean success =
                        engine.processDataEventUser(dataEvent);

                if (!success) {
                    throw new IllegalStateException(
                            "AIValidationEngine failed for file event "
                            + eventNumberInFile);
                }

                ValidationEvent validationEvent =
                        engine.getCurrentValidationEvent();

                if (validationEvent == null) {
                    throw new IllegalStateException(
                            "AIValidationEngine returned no ValidationEvent "
                            + "for file event "
                            + eventNumberInFile);
                }

                List<MatchResult> results =
                        Collections.unmodifiableList(
                                new ArrayList<>(
                                        engine.getCurrentEventResults()));

                return new LoadedValidation(
                        validationEvent,
                        results,
                        eventNumberInFile,
                        source.hasEvent());
            }

            @Override
            protected void done() {

                try {
                    LoadedValidation loaded =
                            get();

                    if (loaded == null) {
                        endOfFile = true;
                        positionLabel.setText("End of file");
                    } else {
                        endOfFile =
                                !loaded.hasMoreEvents;

                        history.add(loaded);
                        currentIndex =
                                history.size() - 1;

                        validationPanel.setEvent(
                                loaded.event,
                                loaded.results);

                        updatePositionLabel();
                    }

                } catch (Exception exception) {
                    /*
                     * Do not declare end-of-file after a validation failure.
                     * The user may press Next to inspect the following event.
                     */
                    endOfFile =
                            source == null
                            || !source.hasEvent();

                    showError(
                            "Unable to read or validate the next event.",
                            exception);

                } finally {
                    loading = false;
                    setCursor(Cursor.getDefaultCursor());
                    updateNavigationState();
                }
            }
        };

        worker.execute();
    }

    private void updatePositionLabel() {

        if (currentIndex < 0
                || currentIndex >= history.size()) {

            positionLabel.setText("No event");
            return;
        }

        LoadedValidation loaded =
                history.get(currentIndex);

        ValidationEvent event =
                loaded.event;

        positionLabel.setText(
                String.format(
                        "file event %d | run %d event %d | results %d",
                        loaded.fileEventNumber,
                        event.getRun(),
                        event.getEvent(),
                        loaded.results.size()));
    }

    private void updateNavigationState() {

        openButton.setEnabled(!loading);

        previousButton.setEnabled(
                !loading
                && currentIndex > 0);

        boolean hasCachedNext =
                currentIndex + 1 < history.size();

        nextButton.setEnabled(
                !loading
                && (hasCachedNext
                || (source != null
                && !endOfFile)));
    }

    private void closeSource() {

        if (source == null) {
            return;
        }

        try {
            source.close();
        } catch (RuntimeException exception) {
            // Closing the frame or opening another file.
        } finally {
            source = null;
        }
    }

    private void showError(
            String message,
            Throwable throwable) {

        Throwable root =
                rootCause(throwable);

        String details =
                root == null
                        ? message
                        : message
                        + "\n\n"
                        + root.getClass().getName()
                        + ": "
                        + root.getMessage();

        JOptionPane.showMessageDialog(
                this,
                details,
                "Tracking validation",
                JOptionPane.ERROR_MESSAGE);
    }

    private static Throwable rootCause(
            Throwable throwable) {

        if (throwable == null) {
            return null;
        }

        Throwable root = throwable;
        while (root.getCause() != null
                && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * Creates a frame for one already validated event.
     */
    public static TrackingValidationFrame show(
            ValidationEvent event,
            List<MatchResult> results) {

        final TrackingValidationFrame[] reference =
                new TrackingValidationFrame[1];

        Runnable task =
                () -> {
                    TrackingValidationFrame frame =
                            new TrackingValidationFrame();

                    frame.setEvent(event, results);
                    frame.setVisible(true);
                    reference[0] = frame;
                };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Unable to create tracking-validation display",
                        exception);
            }
        }

        return reference[0];
    }

    /**
     * Standalone launcher. With no argument, a file chooser opens. A HIPO
     * filename may be supplied as the first program argument.
     */
    public static void main(String[] arguments) {

        String clas12Dir =
                System.getProperty("CLAS12DIR");

        if (clas12Dir == null
                || clas12Dir.isBlank()) {

            String environmentValue =
                    System.getenv("CLAS12DIR");

            if (environmentValue != null
                    && !environmentValue.isBlank()) {

                System.setProperty(
                        "CLAS12DIR",
                        environmentValue);
            }
        }

        SwingUtilities.invokeLater(
                () -> {
                    TrackingValidationFrame frame =
                            new TrackingValidationFrame();

                    frame.setVisible(true);

                    if (arguments != null
                            && arguments.length > 0
                            && arguments[0] != null
                            && !arguments[0].isBlank()) {

                        frame.openFile(new File(arguments[0]));
                    } else {
                        frame.chooseFile();
                    }
                });
    }

    private static final class LoadedValidation {

        private final ValidationEvent event;
        private final List<MatchResult> results;
        private final long fileEventNumber;
        private final boolean hasMoreEvents;

        private LoadedValidation(
                ValidationEvent event,
                List<MatchResult> results,
                long fileEventNumber,
                boolean hasMoreEvents) {

            this.event = event;
            this.results =
                    results == null
                            ? Collections.emptyList()
                            : results;
            this.fileEventNumber = fileEventNumber;
            this.hasMoreEvents = hasMoreEvents;
        }
    }
}
