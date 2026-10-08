package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la asignación de personal, usando {@link Nurse}.
 */
class StaffMemberTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    private Patient newPatient(int id) {
        return new Patient(id, 40, Sex.FEMALE, BloodType.A_POSITIVE, FLU,
                List.of(Symptom.FEVER), 0, 90.0, false);
    }

    @Test
    void newNurseIsAvailable() {
        Nurse nurse = new Nurse(1, 28, Sex.FEMALE);
        assertEquals("E01", nurse.getCode());
        assertTrue(nurse.isAvailable());
        assertNull(nurse.getAssignedPatient());
        assertEquals(-1, nurse.getBusyUntil());
    }

    @Test
    void staffUnderEighteenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Nurse(1, 17, Sex.MALE));
    }

    @Test
    void assignedNurseIsBusyUntilReleased() {
        Nurse nurse = new Nurse(1, 28, Sex.FEMALE);
        Patient patient = newPatient(1);
        nurse.assign(patient, 60);
        assertFalse(nurse.isAvailable());
        assertEquals(patient, nurse.getAssignedPatient());
        assertEquals(60, nurse.getBusyUntil());

        nurse.release();
        assertTrue(nurse.isAvailable());
        assertEquals(-1, nurse.getBusyUntil());
    }

    @Test
    void busyNurseCannotTakeAnotherPatient() {
        Nurse nurse = new Nurse(1, 28, Sex.FEMALE);
        nurse.assign(newPatient(1), 60);
        assertThrows(IllegalStateException.class, () -> nurse.assign(newPatient(2), 90));
    }

    @Test
    void releasingAFreeNurseIsRejected() {
        Nurse nurse = new Nurse(1, 28, Sex.FEMALE);
        assertThrows(IllegalStateException.class, nurse::release);
    }

    @Test
    void finishedPatientCannotBeAssigned() {
        Nurse nurse = new Nurse(1, 28, Sex.FEMALE);
        Patient patient = newPatient(1);
        patient.die(CauseOfDeath.LACK_OF_TIMELY_CARE, 30);
        assertThrows(IllegalArgumentException.class, () -> nurse.assign(patient, 60));
    }

    @Test
    void negativeReleaseMinuteIsRejected() {
        Nurse nurse = new Nurse(1, 28, Sex.FEMALE);
        assertThrows(IllegalArgumentException.class, () -> nurse.assign(newPatient(1), -5));
    }
}