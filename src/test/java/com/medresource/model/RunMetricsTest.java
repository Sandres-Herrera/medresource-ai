package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las métricas de una ejecución.
 */
class RunMetricsTest {

    private static RunMetrics sample() {
        return new RunMetrics("FIFO", 10, 3, 2, 5, 1, 1, 30.5, 12.0, 250.0, 4, 1, 2, 2);
    }

    @Test
    void keepsTheData() {
        RunMetrics metrics = sample();

        assertEquals("FIFO", metrics.strategyName());
        assertEquals(10, metrics.patients());
        assertEquals(3, metrics.deaths());
        assertEquals(2, metrics.avoidableDeaths());
        assertEquals(5, metrics.survivors());
        assertEquals(1, metrics.referred());
        assertEquals(1, metrics.unattended());
        assertEquals(30.5, metrics.averageWaitMinutes(), 0.0);
        assertEquals(12.0, metrics.urgentAverageWaitMinutes(), 0.0);
        assertEquals(250.0, metrics.lifeYearsSaved(), 0.0);
    }

    @Test
    void calculatesTheMortalityOfEachGroup() {
        RunMetrics metrics = sample();

        assertEquals(0.3, metrics.mortalityRate(), 1e-12);
        assertEquals(0.25, metrics.minorMortality(), 1e-12);
        assertEquals(1.0, metrics.elderlyMortality(), 1e-12);
    }

    @Test
    void theMortalityOfAnEmptyGroupIsNotANumber() {
        RunMetrics metrics = new RunMetrics("FIFO", 5, 1, 1, 4, 0, 0, 10.0, Double.NaN, 100.0, 0, 0, 0, 0);

        assertTrue(Double.isNaN(metrics.minorMortality()));
        assertTrue(Double.isNaN(metrics.elderlyMortality()));
        assertTrue(Double.isNaN(metrics.urgentAverageWaitMinutes()));
        assertEquals(0.2, metrics.mortalityRate(), 1e-12);
    }

    @Test
    void theMortalityWithoutPatientsIsZero() {
        RunMetrics metrics = new RunMetrics("FIFO", 0, 0, 0, 0, 0, 0, Double.NaN, Double.NaN, 0.0, 0, 0, 0, 0);

        assertEquals(0.0, metrics.mortalityRate(), 0.0);
    }

    @Test
    void requiresTheNameOfTheStrategy() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics(null, 10, 3, 2, 5, 1, 1, 30.5, 12.0, 250.0, 4, 1, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics(" ", 10, 3, 2, 5, 1, 1, 30.5, 12.0, 250.0, 4, 1, 2, 2));
    }

    @Test
    void rejectsNegativeNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", -1, 0, 0, 0, 0, 0, 1.0, 1.0, 0.0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 3, 2, 5, 1, 1, -0.5, 12.0, 250.0, 4, 1, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 3, 2, 5, 1, 1, 30.5, -12.0, 250.0, 4, 1, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 3, 2, 5, 1, 1, 30.5, 12.0, -1.0, 4, 1, 2, 2));
    }

    @Test
    void rejectsAPartLargerThanItsTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 3, 4, 5, 1, 1, 30.5, 12.0, 250.0, 4, 1, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 3, 2, 5, 1, 1, 30.5, 12.0, 250.0, 4, 5, 2, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 3, 2, 5, 1, 1, 30.5, 12.0, 250.0, 4, 1, 2, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 1, 1, 5, 1, 1, 30.5, 12.0, 250.0, 4, 2, 2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 1, 1, 5, 1, 1, 30.5, 12.0, 250.0, 4, 0, 2, 2));
    }

    @Test
    void rejectsOutcomesThatAddUpToMoreThanThePatients() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetrics("FIFO", 10, 3, 2, 5, 1, 2, 30.5, 12.0, 250.0, 4, 1, 2, 2));
    }
}