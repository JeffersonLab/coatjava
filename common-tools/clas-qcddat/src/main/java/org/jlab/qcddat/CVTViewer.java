package org.jlab.qcddat;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author veronique
 */

public class CVTViewer extends Application {

    // ---------- config ----------
    private static String inputFile;
    private static String bankName;

    public static void configure(String input) {
        inputFile = input;
        bankName = "CVT::QCDDATHit";
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java org.jlab.qcddat.CVTViewer <input.hipo>");
            System.exit(1);
        }
        configure(args[0]);
        launch(args);
    }

    // ---------- bounded cache ----------
    private static final int MAX_CACHE_SIZE = 200;

    private HipoDataSource reader;
    private final List<List<HitPoint>> eventPointCache = new ArrayList<>();
    private final List<Integer> eventRowCountCache = new ArrayList<>();

    // global event number of first cached event
    private int cacheStartEventNumber = 0;

    // current global event number
    private int currentEventNumber = -1;

    // next global event number expected from persistent reader
    private int nextUnreadEventNumber = 0;

    private int totalEvents = 0;

    // ---------- gui ----------
    private CheckBox showLoc1;
    private CheckBox showLoc2;
    private CheckBox showLoc3;
    private CheckBox showSVT;
    private CheckBox showBMTC;
    private CheckBox showBMTZ;

    private Label hoverLabel;
    private Label infoLabel;
    private Label countsLabel;

    // ---------- 3D ----------
    private final Group world = new Group();
    private final Group pointsGroup = new Group();

    private final Rotate rotateX = new Rotate(20, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(-35, Rotate.Y_AXIS);

    private double anchorX;
    private double anchorY;
    private double anchorAngleX;
    private double anchorAngleY;

    private PerspectiveCamera camera;

    private static final double DRAW_SCALE = 5.0;
    private static final double POINT_RADIUS = 1.8;

    private enum DetectorKind {
        SVT,
        BMT_C,
        BMT_Z,
        UNKNOWN
    }

    private static class HitPoint {
        final double x;
        final double y;
        final double z;
        final DetectorKind kind;
        final int pointloc;
        final int mctrue;

        HitPoint(double x, double y, double z, DetectorKind kind, int loc, int mct) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.pointloc = loc;
            this.mctrue = mct;
        }
    }

    @Override
    public void start(Stage stage) {
        if (inputFile == null || bankName == null) {
            throw new IllegalStateException("CVTViewer.configure(inputFile) must be called before launch.");
        }

        initializeFile(inputFile);
        buildWorld();

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(100000);
        camera.setTranslateZ(-1400);

        SubScene subScene = new SubScene(world, 1200, 850, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.rgb(18, 18, 22));
        subScene.setCamera(camera);

        enableMouseControls(subScene);

        BorderPane root = new BorderPane();
        root.setCenter(subScene);
        root.setTop(buildTopBar());
        root.setRight(buildLegendPane());

        Scene scene = new Scene(root, 1450, 900, true);

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.N) {
                nextEvent();
            } else if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.P) {
                previousEvent();
            } else if (e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD || (e.getCode() == KeyCode.EQUALS && e.isShiftDown())) {
                camera.setTranslateZ(camera.getTranslateZ() + 50);
            } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                camera.setTranslateZ(camera.getTranslateZ() - 50);
            } else if (e.getCode() == KeyCode.R) {
                resetView();
            }
        });

        stage.setTitle("COATJAVA Bank 3D Event Browser");
        stage.setScene(scene);
        stage.show();

        if (totalEvents == 0) {
            infoLabel.setText("No events found in file: " + inputFile);
            countsLabel.setText("");
        } else if (loadNextEventIntoCache()) {
            renderCachedEvent();
        } else {
            infoLabel.setText("Could not load first event");
            countsLabel.setText("");
        }
    }

    private void initializeFile(String fileName) {
        File f = new File(fileName);
        if (!f.exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + fileName);
        }

        HipoDataSource counter = new HipoDataSource();
        counter.open(fileName);
        totalEvents = 0;
        while (counter.hasEvent()) {
            counter.getNextEvent();
            totalEvents++;
        }
        counter.close();

        reader = new HipoDataSource();
        reader.open(fileName);

        eventPointCache.clear();
        eventRowCountCache.clear();

        cacheStartEventNumber = 0;
        currentEventNumber = -1;
        nextUnreadEventNumber = 0;

        System.out.printf("Initialized file %s with %d events%n", fileName, totalEvents);
    }

    private boolean loadNextEventIntoCache() {
        if (reader == null || !reader.hasEvent()) {
            return false;
        }

        DataEvent event = reader.getNextEvent();
        int thisEventNumber = nextUnreadEventNumber;
        nextUnreadEventNumber++;

        if (!event.hasBank(bankName)) {
            return false;
        }

        DataBank bank = event.getBank(bankName);
        List<HitPoint> points = extractAllThreePoints(bank);

        eventPointCache.add(points);
        eventRowCountCache.add(bank.rows());

        if (eventPointCache.size() > MAX_CACHE_SIZE) {
            eventPointCache.remove(0);
            eventRowCountCache.remove(0);
            cacheStartEventNumber++;
        }

        currentEventNumber = thisEventNumber;
        return true;
    }

    private boolean isCurrentEventCached() {
        return currentEventNumber >= cacheStartEventNumber
                && currentEventNumber < cacheStartEventNumber + eventPointCache.size();
    }

    private int currentCacheIndex() {
        return currentEventNumber - cacheStartEventNumber;
    }

    private void renderCachedEvent() {
        pointsGroup.getChildren().clear();

        if (!isCurrentEventCached()) {
            infoLabel.setText(String.format(
                    "Event %d is no longer in cache. Cache window: [%d .. %d]",
                    currentEventNumber + 1,
                    cacheStartEventNumber + 1,
                    cacheStartEventNumber + eventPointCache.size()
            ));
            countsLabel.setText("");
            hoverLabel.setText("Hover over a point to see coordinates");
            return;
        }

        int cacheIndex = currentCacheIndex();
        List<HitPoint> points = eventPointCache.get(cacheIndex);
        int rowCount = eventRowCountCache.get(cacheIndex);

        int nSVT = 0;
        int nBMTC = 0;
        int nBMTZ = 0;
        int nLoc1 = 0;
        int nLoc2 = 0;
        int nLoc3 = 0;
        int nMcTrue0 = 0;
        int nOther = 0;

        for (HitPoint p : points) {
            if (!isVisible(p.kind) || !isVisibleLoc(p.pointloc)) {
                continue;
            }

            Node marker = makeMarker(p);
            pointsGroup.getChildren().add(marker);

            switch (p.kind) {
                case SVT -> nSVT++;
                case BMT_C -> nBMTC++;
                case BMT_Z -> nBMTZ++;
                default -> { }
            }

            switch (p.pointloc) {
                case 1 -> nLoc1++;
                case 2 -> nLoc2++;
                case 3 -> nLoc3++;
                default -> { }
            }

            if (p.mctrue == 0) {
                nMcTrue0++;
            } else {
                nOther++;
            }
        }

        infoLabel.setText(String.format(
                "File: %s   Bank: %s   Event %d / %d   Rows: %d   Cache: [%d .. %d]",
                new File(inputFile).getName(),
                bankName,
                currentEventNumber + 1,
                totalEvents,
                rowCount,
                cacheStartEventNumber + 1,
                cacheStartEventNumber + eventPointCache.size()
        ));

        countsLabel.setText(String.format(
                "SVT=%d   BMT_C=%d   BMT_Z=%d   loc1=%d   loc2=%d   loc3=%d   mctrue0=%d   other=%d",
                nSVT, nBMTC, nBMTZ, nLoc1, nLoc2, nLoc3, nMcTrue0, nOther
        ));
    }

    private void buildWorld() {
        world.getTransforms().addAll(rotateX, rotateY);

        Group axes = new Group(
                makeAxis(300, 1.0, 1.0, Color.RED),
                makeAxis(1.0, 300, 1.0, Color.LIME),
                makeAxis(1.0, 1.0, 300, Color.DEEPSKYBLUE)
        );

        world.getChildren().add(axes);
        world.getChildren().add(pointsGroup);
    }

    private Node makeAxis(double sx, double sy, double sz, Color color) {
        Box box = new Box(sx, sy, sz);
        box.setMaterial(new PhongMaterial(color));
        return box;
    }

    private HBox buildTopBar() {
        Button prev = new Button("Previous Event");
        Button next = new Button("Next Event");
        Button reset = new Button("Reset View");

        prev.setOnAction(e -> previousEvent());
        next.setOnAction(e -> nextEvent());
        reset.setOnAction(e -> resetView());

        infoLabel = new Label("Loading...");
        countsLabel = new Label("");
        hoverLabel = new Label("Hover over a point to see coordinates");

        infoLabel.setTextFill(Color.BLACK);
        countsLabel.setTextFill(Color.BLACK);
        hoverLabel.setTextFill(Color.DARKBLUE);

        HBox controls = new HBox(
                10,
                prev, next, reset,
                new Separator(),
                infoLabel,
                new Separator(),
                countsLabel,
                new Separator(),
                hoverLabel
        );
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-background-color: #e9edf2;");
        return controls;
    }

    private VBox buildLegendPane() {
        Label legendTitle = new Label("Detectors");
        legendTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        showSVT = new CheckBox("SVT");
        showSVT.setSelected(true);
        showBMTC = new CheckBox("BMT C");
        showBMTC.setSelected(true);
        showBMTZ = new CheckBox("BMT Z");
        showBMTZ.setSelected(true);

        showSVT.setOnAction(e -> renderCachedEvent());
        showBMTC.setOnAction(e -> renderCachedEvent());
        showBMTZ.setOnAction(e -> renderCachedEvent());

        Label svtColor = coloredLabel("Magenta");
        svtColor.setTextFill(Color.MAGENTA);

        Label cColor = coloredLabel("LimeGreen");
        cColor.setTextFill(Color.LIMEGREEN);

        Label zColor = coloredLabel("Cyan");
        zColor.setTextFill(Color.CYAN);

        Label locTitle = new Label("Point location");
        locTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        showLoc1 = new CheckBox("loc 1 = origin");
        showLoc1.setSelected(true);
        showLoc2 = new CheckBox("loc 2 = midpoint");
        showLoc2.setSelected(true);
        showLoc3 = new CheckBox("loc 3 = end");
        showLoc3.setSelected(true);

        showLoc1.setOnAction(e -> renderCachedEvent());
        showLoc2.setOnAction(e -> renderCachedEvent());
        showLoc3.setOnAction(e -> renderCachedEvent());

        Label help = new Label(
                "Mouse drag: rotate\n" +
                "Scroll / - / +: zoom\n" +
                "N / Right arrow: next\n" +
                "P / Left arrow: previous\n" +
                "R: reset view"
        );
        help.setWrapText(true);

        VBox box = new VBox(
                8,
                legendTitle,
                showSVT, svtColor,
                showBMTC, cColor,
                showBMTZ, zColor,
                new Separator(),
                locTitle,
                showLoc1,
                showLoc2,
                showLoc3,
                new Separator(),
                help
        );
        box.setPadding(new Insets(12));
        box.setPrefWidth(220);
        box.setStyle("-fx-background-color: #f7f7f7;");
        return box;
    }

    private Label coloredLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-weight: bold;");
        return lbl;
    }

    private void enableMouseControls(SubScene scene) {
        scene.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
        });

        scene.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            rotateX.setAngle(anchorAngleX - (anchorY - e.getSceneY()) * 0.35);
            rotateY.setAngle(anchorAngleY + (anchorX - e.getSceneX()) * 0.35);
        });

        scene.addEventHandler(ScrollEvent.SCROLL, e -> {
            camera.setTranslateZ(camera.getTranslateZ() + e.getDeltaY() * 0.6);
        });
    }

    private void resetView() {
        rotateX.setAngle(20);
        rotateY.setAngle(-35);
        if (camera != null) {
            camera.setTranslateZ(-1400);
        }
    }

    private void previousEvent() {
        if (totalEvents == 0) {
            return;
        }
        if (currentEventNumber > cacheStartEventNumber) {
            currentEventNumber--;
            renderCachedEvent();
        } else if (currentEventNumber > 0) {
            infoLabel.setText(String.format(
                    "Cannot go back farther: event %d is outside the cache window [%d .. %d]",
                    currentEventNumber,
                    cacheStartEventNumber + 1,
                    cacheStartEventNumber + eventPointCache.size()
            ));
        }
    }

    private void nextEvent() {
        if (totalEvents == 0) {
            return;
        }

        if (currentEventNumber + 1 < nextUnreadEventNumber) {
            currentEventNumber++;
            renderCachedEvent();
            return;
        }

        if (loadNextEventIntoCache()) {
            renderCachedEvent();
        }
    }

    private Node makeMarker(HitPoint p) {
        PhongMaterial material = new PhongMaterial(colorFor(p.kind));

        Node marker;
        if (p.mctrue == 0) {
            Box b = new Box(2.8 * POINT_RADIUS, 2.8 * POINT_RADIUS, 2.8 * POINT_RADIUS);
            b.setMaterial(material);
            marker = b;
        } else {
            Sphere s = new Sphere(POINT_RADIUS);
            s.setMaterial(material);
            marker = s;
        }

        marker.setTranslateX(p.x * DRAW_SCALE);
        marker.setTranslateY(-p.y * DRAW_SCALE);
        marker.setTranslateZ(p.z * DRAW_SCALE);

        marker.setOnMouseEntered(e -> hoverLabel.setText(String.format(
                "%s  loc=%d  mctrue=%d  (x, y, z) = (%.4f, %.4f, %.4f)",
                detectorName(p.kind), p.pointloc, p.mctrue, p.x, p.y, p.z
        )));

        marker.setOnMouseExited(e -> hoverLabel.setText("Hover over a point to see coordinates"));

        return marker;
    }

    private String detectorName(DetectorKind kind) {
        return switch (kind) {
            case SVT -> "SVT";
            case BMT_C -> "BMT_C";
            case BMT_Z -> "BMT_Z";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private boolean isVisible(DetectorKind kind) {
        return switch (kind) {
            case SVT -> showSVT.isSelected();
            case BMT_C -> showBMTC.isSelected();
            case BMT_Z -> showBMTZ.isSelected();
            default -> true;
        };
    }

    private boolean isVisibleLoc(int loc) {
        return switch (loc) {
            case 1 -> showLoc1.isSelected();
            case 2 -> showLoc2.isSelected();
            case 3 -> showLoc3.isSelected();
            default -> true;
        };
    }

    private List<HitPoint> extractAllThreePoints(DataBank bank) {
        List<HitPoint> out = new ArrayList<>();

        for (int i = 0; i < bank.rows(); i++) {
            int layer = bank.getByte("layer", i);
            DetectorKind kind = detectorKindFromLayer(layer);
            int mct = bank.getByte("mctrue", i);

            out.add(new HitPoint(
                    bank.getFloat("x1", i),
                    bank.getFloat("y1", i),
                    bank.getFloat("z1", i),
                    kind, 1, mct
            ));

            out.add(new HitPoint(
                    bank.getFloat("x2", i),
                    bank.getFloat("y2", i),
                    bank.getFloat("z2", i),
                    kind, 2, mct
            ));

            out.add(new HitPoint(
                    bank.getFloat("x3", i),
                    bank.getFloat("y3", i),
                    bank.getFloat("z3", i),
                    kind, 3, mct
            ));
        }

        return out;
    }

    private DetectorKind detectorKindFromLayer(int layer) {
        if (layer >= 1 && layer <= 6) {
            return DetectorKind.SVT;
        }
        if (layer >= 7 && layer <= 12) {
            return isBmtCLayer(layer) ? DetectorKind.BMT_C : DetectorKind.BMT_Z;
        }
        return DetectorKind.UNKNOWN;
    }

    private boolean isBmtCLayer(int layer) {
        return (layer == 7 || layer == 10 || layer == 12);
    }

    private Color colorFor(DetectorKind kind) {
        return switch (kind) {
            case SVT -> Color.MAGENTA;
            case BMT_C -> Color.LIMEGREEN;
            case BMT_Z -> Color.CYAN;
            case UNKNOWN -> Color.WHITE;
        };
    }
}
