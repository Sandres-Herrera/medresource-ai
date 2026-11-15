package com.medresource.services.simulation;

import com.medresource.model.CauseOfDeath;
import com.medresource.model.PatientStatus;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.TriageLevel;
import com.medresource.model.TriageLevelStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.medresource.services.simulation.ReportFixtures.care;
import static com.medresource.services.simulation.ReportFixtures.outcome;
import static com.medresource.services.simulation.ReportFixtures.result;
import static com.medresource.services.simulation.ReportFixtures.trace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los resultados por nivel de triage.
 */
class TriageAnalyzerTest {

    /**
     * Tres pacientes de triage II (uno a tiempo, uno tarde que murió, uno remitido) y uno de triage V que se
     * fue sin atención.
     */
    private static SimulationResult result4() {
        return result(60, 700,
                outcome(1, TriageLevel.II, 0, 10, 40, PatientStatus.DISCHARGED, null, 95.0),
                outcome(2, TriageLevel.II, 0, 50, 90, PatientStatus.DECEASED, CauseOfDeath.LACK_OF_TIMELY_CARE, 0.0),
                outcome(3, TriageLevel.II, 0, -1, 5, PatientStatus.REFERRED, null, 68.0),
                outcome(4, TriageLevel.V, 0, -1, 600, PatientStatus.UNATTENDED, null, 30.0));
    }

    private static SimulationTrace trace4() {
        return trace(Map.of("P001", 90.0, "P002", 80.0, "P003", 70.0, "P004", 60.0),
                List.of(care(1, 10, 85.0), care(2, 50, 50.0)), List.of());
    }

    private static TriageLevelStats levelOf(List<TriageLevelStats> stats, TriageLevel level) {
        return stats.stream().filter(item -> item.level() == level).findFirst().orElseThrow();
    }

    @Test
    void thereIsOneRowPerTriageLevelInOrder() {
        List<TriageLevelStats> stats = new TriageAnalyzer().analyze(result4(), trace4());

        assertEquals(List.of(TriageLevel.values()), stats.stream().map(TriageLevelStats::level).toList());
    }

    @Test
    void countsThePatientsTheDeathsAndTheReferredOfALevel() {
        TriageLevelStats level2 = levelOf(new TriageAnalyzer().analyze(result4(), trace4()), TriageLevel.II);

        assertEquals(3, level2.patients());
        assertEquals(1, level2.deaths());
        assertEquals(1, level2.referred());
    }

    @Test
    void measuresTheWaitsAndHowManyWereAttendedWithinTheTarget() {
        TriageLevelStats level2 = levelOf(new TriageAnalyzer().analyze(result4(), trace4()), TriageLevel.II);

        assertEquals((10 + 50 + 5) / 3.0, level2.averageWaitMinutes(), 1e-9);
        assertEquals(50, level2.maxWaitMinutes());
        assertEquals(1, level2.withinTarget());
        assertEquals(0.5, level2.withinTargetRate(), 1e-12);
    }

    @Test
    void measuresTheHealthLostBeforeTheCareOrTheEndOfTheCase() {
        List<TriageLevelStats> stats = new TriageAnalyzer().analyze(result4(), trace4());

        assertEquals((5.0 + 30.0 + 2.0) / 3, levelOf(stats, TriageLevel.II).averageHealthLost(), 1e-9);
        assertEquals(30.0, levelOf(stats, TriageLevel.V).averageHealthLost(), 1e-9);
    }

    @Test
    void aLevelWithoutPatientsComesInZero() {
        TriageLevelStats level1 = levelOf(new TriageAnalyzer().analyze(result4(), trace4()), TriageLevel.I);

        assertEquals(0, level1.patients());
        assertEquals(0, level1.maxWaitMinutes());
        assertTrue(Double.isNaN(level1.averageWaitMinutes()));
    }

    @Test
    void needsTheResultAndTheTrace() {
        assertThrows(IllegalArgumentException.class, () -> new TriageAnalyzer().analyze(null, trace4()));
        assertThrows(IllegalArgumentException.class, () -> new TriageAnalyzer().analyze(result4(), null));
    }
}
