/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package org.jlab.clas.tracking.validation.io.detector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.TrackingObjectType;
import org.jlab.clas.tracking.validation.data.Cluster;
import org.jlab.clas.tracking.validation.data.Cross;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.Seed;
import org.jlab.clas.tracking.validation.data.Track;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.data.ValidationObject.ObjectKey;
import org.jlab.clas.tracking.validation.io.TrackingBankReader;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/** 
 * Reads the nominal CVT reconstruction banks into the generic validation model. 
 * 
 * @author veronique
 */
public final class CvtBankReader implements TrackingBankReader {

    private static final String ALGORITHM = "CVT";

    private static final String BST_HIT_BANK = "BSTRec::Hits";
    private static final String BMT_HIT_BANK = "BMTRec::Hits";
    private static final String BST_CLUSTER_BANK = "BSTRec::Clusters";
    private static final String BMT_CLUSTER_BANK = "BMTRec::Clusters";
    private static final String BST_CROSS_BANK = "BSTRec::Crosses";
    private static final String BMT_CROSS_BANK = "BMTRec::Crosses";
    private static final String SEED_BANK = "CVTRec::Seeds";
    private static final String TRACK_BANK = "CVTRec::Tracks";

    private static final int BST = DetectorType.BST.getDetectorId();
    private static final int BMT = DetectorType.BMT.getDetectorId();

    @Override
    public boolean isApplicable(DataEvent event) {
        return event != null && (
                event.hasBank(BST_HIT_BANK)
                || event.hasBank(BMT_HIT_BANK)
                || event.hasBank(BST_CLUSTER_BANK)
                || event.hasBank(BMT_CLUSTER_BANK)
                || event.hasBank(BST_CROSS_BANK)
                || event.hasBank(BMT_CROSS_BANK)
                || event.hasBank(SEED_BANK)
                || event.hasBank(TRACK_BANK));
    }

    @Override
    public void readHits(DataEvent event, ValidationEvent output) {
        readHitBank(event, output, BST_HIT_BANK, BST);
        readHitBank(event, output, BMT_HIT_BANK, BMT);
    }

    @Override
    public void readClusters(DataEvent event, ValidationEvent output) {
        readClusterBank(event, output, BST_CLUSTER_BANK, BST);
        readClusterBank(event, output, BMT_CLUSTER_BANK, BMT);
    }

    @Override
    public void readCrosses(DataEvent event, ValidationEvent output) {
        readCrossBank(event, output, BST_CROSS_BANK, BST);
        readCrossBank(event, output, BMT_CROSS_BANK, BMT);
    }

    @Override
    public void readSeeds(DataEvent event, ValidationEvent output) {
        if (!event.hasBank(SEED_BANK)) {
            return;
        }

        DataBank bank = event.getBank(SEED_BANK);
        for (int row = 0; row < bank.rows(); row++) {
            int id = bank.getShort("ID", row);
            int charge = getByte(bank, "q", row, 0);
            double p = getFloat(bank, "p", row, 0.0);
            double phi0 = getFloat(bank, "phi0", row, 0.0);
            double tanDip = getFloat(bank, "tandip", row, 0.0);
            double[] momentum = momentumFromHelix(p, phi0, tanDip);

            ResolvedCvtContent content = resolveCrossContent(bank, row, output);

            output.addSeed(new Seed(
                    id,
                    ALGORITHM,
                    BST,
                    cvtScope(),
                    content.crossIds,
                    content.clusterIds,
                    content.hitKeys,
                    momentum[0],
                    momentum[1],
                    momentum[2],
                    charge));
        }
    }

    @Override
    public void readTracks(DataEvent event, ValidationEvent output) {
        if (!event.hasBank(TRACK_BANK)) {
            return;
        }

        DataBank bank = event.getBank(TRACK_BANK);
        for (int row = 0; row < bank.rows(); row++) {
            int id = bank.getShort("ID", row);
            int seedId = getShort(bank, "seedID", row, -1);
            int charge = getByte(bank, "q", row, 0);
            int status = getShort(bank, "status", row, 0);
            int ndf = getShort(bank, "ndf", row, 0);
            double chi2 = getFloat(bank, "chi2", row, Double.NaN);

            double p = getFloat(bank, "p", row, 0.0);
            double phi0 = getFloat(bank, "phi0", row, 0.0);
            double tanDip = getFloat(bank, "tandip", row, 0.0);
            double[] momentum = momentumFromHelix(p, phi0, tanDip);

            double d0 = getFloat(bank, "d0", row, 0.0);
            double xb = getFloat(bank, "xb", row, 0.0);
            double yb = getFloat(bank, "yb", row, 0.0);
            double z0 = getFloat(bank, "z0", row, 0.0);
            double vx = xb - d0 * Math.sin(phi0);
            double vy = yb + d0 * Math.cos(phi0);

            ResolvedCvtContent content = resolveCrossContent(bank, row, output);
            for (Hit hit : output.getHits()) {
                if ((hit.getDetector() == BST || hit.getDetector() == BMT)
                        && hit.getTrackId() == id) {
                    content.hitKeys.add(hit.key());
                }
            }
            content.deduplicate();

            output.addTrack(new Track(
                    id,
                    ALGORITHM,
                    seedId,
                    BST,
                    cvtScope(),
                    content.crossIds,
                    content.clusterIds,
                    content.hitKeys,
                    momentum[0],
                    momentum[1],
                    momentum[2],
                    vx,
                    vy,
                    z0,
                    chi2,
                    ndf,
                    charge,
                    status));
        }
    }

    private static void readHitBank(
            DataEvent event,
            ValidationEvent output,
            String bankName,
            int detector) {

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank = event.getBank(bankName);
        for (int row = 0; row < bank.rows(); row++) {
            int id = bank.getShort("ID", row);
            int sector = bank.getByte("sector", row);
            int layer = bank.getByte("layer", row);
            int strip = bank.getShort("strip", row);
            int clusterId = getShort(bank, "clusterID", row, -1);
            int trackId = getShort(bank, "trkID", row, -1);
            int truthTrackId = output.getTruthTrackId(detector, id);
            int truthHitId = truthTrackId > 0 ? id : -1;

            output.addHit(new Hit(
                    id,
                    detector,
                    sector,
                    layer,
                    strip,
                    clusterId,
                    trackId,
                    truthTrackId,
                    truthHitId,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    false,
                    Double.NaN));
        }
    }

    private static void readClusterBank(
            DataEvent event,
            ValidationEvent output,
            String bankName,
            int detector) {

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank = event.getBank(bankName);
        for (int row = 0; row < bank.rows(); row++) {
            int id = bank.getShort("ID", row);
            int sector = bank.getByte("sector", row);
            int layer = bank.getByte("layer", row);

            List<HitKey> hitKeys = new ArrayList<>();
            for (Hit hit : output.getHits()) {
                if (hit.getDetector() == detector && hit.getClusterId() == id) {
                    hitKeys.add(hit.key());
                }
            }

            if (hitKeys.isEmpty()) {
                for (int i = 1; i <= 5; i++) {
                    int hitId = getShort(bank, "Hit" + i + "_ID", row, -1);
                    Hit hit = findHit(output, detector, hitId);
                    if (hit != null) {
                        hitKeys.add(hit.key());
                    }
                }
            }

            double cx = getFloat(bank, "cx", row, Double.NaN);
            double cy = getFloat(bank, "cy", row, Double.NaN);
            double cz = getFloat(bank, "cz", row, Double.NaN);
            for (HitKey key : hitKeys) {
                Hit hit = output.getHit(key);
                if (hit != null) {
                    output.addHit(copyWithPosition(hit, cx, cy, cz));
                }
            }

            output.addCluster(new Cluster(
                    id,
                    ALGORITHM,
                    detector,
                    sector,
                    layer,
                    hitKeys,
                    getFloat(bank, "centroid", row, Double.NaN),
                    getFloat(bank, "ETot", row, 0.0),
                    getFloat(bank, "time", row, Double.NaN)));
        }
    }

    private static void readCrossBank(
            DataEvent event,
            ValidationEvent output,
            String bankName,
            int detector) {

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank = event.getBank(bankName);
        for (int row = 0; row < bank.rows(); row++) {
            int id = bank.getShort("ID", row);
            int sector = bank.getByte("sector", row);
            int region = bank.getByte("region", row);

            List<Integer> clusterIds = positiveIds(
                    getShort(bank, "Cluster1_ID", row, -1),
                    getShort(bank, "Cluster2_ID", row, -1));

            output.addCross(new Cross(
                    id,
                    ALGORITHM,
                    detector,
                    sector,
                    region,
                    clusterIds,
                    output.resolveHitKeysFromClusters(ALGORITHM, detector, clusterIds),
                    getFloat(bank, "x", row, Double.NaN),
                    getFloat(bank, "y", row, Double.NaN),
                    getFloat(bank, "z", row, Double.NaN)));
        }
    }

    private static ResolvedCvtContent resolveCrossContent(
            DataBank bank,
            int row,
            ValidationEvent output) {

        ResolvedCvtContent content = new ResolvedCvtContent();
        for (int slot = 1; slot <= 9; slot++) {
            int crossId = getShort(bank, "Cross" + slot + "_ID", row, -1);
            if (crossId < 0) {
                continue;
            }

            int detector = slot <= 3 ? BST : BMT;
            Cross cross = output.getCross(new ObjectKey(
                    ALGORITHM,
                    detector,
                    TrackingObjectType.CROSS,
                    crossId));
            if (cross == null) {
                continue;
            }

            content.crossIds.add(crossId);
            content.clusterIds.addAll(cross.getClusterIds());
            content.hitKeys.addAll(cross.getHitKeys());
        }
        content.deduplicate();
        return content;
    }

    private static Set<Integer> cvtScope() {
        Set<Integer> scope = new LinkedHashSet<>();
        scope.add(BST);
        scope.add(BMT);
        return scope;
    }

    private static double[] momentumFromHelix(double p, double phi0, double tanDip) {
        double pt = p / Math.sqrt(1.0 + tanDip * tanDip);
        return new double[]{
            pt * Math.cos(phi0),
            pt * Math.sin(phi0),
            pt * tanDip
        };
    }

    private static Hit copyWithPosition(Hit hit, double x, double y, double z) {
        return new Hit(
                hit.getId(),
                hit.getDetector(),
                hit.getSector(),
                hit.getLayer(),
                hit.getComponent(),
                hit.getClusterId(),
                hit.getTrackId(),
                hit.getTruthTrackId(),
                hit.getTruthHitId(),
                x,
                y,
                z,
                hit.isAiSelected(),
                hit.getAiScore());
    }

    private static Hit findHit(ValidationEvent event, int detector, int hitId) {
        if (hitId < 0) {
            return null;
        }
        for (Hit hit : event.getHits()) {
            if (hit.getDetector() == detector && hit.getId() == hitId) {
                return hit;
            }
        }
        return null;
    }

    private static List<Integer> positiveIds(int... ids) {
        List<Integer> result = new ArrayList<>();
        for (int id : ids) {
            if (id >= 0) {
                result.add(id);
            }
        }
        return result;
    }

    private static boolean hasColumn(DataBank bank, String name) {
        return bank.getDescriptor() != null && bank.getDescriptor().hasEntry(name);
    }

    private static int getByte(DataBank bank, String name, int row, int defaultValue) {
        return hasColumn(bank, name) ? bank.getByte(name, row) : defaultValue;
    }

    private static int getShort(DataBank bank, String name, int row, int defaultValue) {
        return hasColumn(bank, name) ? bank.getShort(name, row) : defaultValue;
    }

    private static double getFloat(DataBank bank, String name, int row, double defaultValue) {
        return hasColumn(bank, name) ? bank.getFloat(name, row) : defaultValue;
    }

    private static final class ResolvedCvtContent {
        private List<Integer> crossIds = new ArrayList<>();
        private List<Integer> clusterIds = new ArrayList<>();
        private List<HitKey> hitKeys = new ArrayList<>();

        private void deduplicate() {
            crossIds = new ArrayList<>(new LinkedHashSet<>(crossIds));
            clusterIds = new ArrayList<>(new LinkedHashSet<>(clusterIds));
            hitKeys = new ArrayList<>(new LinkedHashSet<>(hitKeys));
        }
    }
}
