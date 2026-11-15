package com.medresource.services.simulation;

import com.medresource.model.CauseOfDeath;
import com.medresource.model.PatientStatus;
import com.medresource.model.ReviewCase;
import com.medresource.model.ReviewReason;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.medresource.services.simulation.ReportFixtures.arrivalHealth;
import static com.medresource.services.simulation.ReportFixtures.care;
import static com.medresource.services.simulation.ReportFixtures.outcome;
import static com.medresource.services.simulation.ReportFixtures.result;
import static com.medresource.services.simulation.ReportFixtures.trace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la elección de los casos para revisar.
 */
class CaseSelectorTest {

    /**
     * P001 y P002 murieron esperando (P002 esperó más); P003 murió atendido tarde; P004 es urgente y esperó;
     * P005 es leve y esperó mucho; P006 se fue sin atención; P007 fue remitido después de esperar muchísimo.
     */
    private static SimulationResult result7() {
        return result(60, 900,
                outcome(1, TriageLevel.I, 0, -1, 20, PatientStatus.DECEASED, CauseOfDeath.LACK_OF_TIMELY_CARE, 0.0),
                outcome(2, TriageLevel.II, 0, -1, 45, PatientStatus.DECEASED, CauseOfDeath.LACK_OF_TIMELY_CARE, 0.0),
                outcome(3, TriageLevel.II, 0, 60, 90, PatientStatus.DECEASED, CauseOfDeath.LACK_OF_TIMELY_CARE, 0.0),
                outcome(4, TriageLevel.II, 10, 35, 90, PatientStatus.DISCHARGED, null, 90.0),
                outcome(5, TriageLevel.V, 0, 700, 730, PatientStatus.DISCHARGED, null, 90.0),
                outcome(6, TriageLevel.IV, 0, -1, 300, PatientStatus.UNATTENDED, null, 70.0),
                outcome(7, TriageLevel.I, 0, -1, 899, PatientStatus.REFERRED, null, 10.0));
    }

    private static SimulationTrace trace7() {
        return trace(arrivalHealth(7, 90.0), List.of(care(3, 60, 30.0), care(4, 35, 85.0), care(5, 700, 89.0)),
                List.of());
    }

    @Test
    void choosesOnePatientPerReasonInOrderAndAtMostFive() {
        List<ReviewCase> cases = new CaseSelector().select(result7(), trace7());

        assertEquals(CaseSelector.MAX_CASES, cases.size());
        assertEquals(List.of(ReviewReason.LONGEST_WAIT_BEFORE_DEATH, ReviewReason.DIED_AFTER_LATE_CARE,
                        ReviewReason.LONGEST_URGENT_WAIT, ReviewReason.LARGEST_HEALTH_LOSS,
                        ReviewReason.FURTHEST_OVER_TARGET),
                cases.stream().map(ReviewCase::reason).toList());
        assertEquals(List.of("P002", "P003", "P004", "P001", "P005"),
                cases.stream().map(ReviewCase::patientCode).toList());
    }

    @Test
    void keepsTheMinuteToSeeInThePlan() {
        List<ReviewCase> cases = new CaseSelector().select(result7(), trace7());

        assertEquals(45, cases.get(0).focusMinute());
        assertEquals(60, cases.get(1).focusMinute());
        assertEquals(480, cases.get(4).focusMinute());
        assertEquals(90.0, cases.get(3).healthLost(), 1e-9);
    }

    @Test
    void neverChoosesAReferredPatient() {
        List<ReviewCase> cases = new CaseSelector().select(result7(), trace7());

        assertTrue(cases.stream().noneMatch(review -> review.patientCode().equals("P007")));
    }

    @Test
    void aCalmSimulationHasNoCasesToReview() {
        SimulationResult calm = result(60, 60, outcome(1, TriageLevel.IV, 0, 0, 30, PatientStatus.DISCHARGED, null,
                90.0));

        assertTrue(new CaseSelector().select(calm, trace(Map.of("P001", 90.0), List.of(care(1, 0, 90.0)),
                List.of())).isEmpty());
    }

    @Test
    void needsTheResultAndTheTrace() {
        assertThrows(IllegalArgumentException.class, () -> new CaseSelector().select(null, trace7()));
        assertThrows(IllegalArgumentException.class, () -> new CaseSelector().select(result7(), null));
    }
}
