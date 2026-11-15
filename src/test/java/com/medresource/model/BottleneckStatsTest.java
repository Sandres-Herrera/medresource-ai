package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de un cuello de botella.
 */
class BottleneckStatsTest {

    @Test
    void measuresHowLongTheResourceWasMissing() {
        BottleneckStats stats = new BottleneckStats(MissingResource.DOCTOR, 600, 0.62, 14, 70, 209, 9, 130);

        assertEquals(140, stats.spanMinutes());
        assertEquals(0.62, stats.share(), 0.0);
    }

    @Test
    void rejectsIncoherentData() {
        assertThrows(IllegalArgumentException.class, () -> new BottleneckStats(null, 1, 0.5, 1, 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new BottleneckStats(MissingResource.BED, 1, 1.5, 1, 0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new BottleneckStats(MissingResource.BED, 1, 0.5, 1, 10, 5, 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new BottleneckStats(MissingResource.BED, 1, 0.5, 1, 10, 20, 1, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new BottleneckStats(MissingResource.BED, -1, 0.5, 1, 0, 0, 1, 0));
    }
}
