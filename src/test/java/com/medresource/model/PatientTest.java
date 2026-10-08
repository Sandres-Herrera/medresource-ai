package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del estado, la salud y el ciclo de vida de {@link Patient}.
 */
class PatientTest {

    /** Gripa fuerte: afecta de 0 a 100 años. */
    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    /** Bronquiolitis: solo afecta de 0 a 2 años. */
    private static final Disease BRONCHIOLITIS = DiseaseCatalog.findById(3).orElseThrow();

    private Patient newPatient() {
        return new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, FLU,
                List.of(Symptom.FEVER, Symptom.COUGH), 10, 80.0, false);
    }

    @Test
    void newPatientIsWaitingWithoutTriage() {
        Patient patient = newPatient();
        assertEquals("P001", patient.getCode());
        assertEquals(PatientStatus.WAITING, patient.getStatus());
        assertFalse(patient.hasTriage());
        assertNull(patient.getTriageLevel());
        assertEquals(-1, patient.getOutcomeMinute());
    }

    @Test
    void diseaseMustMatchTheAge() {
        assertThrows(IllegalArgumentException.class, () -> new Patient(2, 30, Sex.FEMALE,
                BloodType.A_POSITIVE, BRONCHIOLITIS, List.of(Symptom.COUGH), 0, 90.0, false));
    }

    @Test
    void patientWithoutSymptomsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Patient(2, 30, Sex.FEMALE,
                BloodType.A_POSITIVE, FLU, List.of(), 0, 90.0, false));
    }

    @Test
    void invalidInitialHealthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Patient(2, 30, Sex.FEMALE,
                BloodType.A_POSITIVE, FLU, List.of(Symptom.FEVER), 0, 0.0, false));
        assertThrows(IllegalArgumentException.class, () -> new Patient(2, 30, Sex.FEMALE,
                BloodType.A_POSITIVE, FLU, List.of(Symptom.FEVER), 0, 100.5, false));
    }

    @Test
    void triageCanBeAssigned() {
        Patient patient = newPatient();
        patient.assignTriage(TriageLevel.IV);
        assertTrue(patient.hasTriage());
        assertEquals("P001 · 30 años · Triage IV", patient.describe());
    }

    @Test
    void healthStaysBetweenZeroAndMaximum() {
        Patient patient = newPatient();
        patient.improve(50);
        assertEquals(Patient.MAX_HEALTH, patient.getHealth());
        patient.deteriorate(150);
        assertEquals(0.0, patient.getHealth());
        assertFalse(patient.isAlive());
    }

    @Test
    void negativeDeteriorationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> newPatient().deteriorate(-1));
    }

    @Test
    void waitingTimeCountsFromArrival() {
        Patient patient = newPatient();
        assertEquals(0, patient.waitingTime(10));
        assertEquals(25, patient.waitingTime(35));
        assertThrows(IllegalArgumentException.class, () -> patient.waitingTime(5));
    }

    @Test
    void treatmentAndDischargeFollowTheLifecycle() {
        Patient patient = newPatient();
        patient.startTreatment();
        assertEquals(PatientStatus.IN_TREATMENT, patient.getStatus());
        patient.finish(PatientStatus.DISCHARGED, 40);
        assertTrue(patient.isFinished());
        assertEquals(40, patient.getOutcomeMinute());
    }

    @Test
    void treatmentCannotStartTwice() {
        Patient patient = newPatient();
        patient.startTreatment();
        assertThrows(IllegalStateException.class, patient::startTreatment);
    }

    @Test
    void waitingPatientCanGoToIcu() {
        Patient patient = newPatient();
        patient.admitToIcu();
        assertEquals(PatientStatus.IN_ICU, patient.getStatus());
        assertThrows(IllegalStateException.class, patient::admitToIcu);
    }

    @Test
    void deathRecordsCauseAndMinute() {
        Patient patient = newPatient();
        patient.die(CauseOfDeath.NO_ICU_BED, 90);
        assertEquals(PatientStatus.DECEASED, patient.getStatus());
        assertEquals(CauseOfDeath.NO_ICU_BED, patient.getCauseOfDeath());
        assertEquals(90, patient.getOutcomeMinute());
        assertEquals(0.0, patient.getHealth());
        assertFalse(patient.isAlive());
    }

    @Test
    void finishedPatientCannotChange() {
        Patient patient = newPatient();
        patient.finish(PatientStatus.REFERRED, 20);
        assertThrows(IllegalStateException.class, () -> patient.deteriorate(5));
        assertThrows(IllegalStateException.class, () -> patient.die(CauseOfDeath.MISDIAGNOSIS, 30));
    }

    @Test
    void finishRejectsDeathAndNonFinalStatus() {
        Patient patient = newPatient();
        assertThrows(IllegalArgumentException.class, () -> patient.finish(PatientStatus.DECEASED, 20));
        assertThrows(IllegalArgumentException.class, () -> patient.finish(PatientStatus.WAITING, 20));
    }
}