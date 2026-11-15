package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reporte de fin de simulación.
 */
class SimulationReportTest {

    private static final RunMetrics METRICS = new RunMetrics("Prioridad clínica", 10, 2, 2, 7, 0, 1, 20.0, 5.0,
            300.0, 2, 0, 1, 1);
    private static final DeathBreakdown DEATHS = new DeathBreakdown(1, 1, 0,
            Map.of(CauseOfDeath.LACK_OF_TIMELY_CARE, 2));
    private static final List<BottleneckStats> BOTTLENECKS = List.of(
            new BottleneckStats(MissingResource.OPERATING_ROOM, 300, 0.75, 4, 30, 200, 3, 90),
            new BottleneckStats(MissingResource.DOCTOR, 100, 0.25, 3, 40, 120, 2, 60));
    private static final List<ResourceUsage> RESOURCES = List.of(
            new ResourceUsage(ResourceGroup.OPERATING_ROOMS, 2, 0.9, 2, 120),
            new ResourceUsage(ResourceGroup.BEDS, 10, 0.2, 5, 0));
    private static final List<StaffLoad> STAFF = List.of(new StaffLoad("D01", Specialty.SURGERY, 4, 500, 0.95),
            new StaffLoad("E01", null, 1, 30, 0.05), new StaffLoad("E02", null, 3, 300, 0.5));

    private static SimulationReport report(RunMetrics metrics, DeathBreakdown deaths, int treated) {
        return new SimulationReport(CaseConfig.of(7L, Scenario.NORMAL), 520, metrics, treated, 95,
                List.of(new TriageLevelStats(TriageLevel.II, 3, 1, 0, 40.0, 95, 1, 20.0)), deaths,
                new UnfinishedPatients(480, 0, 2, 3, 40, 1), new CareFailures(4, 1, 6), BOTTLENECKS,
                List.of(new CollapsePeriod(50, 180, 3, 90)), RESOURCES, STAFF,
                List.of(new SpecialtyLoad(Specialty.SURGERY, 1, 4, 0.95, 100)),
                new BloodReport(List.of(), 0, 0, 0, 0, 0), List.of(), List.of("Sin turnos."));
    }

    @Test
    void theMainBottleneckIsTheFirstOne() {
        assertEquals(MissingResource.OPERATING_ROOM, report(METRICS, DEATHS, 8).mainBottleneck().orElseThrow()
                .resource());
    }

    @Test
    void findsTheResultsOfATriageLevel() {
        SimulationReport report = report(METRICS, DEATHS, 8);

        assertEquals(95, report.triageOf(TriageLevel.II).orElseThrow().maxWaitMinutes());
        assertTrue(report.triageOf(TriageLevel.V).isEmpty());
    }

    @Test
    void separatesUnderusedResourcesAndSaturatedOrIdleStaff() {
        SimulationReport report = report(METRICS, DEATHS, 8);

        assertEquals(List.of(ResourceGroup.BEDS),
                report.underusedResources().stream().map(ResourceUsage::group).toList());
        assertEquals(List.of("D01"), report.saturatedStaff().stream().map(StaffLoad::code).toList());
        assertEquals(List.of("E01"), report.idleStaff().stream().map(StaffLoad::code).toList());
    }

    @Test
    void theDeathsMustMatchTheMetrics() {
        DeathBreakdown other = new DeathBreakdown(1, 0, 0, Map.of(CauseOfDeath.NO_ICU_BED, 1));

        assertThrows(IllegalArgumentException.class, () -> report(METRICS, other, 8));
    }

    @Test
    void cannotTreatMorePatientsThanArrived() {
        assertThrows(IllegalArgumentException.class, () -> report(METRICS, DEATHS, 11));
    }
}
