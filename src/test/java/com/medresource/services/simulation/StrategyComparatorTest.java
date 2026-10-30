package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.ComparisonReport;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Metric;
import com.medresource.model.RunMetrics;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.StrategySummary;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.RandomStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del comparador de estrategias.
 */
class StrategyComparatorTest {

    private static final HospitalConfig TIGHT = new HospitalConfig(3, 1, 1, 2, 3, 1, 1);

    private final CaseGenerator generator = new CaseGenerator();
    private final StrategyComparator comparator = new StrategyComparator(HospitalConfig.standard());

    private List<SimulationCase> cases(Scenario scenario, int count) {
        List<SimulationCase> cases = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cases.add(generator.generate(new CaseConfig(100 + i, scenario, 30, 180)));
        }
        return cases;
    }

    private static List<AllocationStrategy> threeStrategies() {
        return List.of(new ClinicalPriorityStrategy(), new FifoStrategy(), new RandomStrategy(1));
    }

    private static RunMetrics direct(AllocationStrategy strategy, SimulationCase simulationCase, HospitalConfig hospital) {
        return new MetricsCalculator().calculate(new SimulationEngine(simulationCase,
                new HospitalFactory().create(hospital), strategy).run());
    }

    // ---------- datos obligatorios ----------

    @Test
    void requiresTheHospital() {
        assertThrows(IllegalArgumentException.class, () -> new StrategyComparator(null));
    }

    @Test
    void requiresStrategiesWithoutNullsOrRepeatedNames() {
        List<SimulationCase> cases = cases(Scenario.NORMAL, 1);

        assertThrows(IllegalArgumentException.class, () -> comparator.compare(null, cases));
        assertThrows(IllegalArgumentException.class, () -> comparator.compare(List.of(), cases));
        assertThrows(IllegalArgumentException.class,
                () -> comparator.compare(Arrays.asList(new FifoStrategy(), null), cases));
        IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
                () -> comparator.compare(List.of(new FifoStrategy(), new FifoStrategy()), cases));
        assertTrue(repeated.getMessage().contains("Dos estrategias se llaman"));
    }

    @Test
    void requiresCasesWithoutNulls() {
        List<AllocationStrategy> strategies = threeStrategies();

        assertThrows(IllegalArgumentException.class, () -> comparator.compare(strategies, null));
        assertThrows(IllegalArgumentException.class, () -> comparator.compare(strategies, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> comparator.compare(strategies, Arrays.asList(cases(Scenario.NORMAL, 1).get(0), null)));
    }

    // ---------- el informe ----------

    @Test
    void reportsOneSummaryPerStrategyInTheSameOrder() {
        ComparisonReport report = comparator.compare(threeStrategies(), cases(Scenario.NORMAL, 3));

        assertEquals(HospitalConfig.standard(), report.hospital());
        assertEquals(3, report.cases());
        assertEquals(List.of("Prioridad clínica (triage)", "FIFO (orden de llegada)", "Aleatoria (entrenamiento)"),
                report.summaries().stream().map(StrategySummary::strategyName).toList());
        assertTrue(report.summaries().stream().allMatch(summary -> summary.cases() == 3));
    }

    @Test
    void theBaselineDiffersFromItselfByNothing() {
        ComparisonReport report = comparator.compare(threeStrategies(), cases(Scenario.CATASTROPHE, 3));

        for (Metric metric : Metric.values()) {
            assertEquals(0.0, report.baseline().differences().get(metric).mean(), 0.0, metric.name());
            assertEquals(0.0, report.baseline().differences().get(metric).standardDeviation(), 0.0, metric.name());
        }
    }

    @Test
    void theValuesAreTheMetricsOfEachRun() {
        List<SimulationCase> cases = cases(Scenario.CATASTROPHE, 2);
        FifoStrategy fifo = new FifoStrategy();

        ComparisonReport report = comparator.compare(List.of(new ClinicalPriorityStrategy(), fifo), cases);

        double expected = (direct(fifo, cases.get(0), HospitalConfig.standard()).mortalityRate()
                + direct(fifo, cases.get(1), HospitalConfig.standard()).mortalityRate()) / 2;
        assertEquals(expected, report.summaries().get(1).values().get(Metric.MORTALITY).mean(), 1e-12);
        assertEquals(2, report.summaries().get(1).values().get(Metric.MORTALITY).count());
    }

    @Test
    void theDifferenceIsCalculatedCaseByCase() {
        List<SimulationCase> cases = cases(Scenario.CATASTROPHE, 4);
        ClinicalPriorityStrategy clinical = new ClinicalPriorityStrategy();
        RandomStrategy random = new RandomStrategy(1);

        ComparisonReport report = comparator.compare(List.of(clinical, random), cases);

        double[] gaps = cases.stream().mapToDouble(simulationCase ->
                direct(random, simulationCase, HospitalConfig.standard()).avoidableDeaths()
                        - direct(clinical, simulationCase, HospitalConfig.standard()).avoidableDeaths()).toArray();
        double mean = Arrays.stream(gaps).average().orElseThrow();
        assertEquals(mean, report.summaries().get(1).differences().get(Metric.AVOIDABLE_DEATHS).mean(), 1e-12);
        assertEquals(report.summaries().get(1).values().get(Metric.AVOIDABLE_DEATHS).mean()
                        - report.baseline().values().get(Metric.AVOIDABLE_DEATHS).mean(),
                report.summaries().get(1).differences().get(Metric.AVOIDABLE_DEATHS).mean(), 1e-12);
    }

    @Test
    void theHospitalResourcesChangeTheResults() {
        List<SimulationCase> cases = cases(Scenario.CATASTROPHE, 3);
        List<AllocationStrategy> strategies = List.of(new ClinicalPriorityStrategy());

        ComparisonReport standard = comparator.compare(strategies, cases);
        ComparisonReport tight = new StrategyComparator(TIGHT).compare(strategies, cases);

        assertEquals(TIGHT, tight.hospital());
        assertTrue(tight.baseline().values().get(Metric.MORTALITY).mean()
                > standard.baseline().values().get(Metric.MORTALITY).mean());
    }

    @Test
    void theSameComparisonGivesTheSameReport() {
        List<SimulationCase> cases = cases(Scenario.UNEXPECTED_EMERGENCIES, 3);

        assertEquals(comparator.compare(threeStrategies(), cases), comparator.compare(threeStrategies(), cases));
    }

    @Test
    void metricsOfAGroupThatNeverAppearedAreLeftOutOfTheSummary() {
        List<SimulationCase> cases = cases(Scenario.NORMAL, 1);

        ComparisonReport report = comparator.compare(threeStrategies(), cases);

        RunMetrics run = direct(new ClinicalPriorityStrategy(), cases.get(0), HospitalConfig.standard());
        int expectedCount = run.minors() == 0 ? 0 : 1;
        assertEquals(expectedCount, report.baseline().values().get(Metric.MINOR_MORTALITY).count());
    }
}