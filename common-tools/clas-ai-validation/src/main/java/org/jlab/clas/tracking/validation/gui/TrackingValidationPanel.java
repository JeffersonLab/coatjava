/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt
 * to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java
 * to edit this template
 */
package org.jlab.clas.tracking.validation.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableModel;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.MatchResult;
import org.jlab.clas.tracking.validation.TrackingObjectType;
import org.jlab.clas.tracking.validation.TruthIndex;
import org.jlab.clas.tracking.validation.data.Cluster;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.data.ValidationObject;
import org.jlab.detector.base.DetectorType;

/**
 * DC-focused event display comparing truth, hit-based, time-based, and
 * AI-cluster selections for one matched MC track.
 *
 * The primary view is local chamber X versus Z.  For DC hits, X is recovered
 * in the sector-local chamber frame and therefore may be negative.  The
 * secondary view shows wire versus global layer.
 *
 * @author veronique
 */
public final class TrackingValidationPanel extends JPanel {

    private static final int DC =
            DetectorType.DC.getDetectorId();

    private static final Color TRUTH_COLOR =
            new Color(45, 95, 210);
    private static final Color HB_COLOR =
            new Color(235, 135, 25);
    private static final Color TB_COLOR =
            new Color(0, 145, 75);
    private static final Color AI_COLOR =
            new Color(135, 55, 190);

    private final JLabel eventLabel =
            new JLabel("No event");
    private final JLabel selectionLabel =
            new JLabel("Select a file to begin");

    private final JComboBox<Integer> truthTrackBox =
            new JComboBox<>();
    private final JComboBox<Integer> sectorBox =
            new JComboBox<>();

    private final JCheckBox showTruth =
            new JCheckBox("MC truth", true);
    private final JCheckBox showHb =
            new JCheckBox("HB matched", true);
    private final JCheckBox showTb =
            new JCheckBox("TB matched", true);
    private final JCheckBox showAi =
            new JCheckBox("AI-cluster matched", true);

    private final ComparisonCanvas localXzCanvas =
            new ComparisonCanvas(ViewMode.LOCAL_X_Z);
    private final ComparisonCanvas layerWireCanvas =
            new ComparisonCanvas(ViewMode.LAYER_WIRE);

    private final DefaultTableModel metricsModel =
            new DefaultTableModel(
                    new Object[]{
                        "selection",
                        "object hits",
                        "truth matched",
                        "truth hits in sector",
                        "purity",
                        "efficiency"
                    },
                    0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private ValidationEvent event;
    private TruthIndex truthIndex;
    private List<MatchResult> results =
            Collections.emptyList();
    private boolean updatingControls;

    public TrackingValidationPanel() {
        super(new BorderLayout(6, 6));

        setBorder(
                BorderFactory.createEmptyBorder(
                        6,
                        6,
                        6,
                        6));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        truthTrackBox.addActionListener(action -> rebuildSectorBox());
        sectorBox.addActionListener(action -> refreshDisplay());

        java.awt.event.ActionListener repaintListener =
                action -> refreshDisplay();

        showTruth.addActionListener(repaintListener);
        showHb.addActionListener(repaintListener);
        showTb.addActionListener(repaintListener);
        showAi.addActionListener(repaintListener);

        clearEvent();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createEtchedBorder(),
                        BorderFactory.createEmptyBorder(5, 7, 5, 7)));

        eventLabel.setFont(
                eventLabel.getFont().deriveFont(Font.BOLD));
        eventLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(eventLabel);
        header.add(Box.createVerticalStrut(4));

        JPanel selectors =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selectors.setAlignmentX(LEFT_ALIGNMENT);
        selectors.add(new JLabel("MC track:"));
        selectors.add(truthTrackBox);
        selectors.add(new JLabel("Sector:"));
        selectors.add(sectorBox);
        selectors.add(Box.createHorizontalStrut(12));
        selectors.add(showTruth);
        selectors.add(showHb);
        selectors.add(showTb);
        selectors.add(showAi);
        header.add(selectors);

        JPanel legend =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 13, 2));
        legend.setAlignmentX(LEFT_ALIGNMENT);
        legend.add(new JLabel("Markers:"));
        legend.add(legendLabel(TRUTH_COLOR, "MC truth: open circle"));
        legend.add(legendLabel(HB_COLOR, "HB: square"));
        legend.add(legendLabel(TB_COLOR, "TB: filled circle"));
        legend.add(legendLabel(AI_COLOR, "AI cluster: diamond/cross"));
        header.add(legend);

        selectionLabel.setAlignmentX(LEFT_ALIGNMENT);
        header.add(selectionLabel);

        return header;
    }

    private static JLabel legendLabel(Color color, String text) {
        JLabel label = new JLabel("\u25A0 " + text);
        label.setForeground(color);
        return label;
    }

    private java.awt.Component buildCenter() {
        JTabbedPane plots = new JTabbedPane();
        plots.addTab("local X-Z", localXzCanvas);
        plots.addTab("layer-wire", layerWireCanvas);

        JTable table = new JTable(metricsModel);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);

        JPanel metricsPanel = new JPanel(new BorderLayout());
        metricsPanel.setBorder(
                BorderFactory.createTitledBorder(
                        "Displayed hit selections for the chosen MC track"));
        metricsPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JSplitPane split =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        plots,
                        metricsPanel);
        split.setResizeWeight(0.80);
        split.setDividerLocation(640);
        split.setContinuousLayout(true);
        return split;
    }

    public void clearEvent() {
        setEvent(null, Collections.emptyList());
    }

    /**
     * Installs one validated event and the exact matches produced by
     * AIValidationEngine.
     */
    public void setEvent(
            ValidationEvent validationEvent,
            List<MatchResult> matchResults) {

        event = validationEvent;
        truthIndex =
                validationEvent == null
                        ? null
                        : new TruthIndex(validationEvent.getHits());
        results =
                matchResults == null
                        ? Collections.emptyList()
                        : new ArrayList<>(matchResults);

        updatingControls = true;
        try {
            truthTrackBox.removeAllItems();
            sectorBox.removeAllItems();

            if (validationEvent != null) {
                for (int truthTrackId : availableTruthTracks()) {
                    truthTrackBox.addItem(truthTrackId);
                }
            }
        } finally {
            updatingControls = false;
        }

        if (validationEvent == null) {
            eventLabel.setText("No event");
            selectionLabel.setText("Select a HIPO file to begin");
            metricsModel.setRowCount(0);
            localXzCanvas.setData(DisplayData.empty());
            layerWireCanvas.setData(DisplayData.empty());
            return;
        }

        eventLabel.setText(
                String.format(
                        Locale.US,
                        "run %d   event %d",
                        validationEvent.getRun(),
                        validationEvent.getEvent()));

        rebuildSectorBox();
    }

    private List<Integer> availableTruthTracks() {
        Set<Integer> ids = new LinkedHashSet<>();

        for (MatchResult result : results) {
            if (result != null
                    && result.isMatched()
                    && result.getTruthTrackId() > 0
                    && isTrackLevelObject(result.getObject())) {
                ids.add(result.getTruthTrackId());
            }
        }

        if (truthIndex != null) {
            ids.addAll(truthIndex.getTruthTrackIds());
        }

        List<Integer> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        return sorted;
    }

    private static boolean isTrackLevelObject(ValidationObject object) {
        if (object == null) {
            return false;
        }
        return object.getType() == TrackingObjectType.SEED
                || object.getType() == TrackingObjectType.TRACK;
    }

    private void rebuildSectorBox() {
        if (updatingControls) {
            return;
        }

        Integer truthTrackId =
                (Integer) truthTrackBox.getSelectedItem();

        updatingControls = true;
        try {
            sectorBox.removeAllItems();

            if (truthTrackId != null && truthIndex != null) {
                Set<Integer> sectors = new LinkedHashSet<>();
                for (Hit hit : truthIndex.getHitsForTruthTrack(truthTrackId)) {
                    if (hit.getDetector() == DC && hit.getSector() > 0) {
                        sectors.add(hit.getSector());
                    }
                }

                List<Integer> sorted = new ArrayList<>(sectors);
                Collections.sort(sorted);
                for (Integer sector : sorted) {
                    sectorBox.addItem(sector);
                }
            }
        } finally {
            updatingControls = false;
        }

        refreshDisplay();
    }

    private void refreshDisplay() {
        if (updatingControls) {
            return;
        }

        Integer truthTrackId =
                (Integer) truthTrackBox.getSelectedItem();
        Integer sector =
                (Integer) sectorBox.getSelectedItem();

        if (event == null
                || truthIndex == null
                || truthTrackId == null
                || sector == null) {
            selectionLabel.setText("No matched MC track/sector selected");
            metricsModel.setRowCount(0);
            localXzCanvas.setData(DisplayData.empty());
            layerWireCanvas.setData(DisplayData.empty());
            return;
        }

        DisplayData data =
                buildDisplayData(truthTrackId, sector);

        data.showTruth = showTruth.isSelected();
        data.showHb = showHb.isSelected();
        data.showTb = showTb.isSelected();
        data.showAi = showAi.isSelected();

        localXzCanvas.setData(data);
        layerWireCanvas.setData(data);

        selectionLabel.setText(
                String.format(
                        Locale.US,
                        "MC track %d, sector %d | truth hits %d | "
                        + "HB matched %d | TB matched %d | AI-cluster matched %d",
                        truthTrackId,
                        sector,
                        data.truthKeys.size(),
                        data.hbMatchedKeys.size(),
                        data.tbMatchedKeys.size(),
                        data.aiMatchedKeys.size()));

        fillMetricsTable(data);
    }

    private DisplayData buildDisplayData(
            int truthTrackId,
            int sector) {

        Map<HitKey, Hit> dcHits = new LinkedHashMap<>();
        for (Hit hit : event.getHits()) {
            if (hit.getDetector() == DC
                    && hit.getSector() == sector) {
                dcHits.put(hit.key(), hit);
            }
        }

        Set<HitKey> truthKeys = new LinkedHashSet<>();
        for (Hit hit : truthIndex.getHitsForTruthTrack(truthTrackId)) {
            if (hit.getDetector() == DC
                    && hit.getSector() == sector) {
                truthKeys.add(hit.key());
            }
        }

        Set<HitKey> hbObjectKeys =
                collectMatchedObjectHits(
                        truthTrackId,
                        sector,
                        "DC-HB",
                        TrackingObjectType.SEED);

        Set<HitKey> tbObjectKeys =
                collectMatchedObjectHits(
                        truthTrackId,
                        sector,
                        "DC-TB",
                        TrackingObjectType.TRACK);

        Set<HitKey> aiObjectKeys =
                collectAiSuggestedClusterHits(sector);

        Set<HitKey> hbMatched = intersection(hbObjectKeys, truthKeys);
        Set<HitKey> tbMatched = intersection(tbObjectKeys, truthKeys);
        Set<HitKey> aiMatched = intersection(aiObjectKeys, truthKeys);

        return new DisplayData(
                truthTrackId,
                sector,
                dcHits,
                truthKeys,
                hbObjectKeys,
                hbMatched,
                tbObjectKeys,
                tbMatched,
                aiObjectKeys,
                aiMatched);
    }

    private Set<HitKey> collectMatchedObjectHits(
            int truthTrackId,
            int sector,
            String algorithm,
            TrackingObjectType type) {

        Set<HitKey> keys = new LinkedHashSet<>();

        for (MatchResult result : results) {
            if (result == null
                    || result.getTruthTrackId() != truthTrackId) {
                continue;
            }

            ValidationObject object = result.getObject();
            if (object == null
                    || object.getType() != type
                    || !algorithm.equals(object.getAlgorithm())) {
                continue;
            }

            for (HitKey key : object.getHitKeys()) {
                if (key != null
                        && key.getDetectorId() == DC
                        && key.getSector() == sector) {
                    keys.add(key);
                }
            }
        }

        return keys;
    }

    /**
     * AI membership comes from clusters marked by DcAiBankReader after they
     * occur in an ai::tracks candidate.  It is deliberately not inferred from
     * the AI fitted track bank.
     */
    private Set<HitKey> collectAiSuggestedClusterHits(int sector) {
        Set<HitKey> keys = new LinkedHashSet<>();

        for (Cluster cluster : event.getClusters()) {
            if (cluster.getDetector() != DC
                    || cluster.getSector() != sector
                    || !cluster.isAiSuggested()) {
                continue;
            }

            for (HitKey key : cluster.getHitKeys()) {
                if (key != null) {
                    keys.add(key);
                }
            }
        }

        return keys;
    }

    private static Set<HitKey> intersection(
            Set<HitKey> first,
            Set<HitKey> second) {
        Set<HitKey> result = new LinkedHashSet<>(first);
        result.retainAll(second);
        return result;
    }

    private void fillMetricsTable(DisplayData data) {
        metricsModel.setRowCount(0);

        addMetricsRow(
                "MC truth",
                data.truthKeys,
                data.truthKeys,
                data.truthKeys.size());

        addMetricsRow(
                "HB matched track",
                data.hbObjectKeys,
                data.hbMatchedKeys,
                data.truthKeys.size());

        addMetricsRow(
                "TB matched track",
                data.tbObjectKeys,
                data.tbMatchedKeys,
                data.truthKeys.size());

        addMetricsRow(
                "AI-suggested clusters",
                data.aiObjectKeys,
                data.aiMatchedKeys,
                data.truthKeys.size());
    }

    private void addMetricsRow(
            String label,
            Set<HitKey> objectKeys,
            Set<HitKey> matchedKeys,
            int truthHitCount) {

        int objectHitCount = objectKeys.size();
        int matchedHitCount = matchedKeys.size();

        double purity =
                objectHitCount == 0
                        ? 0.0
                        : (double) matchedHitCount / objectHitCount;

        double efficiency =
                truthHitCount == 0
                        ? 0.0
                        : (double) matchedHitCount / truthHitCount;

        metricsModel.addRow(
                new Object[]{
                    label,
                    objectHitCount,
                    matchedHitCount,
                    truthHitCount,
                    String.format(Locale.US, "%.4f", purity),
                    String.format(Locale.US, "%.4f", efficiency)
                });
    }

    private static int countDcHits(ValidationEvent event) {
        int count = 0;
        for (Hit hit : event.getHits()) {
            if (hit.getDetector() == DC) {
                count++;
            }
        }
        return count;
    }

    private enum ViewMode {
        LOCAL_X_Z,
        LAYER_WIRE
    }

    private final class ComparisonCanvas extends JPanel {

        private static final int LEFT = 74;
        private static final int RIGHT = 24;
        private static final int TOP = 46;
        private static final int BOTTOM = 58;

        private final ViewMode mode;
        private DisplayData data = DisplayData.empty();
        private List<ScreenPoint> screenPoints =
                Collections.emptyList();

        private ComparisonCanvas(ViewMode mode) {
            this.mode = mode;
            setPreferredSize(new Dimension(1000, 590));
            setMinimumSize(new Dimension(650, 360));
            setBackground(Color.WHITE);
            setOpaque(true);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        private void setData(DisplayData newData) {
            data = newData == null ? DisplayData.empty() : newData;
            repaint();
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            ScreenPoint nearest = null;
            double best = 11.0;

            for (ScreenPoint point : screenPoints) {
                double distance =
                        event.getPoint().distance(point.x, point.y);
                if (distance <= best) {
                    best = distance;
                    nearest = point;
                }
            }

            if (nearest == null) {
                return null;
            }

            Hit hit = nearest.hit;
            return String.format(
                    Locale.US,
                    "hit %d | sector %d | global layer %d | wire %d | "
                    + "local X %.3f cm | Z %.3f cm | %s",
                    hit.getId(),
                    hit.getSector(),
                    hit.getLayer(),
                    hit.getComponent(),
                    localX(hit),
                    hit.getZ(),
                    categoriesFor(hit.key(), data));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                List<Hit> hits = displayedHits(data);
                Bounds bounds = boundsFor(hits, mode);
                drawTitle(g, hits.size());
                drawAxes(g, bounds);
                drawPoints(g, hits, bounds);
            } finally {
                g.dispose();
            }
        }

        private void drawTitle(Graphics2D g, int count) {
            g.setColor(Color.DARK_GRAY);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 13.0f));

            String title =
                    mode == ViewMode.LOCAL_X_Z
                            ? "DC chamber-local X versus Z"
                            : "DC wire versus global layer";

            g.drawString(
                    title
                    + "   |   MC track "
                    + data.truthTrackId
                    + "   sector "
                    + data.sector
                    + "   displayed physical hits "
                    + count,
                    LEFT,
                    24);
        }

        private void drawAxes(Graphics2D g, Bounds bounds) {
            int width = Math.max(1, getWidth() - LEFT - RIGHT);
            int height = Math.max(1, getHeight() - TOP - BOTTOM);
            int x0 = LEFT;
            int y0 = TOP + height;

            g.setColor(new Color(235, 235, 235));
            g.setStroke(new BasicStroke(1.0f));

            int ticks = 6;
            for (int index = 0; index <= ticks; index++) {
                int x = LEFT + index * width / ticks;
                int y = TOP + index * height / ticks;
                g.drawLine(x, TOP, x, y0);
                g.drawLine(LEFT, y, LEFT + width, y);
            }

            g.setColor(Color.DARK_GRAY);
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(LEFT, TOP, width, height);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11.0f));

            for (int index = 0; index <= ticks; index++) {
                double fraction = (double) index / ticks;
                double horizontal = bounds.minX + fraction * bounds.dx();
                double vertical = bounds.maxY - fraction * bounds.dy();
                int x = LEFT + index * width / ticks;
                int y = TOP + index * height / ticks;

                g.drawString(
                        formatAxis(horizontal),
                        x - 16,
                        y0 + 20);
                g.drawString(
                        formatAxis(vertical),
                        7,
                        y + 4);
            }

            String horizontalLabel =
                    mode == ViewMode.LOCAL_X_Z
                            ? "Z at chamber midplane [cm]"
                            : "global DC layer [1...36]";

            String verticalLabel =
                    mode == ViewMode.LOCAL_X_Z
                            ? "local X at chamber midplane [cm]"
                            : "wire";

            g.setFont(g.getFont().deriveFont(Font.BOLD, 12.0f));
            g.drawString(
                    horizontalLabel,
                    LEFT + width / 2 - 85,
                    getHeight() - 15);

            g.rotate(-Math.PI / 2.0);
            g.drawString(
                    verticalLabel,
                    -TOP - height / 2 - 75,
                    18);
            g.rotate(Math.PI / 2.0);
        }

        private String formatAxis(double value) {
            return mode == ViewMode.LOCAL_X_Z
                    ? String.format(Locale.US, "%.1f", value)
                    : String.format(Locale.US, "%.0f", value);
        }

        private void drawPoints(
                Graphics2D g,
                List<Hit> hits,
                Bounds bounds) {

            int width = Math.max(1, getWidth() - LEFT - RIGHT);
            int height = Math.max(1, getHeight() - TOP - BOTTOM);
            List<ScreenPoint> rendered = new ArrayList<>();

            for (Hit hit : hits) {
                double horizontal = horizontal(hit, mode);
                double vertical = vertical(hit, mode);

                if (!Double.isFinite(horizontal)
                        || !Double.isFinite(vertical)) {
                    continue;
                }

                int x = LEFT + (int) Math.round(
                        (horizontal - bounds.minX) / bounds.dx() * width);
                int y = TOP + height - (int) Math.round(
                        (vertical - bounds.minY) / bounds.dy() * height);

                HitKey key = hit.key();

                if (data.showTruth && data.truthKeys.contains(key)) {
                    drawTruth(g, x, y);
                }
                if (data.showHb && data.hbMatchedKeys.contains(key)) {
                    drawHb(g, x, y);
                }
                if (data.showTb && data.tbMatchedKeys.contains(key)) {
                    drawTb(g, x, y);
                }
                if (data.showAi && data.aiMatchedKeys.contains(key)) {
                    drawAi(g, x, y);
                }

                rendered.add(new ScreenPoint(x, y, hit));
            }

            screenPoints = rendered;
        }

        private void drawTruth(Graphics2D g, int x, int y) {
            g.setColor(TRUTH_COLOR);
            g.setStroke(new BasicStroke(2.7f));
            g.drawOval(x - 7, y - 7, 14, 14);
        }

        private void drawHb(Graphics2D g, int x, int y) {
            g.setColor(HB_COLOR);
            g.setStroke(new BasicStroke(2.0f));
            g.drawRect(x - 5, y - 5, 10, 10);
        }

        private void drawTb(Graphics2D g, int x, int y) {
            g.setColor(TB_COLOR);
            g.fillOval(x - 4, y - 4, 8, 8);
        }

        private void drawAi(Graphics2D g, int x, int y) {
            g.setColor(AI_COLOR);
            Polygon diamond = new Polygon(
                    new int[]{x, x + 7, x, x - 7},
                    new int[]{y - 7, y, y + 7, y},
                    4);
            g.setStroke(new BasicStroke(1.8f));
            g.drawPolygon(diamond);
            g.drawLine(x - 5, y - 5, x + 5, y + 5);
            g.drawLine(x - 5, y + 5, x + 5, y - 5);
        }
    }

    private static List<Hit> displayedHits(DisplayData data) {
        Set<HitKey> visibleKeys = new LinkedHashSet<>();
        if (data.showTruth) {
            visibleKeys.addAll(data.truthKeys);
        }
        if (data.showHb) {
            visibleKeys.addAll(data.hbMatchedKeys);
        }
        if (data.showTb) {
            visibleKeys.addAll(data.tbMatchedKeys);
        }
        if (data.showAi) {
            visibleKeys.addAll(data.aiMatchedKeys);
        }

        List<Hit> hits = new ArrayList<>();
        for (HitKey key : visibleKeys) {
            Hit hit = data.hitsByKey.get(key);
            if (hit != null) {
                hits.add(hit);
            }
        }

        hits.sort(
                Comparator.comparingDouble(Hit::getZ)
                        .thenComparingInt(Hit::getLayer)
                        .thenComparingInt(Hit::getComponent));
        return hits;
    }

    private static String categoriesFor(HitKey key, DisplayData data) {
        List<String> labels = new ArrayList<>();
        if (data.truthKeys.contains(key)) {
            labels.add("MC truth");
        }
        if (data.hbMatchedKeys.contains(key)) {
            labels.add("HB matched");
        }
        if (data.tbMatchedKeys.contains(key)) {
            labels.add("TB matched");
        }
        if (data.aiMatchedKeys.contains(key)) {
            labels.add("AI-cluster matched");
        }
        return labels.isEmpty() ? "unclassified" : String.join(", ", labels);
    }

    private static double localX(Hit hit) {
        double phi =
                Math.toRadians((hit.getSector() - 1) * 60.0);

        /*
         * DcBankReader stores the chamber-local X as a sector-rotated global
         * (x,y).  Projecting back on the sector radial axis restores the
         * signed local coordinate.
         */
        return hit.getX() * Math.cos(phi)
                + hit.getY() * Math.sin(phi);
    }

    private static double horizontal(Hit hit, ViewMode mode) {
        return mode == ViewMode.LOCAL_X_Z
                ? hit.getZ()
                : hit.getLayer();
    }

    private static double vertical(Hit hit, ViewMode mode) {
        return mode == ViewMode.LOCAL_X_Z
                ? localX(hit)
                : hit.getComponent();
    }

    private static Bounds boundsFor(
            List<Hit> hits,
            ViewMode mode) {

        Bounds bounds = new Bounds();
        for (Hit hit : hits) {
            bounds.add(
                    horizontal(hit, mode),
                    vertical(hit, mode));
        }
        bounds.ensureValidAndPad();
        return bounds;
    }

    private static final class DisplayData {
        private final int truthTrackId;
        private final int sector;
        private final Map<HitKey, Hit> hitsByKey;
        private final Set<HitKey> truthKeys;
        private final Set<HitKey> hbObjectKeys;
        private final Set<HitKey> hbMatchedKeys;
        private final Set<HitKey> tbObjectKeys;
        private final Set<HitKey> tbMatchedKeys;
        private final Set<HitKey> aiObjectKeys;
        private final Set<HitKey> aiMatchedKeys;

        private boolean showTruth = true;
        private boolean showHb = true;
        private boolean showTb = true;
        private boolean showAi = true;

        private DisplayData(
                int truthTrackId,
                int sector,
                Map<HitKey, Hit> hitsByKey,
                Set<HitKey> truthKeys,
                Set<HitKey> hbObjectKeys,
                Set<HitKey> hbMatchedKeys,
                Set<HitKey> tbObjectKeys,
                Set<HitKey> tbMatchedKeys,
                Set<HitKey> aiObjectKeys,
                Set<HitKey> aiMatchedKeys) {

            this.truthTrackId = truthTrackId;
            this.sector = sector;
            this.hitsByKey = hitsByKey;
            this.truthKeys = truthKeys;
            this.hbObjectKeys = hbObjectKeys;
            this.hbMatchedKeys = hbMatchedKeys;
            this.tbObjectKeys = tbObjectKeys;
            this.tbMatchedKeys = tbMatchedKeys;
            this.aiObjectKeys = aiObjectKeys;
            this.aiMatchedKeys = aiMatchedKeys;
        }

        private static DisplayData empty() {
            return new DisplayData(
                    -1,
                    -1,
                    Collections.emptyMap(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet());
        }
    }

    private static final class ScreenPoint {
        private final int x;
        private final int y;
        private final Hit hit;

        private ScreenPoint(int x, int y, Hit hit) {
            this.x = x;
            this.y = y;
            this.hit = hit;
        }
    }

    private static final class Bounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;

        private void add(double x, double y) {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                return;
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        private void ensureValidAndPad() {
            if (!Double.isFinite(minX)
                    || !Double.isFinite(maxX)
                    || !Double.isFinite(minY)
                    || !Double.isFinite(maxY)) {
                minX = 0.0;
                maxX = 1.0;
                minY = -1.0;
                maxY = 1.0;
                return;
            }

            if (maxX <= minX) {
                minX -= 0.5;
                maxX += 0.5;
            }
            if (maxY <= minY) {
                minY -= 0.5;
                maxY += 0.5;
            }

            double padX = 0.06 * (maxX - minX);
            double padY = 0.09 * (maxY - minY);
            minX -= padX;
            maxX += padX;
            minY -= padY;
            maxY += padY;
        }

        private double dx() {
            return Math.max(1.0e-9, maxX - minX);
        }

        private double dy() {
            return Math.max(1.0e-9, maxY - minY);
        }
    }
}
