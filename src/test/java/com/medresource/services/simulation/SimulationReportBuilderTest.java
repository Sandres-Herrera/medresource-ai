package com.medresource.services.simulation;

import com.medresource.model.BottleneckStats;
import com.medresource.model.CaseConfig;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.PatientOutcome;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationReport;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.TriageLevelStats;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reporte completo sobre simulaciones reales.
 */
class SimulationReportBuilderTest {

    /** Una ejecución terminada con su hospital, para armar el reporte. */
    private record Run(SimulationResult result, SimulationTrace trace, Hospital hospital) {
    }

    private static Run run(Scenario scenario, HospitalConfig config) {
        Hospital hospital = new HospitalFactory().create(config);
        SimulationEngine engine = new SimulationEngine(
                new CaseGenerator().generate(CaseConfig.of(7L, scenario)), hospital, new ClinicalPriorityStrategy());
        SimulationResult result = engine.run();
        return new Run(result, engine.getTrace(), hospital);
    }

    private static SimulationReport reportOf(Run run) {
        return new SimulationReportBuilder().build(run.result(), run.trace(), run.hospital());
    }

    @Test
    void theNumbersOfTheReportAddUp() {
        Run run = run(Scenario.CATASTROPHE, HospitalConfig.small());
        SimulationReport report = reportOf(run);

        assertEquals(run.result().deaths(), report.deaths().total());
        assertEquals(report.metrics().patients(), report.triage().stream().mapToInt(TriageLevelStats::patients).sum());
        assertEquals(report.metrics().deaths(), report.triage().stream().mapToInt(TriageLevelStats::deaths).sum());
        assertEquals(run.result().outcomes().stream().filter(PatientOutcome::wasTreated).count(), report.treated());
        assertEquals(run.result().totalMinutes(), report.totalMinutes());
    }

    @Test
    void aCatastropheInASmallHospitalHasBottlenecksAndACollapse() {
        SimulationReport report = reportOf(run(Scenario.CATASTROPHE, HospitalConfig.small()));

        assertFalse(report.bottlenecks().isEmpty());
        assertEquals(1.0, report.bottlenecks().stream().mapToDouble(BottleneckStats::share).sum(), 1e-9);
        assertFalse(report.collapses().isEmpty());
        assertFalse(report.cases().isEmpty());
        assertTrue(report.cases().size() <= CaseSelector.MAX_CASES);
        assertTrue(report.failures().overTarget() > 0);
    }

    @Test
    void theReportHasEveryResourceStaffMemberAndBloodType() {
        Run run = run(Scenario.NORMAL, HospitalConfig.standard());
        SimulationReport report = reportOf(run);

        assertEquals(6, report.resources().size());
        assertEquals(run.hospital().getDoctors().size() + run.hospital().getNurses().size(), report.staff().size());
        assertEquals(8, report.blood().types().size());
        assertEquals(ReportAssumptions.all(), report.assumptions());
    }

    @Test
    void theTraceMustBelongToTheResult() {
        Run run = run(Scenario.NORMAL, HospitalConfig.standard());
        SimulationTrace other = new SimulationTrace(Map.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(),
                Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationReportBuilder().build(run.result(), other, run.hospital()));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationReportBuilder().build(null, run.trace(), run.hospital()));
    }
}
