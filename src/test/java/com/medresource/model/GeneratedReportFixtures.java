package com.medresource.model;

import java.util.List;
import java.util.Map;

/**
 * Reporte terminado pequeño, armado a mano, para las pruebas del modelo que solo necesitan uno cualquiera.
 */
public final class GeneratedReportFixtures {

    private GeneratedReportFixtures() {
    }

    /**
     * Arma un reporte de 10 pacientes redactado por la IA.
     *
     * @return reporte terminado
     */
    public static GeneratedReport small() {
        SimulationReport report = new SimulationReport(CaseConfig.of(7L, Scenario.NORMAL), 520,
                new RunMetrics("Prioridad clínica", 10, 2, 2, 7, 0, 1, 20.0, 5.0, 300.0, 2, 0, 1, 1), 8, 95,
                List.of(new TriageLevelStats(TriageLevel.II, 3, 1, 0, 40.0, 95, 1, 20.0)),
                new DeathBreakdown(1, 1, 0, Map.of(CauseOfDeath.LACK_OF_TIMELY_CARE, 2)),
                new UnfinishedPatients(480, 0, 2, 3, 40, 1), new CareFailures(4, 1, 6), List.of(), List.of(),
                List.of(), List.of(), List.of(), new BloodReport(List.of(), 0, 0, 0, 0, 0), List.of(),
                List.of("Sin turnos."));
        WhatIfReport whatIf = new WhatIfReport(3, "Prioridad clínica (triage)", Statistic.of(0.10, 0.12, 0.11),
                Statistic.of(40, 45, 50), List.of(), List.of());
        ReportNarrative narrative = new ReportNarrative(NarrativeSource.AI, "Resumen.",
                Map.of(ReportSection.TRIAGE, "Texto del triage."), List.of(), "Conclusión.", null);
        return new GeneratedReport(report, whatIf, narrative);
    }
}
