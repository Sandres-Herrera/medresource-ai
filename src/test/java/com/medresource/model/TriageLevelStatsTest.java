package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los resultados de un nivel de triage.
 */
class TriageLevelStatsTest {

    @Test
    void calculatesTheMortalityAndTheShareWithinTheTarget() {
        TriageLevelStats stats = new TriageLevelStats(TriageLevel.II, 10, 2, 2, 25.5, 70, 6, 12.0);

        assertEquals(0.2, stats.mortality(), 1e-12);
        assertEquals(0.75, stats.withinTargetRate(), 1e-12);
        assertEquals(70, stats.maxWaitMinutes());
    }

    @Test
    void aLevelWithoutPatientsHasNoRates() {
        TriageLevelStats stats = new TriageLevelStats(TriageLevel.I, 0, 0, 0, Double.NaN, 0, 0, Double.NaN);

        assertTrue(Double.isNaN(stats.mortality()));
        assertTrue(Double.isNaN(stats.withinTargetRate()));
    }

    @Test
    void rejectsIncoherentNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new TriageLevelStats(null, 1, 0, 0, 1.0, 1, 1, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new TriageLevelStats(TriageLevel.III, 1, 2, 0, 1.0, 1, 1, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new TriageLevelStats(TriageLevel.III, 2, 0, 1, 1.0, 1, 2, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new TriageLevelStats(TriageLevel.III, 2, 0, 0, -1.0, 1, 1, 0.0));
    }
}
