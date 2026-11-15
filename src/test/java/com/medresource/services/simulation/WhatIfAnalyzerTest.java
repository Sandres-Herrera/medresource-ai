package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.ResourceChange;
import com.medresource.model.Scenario;
import com.medresource.model.Statistic;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del análisis «¿y si…?».
 */
class WhatIfAnalyzerTest {

    /** Caso corto para que las pruebas sean rápidas: 40 pacientes en 2 horas de catástrofe. */
    private static final CaseConfig SHORT_CASE = new CaseConfig(7L, Scenario.CATASTROPHE, 40, 120);
    private static final HospitalConfig SMALL = HospitalConfig.small();
    private static final int CASES = 3;

    private static WhatIfReport analyze(List<WhatIfProgress> progress) {
        return new WhatIfAnalyzer().analyze(SHORT_CASE, SMALL, ClinicalPriorityStrategy::new,
                List.of(FifoStrategy::new), CASES, progress::add);
    }

    @Test
    void countsEveryAlternativeOnEveryCase() {
        assertEquals(12, ResourceChange.allFor(SMALL).size());
        assertEquals((1 + 12 + 1) * CASES, WhatIfAnalyzer.runsNeeded(SMALL, 1, CASES));
    }

    @Test
    void reportsTheProgressAfterEachRunInOrder() {
        List<WhatIfProgress> progress = new ArrayList<>();
        analyze(progress);

        int total = WhatIfAnalyzer.runsNeeded(SMALL, 1, CASES);
        assertEquals(total, progress.size());
        for (int i = 0; i < total; i++) {
            assertEquals(i + 1, progress.get(i).done());
            assertEquals(total, progress.get(i).total());
        }
        assertTrue(progress.get(total - 1).isFinished());
    }

    @Test
    void triesEveryChangeAndTheOtherStrategy() {
        WhatIfReport report = analyze(new ArrayList<>());

        assertEquals(CASES, report.cases());
        assertEquals(new ClinicalPriorityStrategy().getName(), report.strategyName());
        assertEquals(ResourceChange.allFor(SMALL), report.resources().stream().map(WhatIfResult::change).toList());
        assertEquals(1, report.policies().size());
        assertEquals("Usar la estrategia " + new FifoStrategy().getName(), report.policies().get(0).label());
    }

    @Test
    void skipsAStrategyWithTheSameNameAsTheCurrentOne() {
        WhatIfReport report = new WhatIfAnalyzer().analyze(SHORT_CASE, SMALL, ClinicalPriorityStrategy::new,
                List.of(ClinicalPriorityStrategy::new, FifoStrategy::new), CASES, progress -> { });

        assertEquals(1, report.policies().size());
    }

    @Test
    void theHospitalAsItIsMatchesItsOwnSimulations() {
        double[] mortality = new double[CASES];
        for (int i = 0; i < CASES; i++) {
            CaseConfig config = new CaseConfig(SHORT_CASE.seed() + i, SHORT_CASE.scenario(),
                    SHORT_CASE.patientCount(), SHORT_CASE.durationMinutes());
            mortality[i] = new MetricsCalculator().calculate(new SimulationEngine(new CaseGenerator().generate(config),
                    new HospitalFactory().create(SMALL), new ClinicalPriorityStrategy()).run()).mortalityRate();
        }

        assertEquals(Statistic.of(mortality), analyze(new ArrayList<>()).mortality());
    }

    @Test
    void givesTheSameReportEveryTime() {
        assertEquals(analyze(new ArrayList<>()), analyze(new ArrayList<>()));
    }

    @Test
    void aSmallHospitalInACatastropheLacksResources() {
        WhatIfReport report = new WhatIfAnalyzer().analyze(CaseConfig.of(7L, Scenario.CATASTROPHE), SMALL,
                ClinicalPriorityStrategy::new, List.of(FifoStrategy::new), 10, progress -> { });

        assertFalse(report.missingResources().isEmpty());
        assertFalse(report.recommendations().isEmpty());
    }

    @Test
    void rejectsInvalidData() {
        WhatIfAnalyzer analyzer = new WhatIfAnalyzer();

        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(SHORT_CASE, SMALL,
                ClinicalPriorityStrategy::new, List.of(), 1, progress -> { }));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(SHORT_CASE, SMALL,
                ClinicalPriorityStrategy::new, List.of(), WhatIfAnalyzer.MAX_CASES + 1, progress -> { }));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(null, SMALL,
                ClinicalPriorityStrategy::new, List.of(), CASES, progress -> { }));
        assertThrows(IllegalArgumentException.class, () -> WhatIfAnalyzer.runsNeeded(SMALL, -1, CASES));
    }
}
