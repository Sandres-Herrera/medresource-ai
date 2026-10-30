package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del resumen de una estrategia.
 */
class StrategySummaryTest {

    /** Diferencia clara (promedio 1, desviación 1, 100 casos) en el sentido dado. */
    private static final Statistic CLEARLY_POSITIVE = new Statistic(100, 1.0, 1.0, -1.0, 3.0);
    private static final Statistic CLEARLY_NEGATIVE = new Statistic(100, -1.0, 1.0, -3.0, 1.0);
    private static final Statistic UNCLEAR = new Statistic(100, 0.05, 1.0, -3.0, 3.0);
    private static final Statistic ZERO = new Statistic(100, 0.0, 0.0, 0.0, 0.0);

    private static Map<Metric, Statistic> all(Statistic statistic) {
        Map<Metric, Statistic> map = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            map.put(metric, statistic);
        }
        return map;
    }

    private static StrategySummary withDifferences(Statistic difference) {
        return new StrategySummary("IA", 100, all(ZERO), all(difference));
    }

    @Test
    void keepsTheData() {
        StrategySummary summary = new StrategySummary("IA", 100, all(UNCLEAR), all(ZERO));

        assertEquals("IA", summary.strategyName());
        assertEquals(100, summary.cases());
        assertEquals(UNCLEAR, summary.values().get(Metric.MORTALITY));
        assertEquals(ZERO, summary.differences().get(Metric.AVERAGE_WAIT));
    }

    @Test
    void keepsAnImmutableCopyOfTheMaps() {
        Map<Metric, Statistic> values = all(UNCLEAR);
        StrategySummary summary = new StrategySummary("IA", 100, values, all(ZERO));

        values.put(Metric.MORTALITY, ZERO);

        assertEquals(UNCLEAR, summary.values().get(Metric.MORTALITY));
        assertThrows(UnsupportedOperationException.class, () -> summary.values().clear());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new StrategySummary(null, 100, all(ZERO), all(ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new StrategySummary(" ", 100, all(ZERO), all(ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new StrategySummary("IA", 0, all(ZERO), all(ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new StrategySummary("IA", 100, null, all(ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new StrategySummary("IA", 100, all(ZERO), null));
    }

    @Test
    void requiresAValueForEveryMetric() {
        Map<Metric, Statistic> incomplete = all(ZERO);
        incomplete.remove(Metric.URGENT_WAIT);

        assertThrows(IllegalArgumentException.class, () -> new StrategySummary("IA", 100, incomplete, all(ZERO)));
        assertThrows(IllegalArgumentException.class, () -> new StrategySummary("IA", 100, all(ZERO), incomplete));
    }

    @Test
    void aClearlyLowerDifferenceIsBetterWhenLowerIsBetter() {
        StrategySummary summary = withDifferences(CLEARLY_NEGATIVE);

        assertTrue(summary.isBetterThanBaseline(Metric.MORTALITY));
        assertFalse(summary.isWorseThanBaseline(Metric.MORTALITY));
    }

    @Test
    void aClearlyHigherDifferenceIsWorseWhenLowerIsBetter() {
        StrategySummary summary = withDifferences(CLEARLY_POSITIVE);

        assertTrue(summary.isWorseThanBaseline(Metric.AVERAGE_WAIT));
        assertFalse(summary.isBetterThanBaseline(Metric.AVERAGE_WAIT));
    }

    @Test
    void aClearlyHigherDifferenceIsBetterForTheLifeYears() {
        assertTrue(withDifferences(CLEARLY_POSITIVE).isBetterThanBaseline(Metric.LIFE_YEARS_SAVED));
        assertTrue(withDifferences(CLEARLY_NEGATIVE).isWorseThanBaseline(Metric.LIFE_YEARS_SAVED));
        assertFalse(withDifferences(CLEARLY_NEGATIVE).isBetterThanBaseline(Metric.LIFE_YEARS_SAVED));
    }

    @Test
    void anUnclearDifferenceIsNeitherBetterNorWorse() {
        StrategySummary summary = withDifferences(UNCLEAR);

        for (Metric metric : Metric.values()) {
            assertFalse(summary.isBetterThanBaseline(metric), metric.name());
            assertFalse(summary.isWorseThanBaseline(metric), metric.name());
        }
    }
}