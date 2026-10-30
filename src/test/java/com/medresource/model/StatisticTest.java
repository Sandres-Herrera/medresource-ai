package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del resumen estadístico.
 */
class StatisticTest {

    @Test
    void summarizesAListOfNumbers() {
        Statistic statistic = Statistic.of(2, 4, 4, 4, 5, 5, 7, 9);

        assertEquals(8, statistic.count());
        assertEquals(5.0, statistic.mean(), 1e-12);
        assertEquals(Math.sqrt(32.0 / 7.0), statistic.standardDeviation(), 1e-12);
        assertEquals(2.0, statistic.min(), 0.0);
        assertEquals(9.0, statistic.max(), 0.0);
    }

    @Test
    void ignoresTheValuesThatAreNotANumber() {
        Statistic statistic = Statistic.of(1.0, Double.NaN, 3.0);

        assertEquals(2, statistic.count());
        assertEquals(2.0, statistic.mean(), 1e-12);
    }

    @Test
    void summarizesNothingAsZeros() {
        Statistic empty = Statistic.of();
        Statistic onlyNaN = Statistic.of(Double.NaN);

        assertEquals(new Statistic(0, 0, 0, 0, 0), empty);
        assertEquals(empty, onlyNaN);
    }

    @Test
    void oneNumberHasNoDeviation() {
        Statistic statistic = Statistic.of(7.5);

        assertEquals(1, statistic.count());
        assertEquals(7.5, statistic.mean(), 0.0);
        assertEquals(0.0, statistic.standardDeviation(), 0.0);
        assertEquals(7.5, statistic.min(), 0.0);
        assertEquals(7.5, statistic.max(), 0.0);
    }

    @Test
    void doesNotSummarizeAMissingList() {
        assertThrows(IllegalArgumentException.class, () -> Statistic.of((double[]) null));
    }

    @Test
    void theConfidenceIntervalIsOneNinetySixDeviationsOverTheRootOfTheCount() {
        Statistic statistic = new Statistic(100, 5.0, 2.0, 1.0, 9.0);

        assertEquals(1.96 * 2.0 / 10.0, statistic.confidenceHalfWidth(), 1e-12);
    }

    @Test
    void withFewerThanTwoNumbersThereIsNoInterval() {
        assertEquals(0.0, Statistic.of(4.0).confidenceHalfWidth(), 0.0);
        assertEquals(0.0, Statistic.of().confidenceHalfWidth(), 0.0);
    }

    @Test
    void theMeanDiffersFromZeroWhenTheIntervalLeavesItOut() {
        assertTrue(new Statistic(100, 1.0, 2.0, -3.0, 5.0).excludesZero());
        assertTrue(new Statistic(100, -1.0, 2.0, -5.0, 3.0).excludesZero());
        assertFalse(new Statistic(100, 0.2, 2.0, -3.0, 5.0).excludesZero());
    }

    @Test
    void aMeanExactlyAtTheEdgeOfTheIntervalIsNotADifference() {
        double edge = Statistic.Z_95 * 2.0 / Math.sqrt(100);

        assertFalse(new Statistic(100, edge, 2.0, -3.0, 5.0).excludesZero());
    }

    @Test
    void aSingleNumberNeverProvesADifference() {
        assertFalse(Statistic.of(50.0).excludesZero());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new Statistic(-1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Statistic(1, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Statistic(1, 0, Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Statistic(1, Double.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Statistic(1, 0, 0, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Statistic(1, 0, 0, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Statistic(1, 0, 0, 2, 1));
    }
}