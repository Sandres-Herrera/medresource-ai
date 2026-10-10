package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las habitaciones y sus camas.
 */
class RoomTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    private Patient newPatient(int id) {
        return new Patient(id, 40, Sex.FEMALE, BloodType.O_NEGATIVE, FLU,
                List.of(Symptom.FEVER), 0, 90.0, false);
    }

    @Test
    void roomCreatesItsBedsWithCodes() {
        Room room = new Room("H1", RoomType.HOSPITALIZATION, 3);
        assertEquals(3, room.getCapacity());
        assertEquals("H1-C1", room.getBeds().get(0).getCode());
        assertEquals("H1-C3", room.getBeds().get(2).getCode());
    }

    @Test
    void icuRoomCreatesIcuBeds() {
        Room icu = new Room("UCI", RoomType.ICU, 2);
        assertTrue(icu.getBeds().stream().allMatch(Bed::isIcu));
    }

    @Test
    void freeBedsChangeWhenBedsAreAssigned() {
        Room room = new Room("H1", RoomType.HOSPITALIZATION, 2);
        room.getBeds().get(0).assign(newPatient(1), 60);
        assertEquals(1, room.freeBeds().size());
        assertEquals(1, room.occupiedBedCount());
        assertFalse(room.isFull());

        room.getBeds().get(1).assign(newPatient(2), 60);
        assertTrue(room.isFull());
        assertEquals("H1 · Hospitalización · 2/2 camas ocupadas", room.describe());
    }

    @Test
    void roomWithoutBedsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Room("H1", RoomType.HOSPITALIZATION, 0));
    }

    @Test
    void bedListCannotBeModified() {
        Room room = new Room("H1", RoomType.HOSPITALIZATION, 1);
        assertThrows(UnsupportedOperationException.class,
                () -> room.getBeds().add(new Bed("X", RoomType.HOSPITALIZATION)));
    }
}