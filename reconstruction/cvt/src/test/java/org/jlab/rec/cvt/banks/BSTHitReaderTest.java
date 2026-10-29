package org.jlab.rec.cvt.banks;

import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.jlab.detector.banks.RawBank.OrderType;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.analysis.physics.TestEvent;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.services.CVTEngine;
import org.jlab.utils.groups.IndexedTable;

public class BSTHitReaderTest {

    private static final int NOMINAL = OrderType.NOMINAL.getTypeId();
    private static final int BACKGROUND = OrderType.BGADDED_NOMINAL.getTypeId();
    private static final int REMOVED = OrderType.BGREMOVED.getTypeId();
    private static String previousClas12Dir;

    @BeforeClass
    public static void initializeGeometry() {
        previousClas12Dir = System.getProperty("CLAS12DIR");
        System.setProperty("CLAS12DIR", "../../");
        CVTEngine engine = new CVTEngine();
        assertTrue(engine.init());
        engine.detectorChanged(11);
    }

    @AfterClass
    public static void restoreResourcePath() {
        if (previousClas12Dir == null) {
            System.clearProperty("CLAS12DIR");
        } else {
            System.setProperty("CLAS12DIR", previousClas12Dir);
        }
    }

    @Test
    public void leadingOrderFilteredRowPreservesOriginalIds() {
        assertHitReferences(new int[]{REMOVED, NOMINAL, BACKGROUND},
                new int[]{2, 3, 4}, new int[]{2, 3});
    }

    @Test
    public void interiorOrderFilteredRowPreservesOriginalIds() {
        assertHitReferences(new int[]{NOMINAL, REMOVED, NOMINAL},
                new int[]{2, 3, 4}, new int[]{1, 3});
    }

    @Test
    public void unfilteredRowsKeepTheirIds() {
        assertHitReferences(new int[]{NOMINAL, NOMINAL, BACKGROUND},
                new int[]{2, 3, 4}, new int[]{1, 2, 3});
    }

    @Test
    public void timingRowDoesNotRenumberTheFollowingHit() {
        assertHitReferences(new int[]{NOMINAL, NOMINAL},
                new int[]{-1, 3}, new int[]{2});
    }

    private static void assertHitReferences(int[] orders, int[] adcs, int[] expectedRawRows) {
        // Reuse the repository's event/schema factory, retaining no fixture hits.
        DataEvent event = TestEvent.get(DetectorType.CVT);
        event.removeBanks(event.getBankList());
        assertEquals(0, event.getBankList().length);
        DataBank raw = event.createBank("BST::adc", orders.length);
        IndexedTable status = new IndexedTable(3, "status/I");
        for (int row = 0; row < orders.length; row++) {
            int strip = adcs[row] < 0 ? 1 : 10 + row;
            raw.setByte("sector", row, (byte) 1);
            raw.setByte("layer", row, (byte) 1);
            raw.setShort("component", row, (short) strip);
            raw.setInt("ADC", row, adcs[row]);
            raw.setByte("order", row, (byte) orders[row]);
            raw.setFloat("time", row, 10 + row);
            status.addEntry(1, 1, strip);
            status.setIntValue(0, "status", 1, 1, strip);
        }
        event.appendBank(raw);
        IndexedTable adcStatus = new IndexedTable(3, "adcstatus/I");
        adcStatus.addEntry(0, 0, 0);
        adcStatus.setIntValue(0, "adcstatus", 0, 0, 0);

        HitReader reader = new HitReader();
        reader.fetch_SVTHits(event, -1, -1, status, adcStatus);
        List<Hit> hits = reader.getSVTHits();
        assertEquals(expectedRawRows.length, hits.size());

        // Assemble one synthetic cluster to check the bank writer's references.
        Cluster cluster = new Cluster(DetectorType.BST, BMTType.UNDEFINED, 1, 1, 1);
        cluster.addAll(hits);
        cluster.calc_CentroidParams();
        DataBank hitBank = RecoBankWriter.fillSVTHitBank(event, hits, "BST::Hits");
        DataBank clusterBank = RecoBankWriter.fillSVTClusterBank(event,
                List.of(cluster), "BST::Clusters");
        assertNotNull(hitBank);
        assertNotNull(clusterBank);
        assertEquals(expectedRawRows.length, hitBank.rows());
        assertEquals(1, clusterBank.rows());
        assertEquals(expectedRawRows.length, clusterBank.getShort("size", 0));
        for (int index = 0; index < expectedRawRows.length; index++) {
            int rawRow = expectedRawRows[index] - 1;
            // The physical strip independently identifies its original ADC row.
            assertEquals(raw.getShort("component", rawRow), hits.get(index).getStrip().getStrip());
            assertEquals("Hit ID must reference the original ADC row",
                    expectedRawRows[index], hits.get(index).getId());
            assertEquals(expectedRawRows[index], hitBank.getShort("ID", index));
            assertEquals("Cluster reference must use the original ADC row ID",
                    expectedRawRows[index], clusterBank.getShort("Hit" + (index + 1) + "_ID", 0));
        }
    }
}
