package com.medresource.services.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del avance de un análisis largo.
 */
class WhatIfProgressTest {

    @Test
    void estimatesTheTimeLeftFromTheTimeSoFar() {
        WhatIfProgress progress = new WhatIfProgress(30, 120, 6_000);

        assertEquals(0.25, progress.fraction(), 1e-12);
        assertEquals(18_000, progress.estimatedRemainingMillis());
        assertFalse(progress.isFinished());
    }

    @Test
    void withoutAnyRunThereIsNothingToEstimate() {
        assertEquals(-1, new WhatIfProgress(0, 120, 300).estimatedRemainingMillis());
    }

    @Test
    void whenEverythingIsDoneNothingIsLeft() {
        WhatIfProgress progress = new WhatIfProgress(120, 120, 24_000);

        assertTrue(progress.isFinished());
        assertEquals(0, progress.estimatedRemainingMillis());
    }

    @Test
    void rejectsIncoherentNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new WhatIfProgress(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WhatIfProgress(5, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> new WhatIfProgress(1, 4, -1));
    }
}
