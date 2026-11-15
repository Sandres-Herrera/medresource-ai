package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del reporte terminado.
 */
class GeneratedReportTest {

    private static final SimulationReport REPORT = new SimulationReport(CaseConfig.of(7L, Scenario.NORMAL), 520,
            new RunMetrics("Prioridad clínica", 10, 2, 2, 7, 0, 1, 20.0, 5.0, 300.0, 2, 0, 1, 1), 8, 95,
            List.of(new TriageLevelStats(TriageLevel.II, 3, 1, 0, 40.0, 95, 1, 20.0)),
            new DeathBreakdown(1, 1, 0, Map.of(CauseOfDeath.LACK_OF_TIMELY_CARE, 2)),
            new UnfinishedPatients(480, 0, 2, 3, 40, 1), new CareFailures(4, 1, 6), List.of(), List.of(), List.of(),
            List.of(), List.of(), new BloodReport(List.of(), 0, 0, 0, 0, 0), List.of(), List.of("Sin turnos."));
    private static final Statistic BASE = Statistic.of(0.10, 0.12, 0.11);
    private static final WhatIfReport WHAT_IF = new WhatIfReport(3, "Prioridad clínica (triage)", BASE,
            Statistic.of(40, 45, 50), List.of(), List.of());
    private static final ReportNarrative NARRATIVE = new ReportNarrative(NarrativeSource.AI, "Resumen.", Map.of(),
            List.of(), "Conclusión.", null);

    @Test
    void keepsTheNumbersTheAnalysisAndTheText() {
        GeneratedReport generated = new GeneratedReport(REPORT, WHAT_IF, NARRATIVE);

        assertSame(REPORT, generated.report());
        assertSame(WHAT_IF, generated.whatIf());
        assertSame(NARRATIVE, generated.narrative());
    }

    @Test
    void noPartCanBeMissing() {
        assertThrows(IllegalArgumentException.class, () -> new GeneratedReport(null, WHAT_IF, NARRATIVE));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedReport(REPORT, null, NARRATIVE));
        assertThrows(IllegalArgumentException.class, () -> new GeneratedReport(REPORT, WHAT_IF, null));
    }
}
