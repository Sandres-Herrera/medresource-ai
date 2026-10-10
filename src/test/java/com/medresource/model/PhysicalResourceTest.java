package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las camas y los quirófanos.
 */
class PhysicalResourceTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    private Patient newPatient(int id) {
        return new Patient(id, 40, Sex.MALE, BloodType.B_POSITIVE, FLU,
                List.of(Symptom.FEVER), 0, 90.0, false);
    }

    @Test
    void bedKnowsIfItIsInTheIcu() {
        assertTrue(new Bed("UCI-C1", RoomType.ICU).isIcu());
        assertFalse(new Bed("H1-C1", RoomType.HOSPITALIZATION).isIcu());
    }

    @Test
    void bedWithoutCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Bed(" ", RoomType.HOSPITALIZATION));
    }

    @Test
    void assignedBedIsBusyUntilReleased() {
        Bed bed = new Bed("H1-C1", RoomType.HOSPITALIZATION);
        Patient patient = newPatient(1);
        bed.assign(patient, 120);
        assertFalse(bed.isAvailable());
        assertEquals(patient, bed.getAssignedPatient());
        assertEquals(120, bed.getBusyUntil());

        bed.release();
        assertTrue(bed.isAvailable());
    }

    @Test
    void occupiedBedCannotTakeAnotherPatient() {
        Bed bed = new Bed("H1-C1", RoomType.HOSPITALIZATION);
        bed.assign(newPatient(1), 120);
        assertThrows(IllegalStateException.class, () -> bed.assign(newPatient(2), 150));
    }

    @Test
    void bedKeepsADeceasedPatientUntilReleased() {
        Bed bed = new Bed("UCI-C1", RoomType.ICU);
        Patient patient = newPatient(1);
        bed.assign(patient, 200);
        patient.die(CauseOfDeath.IRREDUCIBLE_MORTALITY, 90);
        assertFalse(bed.isAvailable());
        bed.release();
        assertTrue(bed.isAvailable());
    }

    @Test
    void operatingRoomHasCodeAndDescription() {
        OperatingRoom operatingRoom = new OperatingRoom(2);
        assertEquals("Q2", operatingRoom.getCode());
        assertEquals("Q2 · Quirófano", operatingRoom.describe());
        assertTrue(operatingRoom.isAvailable());
    }

    @Test
    void operatingRoomNumberMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new OperatingRoom(0));
    }
}