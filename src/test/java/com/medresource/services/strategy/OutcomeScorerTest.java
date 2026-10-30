package com.medresource.services.strategy;

import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Objective;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del puntaje de desenlaces.
 */
class OutcomeScorerTest {

    private static final double DELTA = 1e-9;

    private final OutcomeScorer scorer = new OutcomeScorer();

    /** Desenlace de un paciente; {@code start} en -1 significa que nunca fue atendido. */
    private static PatientOutcome outcome(PatientStatus status, int age, int arrival, int start, int end) {
        CauseOfDeath cause = status == PatientStatus.DECEASED ? CauseOfDeath.LACK_OF_TIMELY_CARE : null;
        CompatibilityLevel care = start >= 0 ? CompatibilityLevel.PRIMARY : null;
        return new PatientOutcome(1, "P001", age, Sex.MALE, TriageLevel.III,
                DiseaseCatalog.findById(1).orElseThrow(), arrival, start, end, status, cause, care, false, 80.0);
    }

    private static PatientOutcome discharged(int age, int arrival, int start, int end) {
        return outcome(PatientStatus.DISCHARGED, age, arrival, start, end);
    }

    @Test
    void whoDoesNotSurviveScoresZeroForEveryObjective() {
        PatientOutcome deceased = outcome(PatientStatus.DECEASED, 30, 0, 10, 50);
        PatientOutcome unattended = outcome(PatientStatus.UNATTENDED, 30, 0, -1, 1900);
        PatientOutcome referred = outcome(PatientStatus.REFERRED, 30, 0, -1, 0);

        for (Objective objective : Objective.values()) {
            assertEquals(0.0, scorer.score(deceased, objective), DELTA, objective.name());
            assertEquals(0.0, scorer.score(unattended, objective), DELTA, objective.name());
            assertEquals(0.0, scorer.score(referred, objective), DELTA, objective.name());
        }
    }

    @Test
    void livesScoreOneForEverySurvivor() {
        assertEquals(1.0, scorer.score(discharged(30, 0, 10, 90), Objective.MAXIMIZE_LIVES), DELTA);
        assertEquals(1.0, scorer.score(discharged(95, 0, 400, 900), Objective.MAXIMIZE_LIVES), DELTA);
        assertEquals(1.0, scorer.score(outcome(PatientStatus.STABILIZED_WITHOUT_CURE, 60, 0, 5, 50),
                Objective.MAXIMIZE_LIVES), DELTA);
    }

    @Test
    void lifeYearsScoreTheYearsLeftUntilOneHundred() {
        assertEquals(0.8, scorer.score(discharged(20, 0, 10, 90), Objective.MAXIMIZE_LIFE_YEARS), DELTA);
        assertEquals(1.0, scorer.score(discharged(0, 0, 10, 90), Objective.MAXIMIZE_LIFE_YEARS), DELTA);
        assertEquals(0.1, scorer.score(discharged(90, 0, 10, 90), Objective.MAXIMIZE_LIFE_YEARS), DELTA);
    }

    @Test
    void lifeYearsNeverGoBelowZero() {
        assertEquals(0.0, scorer.score(discharged(100, 0, 10, 90), Objective.MAXIMIZE_LIFE_YEARS), DELTA);
        assertEquals(0.0, scorer.score(discharged(120, 0, 10, 90), Objective.MAXIMIZE_LIFE_YEARS), DELTA);
    }

    @Test
    void equityScoresMoreTheMoreThePatientWaited() {
        assertEquals(0.5, scorer.score(discharged(30, 100, 100, 200), Objective.EQUITY), DELTA);
        assertEquals(0.75, scorer.score(discharged(30, 0, 240, 300), Objective.EQUITY), DELTA);
        assertEquals(1.0, scorer.score(discharged(30, 0, 480, 600), Objective.EQUITY), DELTA);
    }

    @Test
    void equityStopsGrowingAfterTheTimeScale() {
        assertEquals(1.0, scorer.score(discharged(30, 0, 960, 1000), Objective.EQUITY), DELTA);
    }

    @Test
    void waitObjectiveScoresMoreTheShorterTheTreatment() {
        assertEquals(1.0, scorer.score(discharged(30, 0, 10, 10), Objective.MINIMIZE_WAIT), DELTA);
        assertEquals(0.75, scorer.score(discharged(30, 0, 10, 130), Objective.MINIMIZE_WAIT), DELTA);
        assertEquals(0.0, scorer.score(discharged(30, 0, 10, 490), Objective.MINIMIZE_WAIT), DELTA);
    }

    @Test
    void waitObjectiveStopsDroppingAfterTheTimeScale() {
        assertEquals(0.0, scorer.score(discharged(30, 0, 10, 1010), Objective.MINIMIZE_WAIT), DELTA);
    }

    @Test
    void waitObjectiveIgnoresHowLongThePatientWaitedBeforeTheTreatment() {
        assertEquals(0.75, scorer.score(discharged(30, 0, 300, 420), Objective.MINIMIZE_WAIT), DELTA);
    }

    @Test
    void ageDoesNotChangeTheScoreOfTheOtherObjectives() {
        for (Objective objective : new Objective[]{Objective.MAXIMIZE_LIVES, Objective.EQUITY,
                Objective.MINIMIZE_WAIT}) {
            assertEquals(scorer.score(discharged(5, 0, 100, 200), objective),
                    scorer.score(discharged(90, 0, 100, 200), objective), DELTA, objective.name());
        }
    }

    @Test
    void rejectsMissingData() {
        PatientOutcome outcome = discharged(30, 0, 10, 90);

        assertThrows(IllegalArgumentException.class, () -> scorer.score(null, Objective.MAXIMIZE_LIVES));
        assertThrows(IllegalArgumentException.class, () -> scorer.score(outcome, null));
    }
}