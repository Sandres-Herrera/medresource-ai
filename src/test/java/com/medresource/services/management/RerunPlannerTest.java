package com.medresource.services.management;

import com.medresource.model.CaseFixtures;
import com.medresource.model.ExtraResource;
import com.medresource.model.GeneratedReport;
import com.medresource.model.GeneratedReportFixtures;
import com.medresource.model.HospitalConfig;
import com.medresource.model.RerunPlan;
import com.medresource.model.ResourceChange;
import com.medresource.model.ResultFixtures;
import com.medresource.model.RunRecord;
import com.medresource.model.SavedReport;
import com.medresource.model.Specialty;
import com.medresource.model.Statistic;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;
import com.medresource.persistence.ResultDao;
import com.medresource.services.simulation.CaseGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del plan para volver a simular el caso de una ejecución guardada.
 *
 * <p>La ejecución 1 es FIFO con el hospital de {@link ResultFixtures#HOSPITAL}.</p>
 */
class RerunPlannerTest {

    private static final String FIFO = "FIFO";
    private static final String PRIORITY = "Prioridad clínica (triage)";
    private static final Statistic CLEARLY_FEWER_DEATHS = Statistic.of(-0.05, -0.06, -0.04);
    private static final Statistic SAME = Statistic.of(0.0, 0.0, 0.0);
    private static final Statistic MORTALITY = Statistic.of(0.10, 0.11, 0.12);

    private final InMemoryResults results = new InMemoryResults();
    private final RerunPlanner planner = new RerunPlanner(new SimulationService(
            new CaseService(new InMemoryCaseDao(), new CaseGenerator(CaseFixtures.diseases())), results));

    RerunPlannerTest() {
        results.stored.put(1, ResultFixtures.runOf(1, FIFO, 77));
    }

    @Test
    void theSameCaseKeepsTheHospitalAndTheStrategy() {
        RerunPlan plan = planner.sameCase(savedWith(List.of(resource(ResourceChange.of(ExtraResource.BEDS)))));

        assertEquals(1, plan.baseRunId());
        assertEquals(ResultFixtures.configOf(77), plan.caseConfig());
        assertEquals(ResultFixtures.HOSPITAL, plan.hospital());
        assertEquals(FIFO, plan.strategyName());
        assertFalse(plan.hasChanges());
    }

    @Test
    void theRecommendedResourcesAreAppliedWithTheirSpecialists() {
        SavedReport saved = savedWith(List.of(resource(ResourceChange.doctor(Specialty.SURGERY)),
                resource(ResourceChange.of(ExtraResource.ICU_BEDS))));

        RerunPlan plan = planner.withRecommendations(saved);

        assertEquals(ResultFixtures.configOf(77), plan.caseConfig());
        assertEquals(List.of(Specialty.SURGERY), plan.hospital().extraDoctors());
        assertEquals(ResultFixtures.HOSPITAL.icuBeds() + 1, plan.hospital().icuBeds());
        assertEquals(ResultFixtures.HOSPITAL.doctors(), plan.hospital().doctors());
        assertEquals(FIFO, plan.strategyName());
        assertEquals(List.of(ResourceChange.doctor(Specialty.SURGERY).describe(),
                ResourceChange.of(ExtraResource.ICU_BEDS).describe()), plan.changes());
    }

    @Test
    void theRecommendedStrategyReplacesTheOriginalOne() {
        RerunPlan plan = planner.withRecommendations(savedWith(List.of(policy(PRIORITY))));

        assertEquals(PRIORITY, plan.strategyName());
        assertEquals(ResultFixtures.HOSPITAL, plan.hospital());
        assertEquals(List.of("Estrategia: " + PRIORITY), plan.changes());
    }

    @Test
    void withoutRecommendationsThePlanHasNoChanges() {
        RerunPlan plan = planner.withRecommendations(savedWith(List.of()));

        assertEquals(planner.sameCase(savedWith(List.of())), plan);
    }

    @Test
    void aSpecialistThatNoLongerFitsIsLeftOut() {
        HospitalConfig full = ResultFixtures.HOSPITAL.withExtraDoctors(
                Collections.nCopies(HospitalConfig.MAX_EXTRA_DOCTORS, Specialty.PEDIATRICS));
        results.stored.put(1, new RunRecord(1, full, ResultFixtures.resultOf(FIFO, 77)));

        RerunPlan plan = planner.withRecommendations(savedWith(List.of(
                resource(ResourceChange.doctor(Specialty.SURGERY)), resource(ResourceChange.of(ExtraResource.BEDS)))));

        assertEquals(0, plan.hospital().extraDoctorsOf(Specialty.SURGERY));
        assertEquals(List.of(ResourceChange.of(ExtraResource.BEDS).describe()), plan.changes());
    }

    @Test
    void needsTheReportAndItsSavedRun() {
        SavedReport ofMissingRun = new SavedReport(3, 9, LocalDateTime.of(2026, 11, 16, 8, 0), report(List.of()));

        assertThrows(IllegalArgumentException.class, () -> new RerunPlanner(null));
        assertThrows(IllegalArgumentException.class, () -> planner.sameCase(null));
        assertThrows(IllegalArgumentException.class, () -> planner.withRecommendations(null));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> planner.sameCase(ofMissingRun));
        assertTrue(error.getMessage().contains("ejecución 9"));
    }

    // ---------- apoyos ----------

    private static SavedReport savedWith(List<WhatIfResult> recommendations) {
        return new SavedReport(4, 1, LocalDateTime.of(2026, 11, 16, 8, 0), report(recommendations));
    }

    private static GeneratedReport report(List<WhatIfResult> recommendations) {
        GeneratedReport base = GeneratedReportFixtures.small();
        List<WhatIfResult> resources = recommendations.stream().filter(result -> !result.isPolicy()).toList();
        List<WhatIfResult> policies = recommendations.stream().filter(WhatIfResult::isPolicy).toList();
        WhatIfReport whatIf = new WhatIfReport(3, FIFO, MORTALITY, Statistic.of(40, 45, 50), resources, policies);
        return new GeneratedReport(base.report(), whatIf, base.narrative());
    }

    private static WhatIfResult resource(ResourceChange change) {
        return new WhatIfResult(change.describe(), change, FIFO, MORTALITY, CLEARLY_FEWER_DEATHS, SAME);
    }

    private static WhatIfResult policy(String strategyName) {
        return new WhatIfResult(strategyName, null, strategyName, MORTALITY, CLEARLY_FEWER_DEATHS, SAME);
    }

    /** Ejecuciones guardadas en memoria; solo hace falta buscarlas. */
    private static final class InMemoryResults implements ResultDao {

        final Map<Integer, RunRecord> stored = new TreeMap<>();

        @Override
        public Integer save(RunRecord run) {
            int id = stored.size() + 1;
            stored.put(id, run);
            return id;
        }

        @Override
        public List<Integer> findIdsByCase(int caseId) {
            return stored.entrySet().stream().filter(entry -> entry.getValue().caseId() == caseId)
                    .map(Map.Entry::getKey).toList();
        }

        @Override
        public Optional<RunRecord> findById(Integer id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<RunRecord> findAll() {
            return List.copyOf(stored.values());
        }

        @Override
        public void update(RunRecord run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            stored.remove(id);
        }
    }
}
