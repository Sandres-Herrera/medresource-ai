package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las métricas de comparación.
 */
class MetricTest {

    private static final RunMetrics METRICS =
            new RunMetrics("FIFO", 10, 3, 2, 5, 1, 1, 30.5, 12.0, 250.0, 4, 1, 2, 2);

    @Test
    void readsEachMetricFromTheRun() {
        assertEquals(0.3, Metric.MORTALITY.of(METRICS), 1e-12);
        assertEquals(2.0, Metric.AVOIDABLE_DEATHS.of(METRICS), 0.0);
        assertEquals(30.5, Metric.AVERAGE_WAIT.of(METRICS), 0.0);
        assertEquals(12.0, Metric.URGENT_WAIT.of(METRICS), 0.0);
        assertEquals(250.0, Metric.LIFE_YEARS_SAVED.of(METRICS), 0.0);
        assertEquals(0.25, Metric.MINOR_MORTALITY.of(METRICS), 1e-12);
        assertEquals(1.0, Metric.ELDERLY_MORTALITY.of(METRICS), 1e-12);
    }

    @Test
    void onlyTheLifeYearsAreBetterWhenTheyAreHigher() {
        for (Metric metric : Metric.values()) {
            assertEquals(metric != Metric.LIFE_YEARS_SAVED, metric.isLowerBetter(), metric.name());
        }
    }

    @Test
    void everyMetricHasANameToShow() {
        for (Metric metric : Metric.values()) {
            assertFalse(metric.getDisplayName().isBlank(), metric.name());
        }
        assertTrue(Metric.MORTALITY.getDisplayName().contains("Mortalidad"));
    }

    @Test
    void doesNotReadAMissingRun() {
        assertThrows(IllegalArgumentException.class, () -> Metric.MORTALITY.of(null));
    }
}