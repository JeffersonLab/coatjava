package org.jlab.rec.cvt.banks;

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

        DataEvent event = TestEvent.get(DetectorType.CVT);
        DataBank bank = RecoBankWriter.fillTrackBank(
                event, Collections.singletonList(track), "CVTRec::Tracks");

        assertEquals(-999.0f, bank.getFloat("cov_d02", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_phi02", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_rho2", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_z02", 0), 0.0f);
        assertEquals(-999.0f, bank.getFloat("cov_tandip2", 0), 0.0f);
    }
}
