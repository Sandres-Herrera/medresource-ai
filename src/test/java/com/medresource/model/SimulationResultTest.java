package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del resultado de una simulación.
 */
class SimulationResultTest {

    private static final CaseConfig CONFIG = new CaseConfig(1L, Scenario.NORMAL, 3, 60);

    private static Disease flu() {
        return DiseaseCatalog.findById(1).orElseThrow();
    }

    private static PatientOutcome discharged(int id) {
        return new PatientOutcome(id, "P-00" + id, 30, Sex.MALE, TriageLevel.IV, flu(), 0, 5, 40,
                PatientStatus.DISCHARGED, null, CompatibilityLevel.PRIMARY, false, 90.0);
    }

    private static PatientOutcome died(int id, CauseOfDeath cause) {
        return new PatientOutcome(id, "P-00" + id, 30, Sex.MALE, TriageLevel.IV, flu(), 0, -1, 40,
                PatientStatus.DECEASED, cause, null, false, 0.0);
    }

    private static SimulationResult result(List<PatientOutcome> outcomes) {
        return new SimulationResult("FIFO", CONFIG, 100, outcomes, List.of());
    }

    @Test
    void countsOutcomesByStatus() {
        SimulationResult result = result(List.of(discharged(1), discharged(2),
                died(3, CauseOfDeath.NO_ICU_BED)));

        assertEquals(2L, result.count(PatientStatus.DISCHARGED));
        assertEquals(1L, result.count(PatientStatus.DECEASED));
        assertEquals(0L, result.count(PatientStatus.REFERRED));
        assertEquals(1L, result.deaths());
    }

    @Test
    void avoidableDeathsExcludeIrreducibleMortality() {
        SimulationResult result = result(List.of(died(1, CauseOfDeath.NO_ICU_BED),
                died(2, CauseOfDeath.IRREDUCIBLE_MORTALITY), discharged(3)));

        assertEquals(2L, result.deaths());
        assertEquals(1L, result.avoidableDeaths());
    }

    @Test
    void keepsTheStrategyConfigAndDuration() {
        SimulationResult result = result(List.of(discharged(1), discharged(2), discharged(3)));

        assertEquals("FIFO", result.strategyName());
        assertEquals(CONFIG, result.config());
        assertEquals(100, result.totalMinutes());
    }

    @Test
    void keepsAnImmutableCopyOfTheLists() {
        List<PatientOutcome> outcomes = new ArrayList<>(List.of(discharged(1), discharged(2), discharged(3)));
        SimulationResult result = result(outcomes);

        outcomes.clear();

        assertEquals(3, result.outcomes().size());
        assertThrows(UnsupportedOperationException.class, () -> result.outcomes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.events().clear());
    }

    @Test
    void requiresOneOutcomePerPatient() {
        assertThrows(IllegalArgumentException.class, () -> result(List.of(discharged(1))));
    }

    @Test
    void rejectsMissingData() {
        List<PatientOutcome> three = List.of(discharged(1), discharged(2), discharged(3));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(null, CONFIG, 1, three, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult(" ", CONFIG, 1, three, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult("FIFO", null, 1, three, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult("FIFO", CONFIG, 1, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult("FIFO", CONFIG, 1, three, null));
    }

    @Test
    void rejectsANegativeDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationResult("FIFO", CONFIG, -1,
                List.of(discharged(1), discharged(2), discharged(3)), List.of()));
    }
}
