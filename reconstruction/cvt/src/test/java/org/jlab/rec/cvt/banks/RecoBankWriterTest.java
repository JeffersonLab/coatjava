package org.jlab.rec.cvt.banks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import org.jlab.analysis.physics.TestEvent;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.Track;
import org.jlab.rec.cvt.trajectory.Helix;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RecoBankWriterTest {

    private static Path repositoryRoot() {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("etc/bankdefs/hipo4"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new IllegalStateException("Cannot locate etc/bankdefs/hipo4 from the test directory");
        }
        return root;
    }

    @Test
    public void seedCovarianceIsNotReportedAsFilteredFitCovariance() {
        double[][] covariance = new double[5][5];
        for (int i = 0; i < covariance.length; i++) {
            covariance[i][i] = i + 1;
        }
        Helix helix = new Helix(0.1, 0.2, 0.01, 0.3, 0.4, 0, 0, covariance);
        Seed seed = new Seed(new ArrayList<>(), helix);
        seed.setId(7);
        seed.setStatus(1);
        Track track = new Track(seed);
        track.setId(1);

        assertFalse(track.isCovFromFilteredFit());

        String originalClas12Dir = System.getProperty("CLAS12DIR");
        DataEvent event;
        try {
            System.setProperty("CLAS12DIR", repositoryRoot().toString());
            event = TestEvent.get(DetectorType.CVT);
        } finally {
            if (originalClas12Dir == null) {
                System.clearProperty("CLAS12DIR");
            } else {
                System.setProperty("CLAS12DIR", originalClas12Dir);
            }
        }
        DataBank bank = RecoBankWriter.fillTrackBank(
                event, Collections.singletonList(track), "CVTRec::Tracks");

        assertEquals(-999.0f, bank.getFloat("cov_d02", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_phi02", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_rho2", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_z02", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_tandip2", 0), 0.0f);
    }
}
