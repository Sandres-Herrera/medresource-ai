package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del informe de comparación.
 */
class ComparisonReportTest {

    private static StrategySummary summary(String name) {
        Map<Metric, Statistic> map = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            map.put(metric, Statistic.of(1.0, 2.0));
        }
        return new StrategySummary(name, 2, map, map);
    }

    @Test
    void theFirstSummaryIsTheBaseline() {
        ComparisonReport report = new ComparisonReport(HospitalConfig.standard(), 2,
                List.of(summary("Clínica"), summary("IA")));

        assertEquals("Clínica", report.baseline().strategyName());
        assertEquals(HospitalConfig.standard(), report.hospital());
        assertEquals(2, report.cases());
    }

    @Test
    void findsASummaryByStrategyName() {
        ComparisonReport report = new ComparisonReport(HospitalConfig.standard(), 2,
                List.of(summary("Clínica"), summary("IA")));

        assertEquals("IA", report.find("IA").orElseThrow().strategyName());
        assertEquals(Optional.empty(), report.find("Otra"));
    }

    @Test
    void keepsAnImmutableCopyOfTheList() {
        List<StrategySummary> summaries = new ArrayList<>(List.of(summary("Clínica")));
        ComparisonReport report = new ComparisonReport(HospitalConfig.standard(), 2, summaries);

        summaries.clear();

        assertEquals(1, report.summaries().size());
        assertThrows(UnsupportedOperationException.class, () -> report.summaries().clear());
    }

    @Test
    void rejectsInvalidData() {
        List<StrategySummary> one = List.of(summary("Clínica"));

        assertThrows(IllegalArgumentException.class, () -> new ComparisonReport(null, 2, one));
        assertThrows(IllegalArgumentException.class, () -> new ComparisonReport(HospitalConfig.standard(), 2, null));
        assertThrows(IllegalArgumentException.class, () -> new ComparisonReport(HospitalConfig.standard(), 0, one));
        assertThrows(IllegalArgumentException.class,
                () -> new ComparisonReport(HospitalConfig.standard(), 2, List.of()));
    }

    @Test
    void rejectsTwoStrategiesWithTheSameName() {
        List<StrategySummary> repeated = List.of(summary("IA"), summary("IA"));

        assertThrows(IllegalArgumentException.class,
                () -> new ComparisonReport(HospitalConfig.standard(), 2, repeated));
    }
}