package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del desenlace de un paciente.
 */
class PatientOutcomeTest {

    private static Disease flu() {
        return DiseaseCatalog.findById(1).orElseThrow();
    }

    private static Patient patient(int arrivalMinute) {
        Patient patient = new Patient(1, 30, Sex.FEMALE, BloodType.A_POSITIVE, flu(), flu().getSymptoms(),
                arrivalMinute, 80.0, false);
        patient.assignTriage(TriageLevel.IV);
        return patient;
    }

    private static PatientOutcome outcome(PatientStatus status, CauseOfDeath cause, int start,
                                          CompatibilityLevel care, int arrival, int end) {
        return new PatientOutcome(1, "P-001", 30, Sex.FEMALE, TriageLevel.IV, flu(), arrival, start, end,
                status, cause, care, false, 80.0);
    }

    @Test
    void buildsTheOutcomeOfAFinishedPatient() {
        Patient patient = patient(10);
        patient.startTreatment();
        patient.finish(PatientStatus.DISCHARGED, 50);

        PatientOutcome outcome = PatientOutcome.of(patient, 20, CompatibilityLevel.PRIMARY);

        assertEquals(PatientStatus.DISCHARGED, outcome.status());
        assertEquals(10, outcome.arrivalMinute());
        assertEquals(20, outcome.treatmentStartMinute());
        assertEquals(50, outcome.outcomeMinute());
        assertEquals(TriageLevel.IV, outcome.triage());
        assertEquals(CompatibilityLevel.PRIMARY, outcome.careLevel());
        assertNull(outcome.causeOfDeath());
    }

    @Test
    void carriesTheCauseOfDeath() {
        Patient patient = patient(0);
        patient.die(CauseOfDeath.LACK_OF_TIMELY_CARE, 30);

        PatientOutcome outcome = PatientOutcome.of(patient, -1, null);

        assertEquals(PatientStatus.DECEASED, outcome.status());
        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcome.causeOfDeath());
    }

    @Test
    void doesNotBuildTheOutcomeOfAPatientThatIsNotFinished() {
        assertThrows(IllegalStateException.class, () -> PatientOutcome.of(patient(0), -1, null));
    }

    @Test
    void waitingTimeCountsUntilTheTreatmentStarts() {
        PatientOutcome outcome = outcome(PatientStatus.DISCHARGED, null, 25, CompatibilityLevel.PRIMARY, 10, 90);

        assertEquals(15, outcome.waitingMinutes());
        assertTrue(outcome.wasTreated());
    }

    @Test
    void waitingTimeOfAnUntreatedPatientCountsUntilTheOutcome() {
        PatientOutcome outcome = outcome(PatientStatus.UNATTENDED, null, -1, null, 10, 70);

        assertEquals(60, outcome.waitingMinutes());
        assertFalse(outcome.wasTreated());
    }

    @Test
    void rejectsAnOutcomeThatIsNotFinal() {
        assertThrows(IllegalArgumentException.class,
                () -> outcome(PatientStatus.WAITING, null, -1, null, 0, 10));
    }

    @Test
    void rejectsADeathWithoutCause() {
        assertThrows(IllegalArgumentException.class,
                () -> outcome(PatientStatus.DECEASED, null, -1, null, 0, 10));
    }

    @Test
    void rejectsACauseOfDeathOnASurvivor() {
        assertThrows(IllegalArgumentException.class,
                () -> outcome(PatientStatus.DISCHARGED, CauseOfDeath.NO_ICU_BED, 5, CompatibilityLevel.PRIMARY, 0, 10));
    }

    @Test
    void rejectsACareLevelWithoutTreatment() {
        assertThrows(IllegalArgumentException.class,
                () -> outcome(PatientStatus.REFERRED, null, -1, CompatibilityLevel.PRIMARY, 0, 10));
    }

    @Test
    void rejectsATreatmentWithoutCareLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> outcome(PatientStatus.DISCHARGED, null, 5, null, 0, 10));
    }

    @Test
    void rejectsAnInvalidTreatmentStart() {
        assertThrows(IllegalArgumentException.class,
                () -> outcome(PatientStatus.DISCHARGED, null, -2, CompatibilityLevel.PRIMARY, 0, 10));
    }

    @Test
    void rejectsAnOutcomeBeforeTheArrival() {
        assertThrows(IllegalArgumentException.class,
                () -> outcome(PatientStatus.REFERRED, null, -1, null, 10, 9));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new PatientOutcome(1, null, 30, Sex.FEMALE,
                TriageLevel.IV, flu(), 0, -1, 10, PatientStatus.REFERRED, null, null, false, 80.0));
        assertThrows(IllegalArgumentException.class, () -> new PatientOutcome(1, "P-001", 30, null,
                TriageLevel.IV, flu(), 0, -1, 10, PatientStatus.REFERRED, null, null, false, 80.0));
        assertThrows(IllegalArgumentException.class, () -> new PatientOutcome(1, "P-001", 30, Sex.FEMALE,
                null, flu(), 0, -1, 10, PatientStatus.REFERRED, null, null, false, 80.0));
        assertThrows(IllegalArgumentException.class, () -> new PatientOutcome(1, "P-001", 30, Sex.FEMALE,
                TriageLevel.IV, null, 0, -1, 10, PatientStatus.REFERRED, null, null, false, 80.0));
        assertThrows(IllegalArgumentException.class, () -> new PatientOutcome(1, "P-001", 30, Sex.FEMALE,
                TriageLevel.IV, flu(), 0, -1, 10, null, null, null, false, 80.0));
    }
}
