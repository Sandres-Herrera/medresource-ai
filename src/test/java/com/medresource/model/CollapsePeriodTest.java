package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de un periodo de colapso.
 */
class CollapsePeriodTest {

    @Test
    void measuresHowLongTheCollapseLasted() {
        CollapsePeriod collapse = new CollapsePeriod(48, 315, 12, 120);

        assertEquals(268, collapse.durationMinutes());
        assertEquals(12, collapse.peakOverTarget());
    }

    @Test
    void rejectsIncoherentData() {
        assertThrows(IllegalArgumentException.class, () -> new CollapsePeriod(50, 40, 1, 45));
        assertThrows(IllegalArgumentException.class, () -> new CollapsePeriod(10, 40, 1, 45));
        assertThrows(IllegalArgumentException.class, () -> new CollapsePeriod(10, 40, 0, 20));
    }
}
