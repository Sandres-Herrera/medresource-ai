package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la morgue: cupos finitos y retiro de cuerpos.
 */
class MorgueTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    private Patient deceasedPatient(int id) {
        Patient patient = new Patient(id, 60, Sex.MALE, BloodType.O_POSITIVE, FLU,
                List.of(Symptom.FEVER), 0, 50.0, false);
        patient.die(CauseOfDeath.LACK_OF_TIMELY_CARE, 10);
        return patient;
    }

    @Test
    void newMorgueIsEmpty() {
        Morgue morgue = new Morgue(3);
        assertEquals(3, morgue.getCapacity());
        assertEquals(0, morgue.occupancy());
        assertTrue(morgue.hasSpace());
    }

    @Test
    void capacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new Morgue(0));
    }

    @Test
    void onlyDeceasedPatientsAreAdmitted() {
        Patient alive = new Patient(1, 60, Sex.MALE, BloodType.O_POSITIVE, FLU,
                List.of(Symptom.FEVER), 0, 50.0, false);
        assertThrows(IllegalArgumentException.class, () -> new Morgue(2).admit(alive, 20));
    }

    @Test
    void morgueFillsUpAndRejectsMoreBodies() {
        Morgue morgue = new Morgue(1);
        morgue.admit(deceasedPatient(1), 20);
        assertFalse(morgue.hasSpace());
        assertEquals(1, morgue.occupancy());
        assertThrows(IllegalStateException.class, () -> morgue.admit(deceasedPatient(2), 25));
    }

    @Test
    void samePatientCannotBeAdmittedTwice() {
        Morgue morgue = new Morgue(3);
        Patient patient = deceasedPatient(1);
        morgue.admit(patient, 20);
        assertThrows(IllegalStateException.class, () -> morgue.admit(patient, 30));
    }

    @Test
    void bodiesAreReleasedWhenTheirTimeIsUp() {
        Morgue morgue = new Morgue(2);
        Patient first = deceasedPatient(1);
        Patient second = deceasedPatient(2);
        morgue.admit(first, 100);
        morgue.admit(second, 150);

        assertTrue(morgue.releaseDue(100 + Morgue.RETENTION_MINUTES - 1).isEmpty());
        assertEquals(List.of(first), morgue.releaseDue(100 + Morgue.RETENTION_MINUTES));
        assertEquals(1, morgue.occupancy());
        assertEquals(List.of(second), morgue.releaseDue(150 + Morgue.RETENTION_MINUTES));
        assertEquals(0, morgue.occupancy());
    }

    @Test
    void releasingABodyFreesASpace() {
        Morgue morgue = new Morgue(1);
        morgue.admit(deceasedPatient(1), 0);
        morgue.releaseDue(Morgue.RETENTION_MINUTES);
        assertTrue(morgue.hasSpace());
        morgue.admit(deceasedPatient(2), Morgue.RETENTION_MINUTES);
        assertEquals(1, morgue.occupancy());
    }

    @Test
    void bodyListCannotBeModified() {
        Morgue morgue = new Morgue(2);
        morgue.admit(deceasedPatient(1), 0);
        assertThrows(UnsupportedOperationException.class, () -> morgue.getBodies().clear());
        assertEquals(1, morgue.occupancy());
    }
}