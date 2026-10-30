package org.jlab.rec.cvt.banks;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HitReaderTest {

    @Test
    public void rejectingStatusTakesPrecedenceOverHvAnnotation() {
        assertEquals(2, HitReader.mergeBmtStatus(2, 6));
        assertEquals(3, HitReader.mergeBmtStatus(3, 5));
        assertEquals(4, HitReader.mergeBmtStatus(0, 4));
        assertEquals(4, HitReader.mergeBmtStatus(6, 4));
    }

    @Test
    public void nonRejectingHvStatusIsKeptForOtherwiseGoodHit() {
        assertEquals(0, HitReader.mergeBmtStatus(0, 0));
        assertEquals(5, HitReader.mergeBmtStatus(0, 5));
        assertEquals(6, HitReader.mergeBmtStatus(0, 6));
        assertEquals(5, HitReader.mergeBmtStatus(5, 6));
    }
}
