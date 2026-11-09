package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los puestos del personal.
 */
class StaffSeatTest {

    @Test
    void keepsItsTableAndSide() {
        StaffSeat seat = new StaffSeat(new Position(3, 4), 1);

        assertEquals(new Position(3, 4), seat.table());
        assertEquals(1, seat.side());
        assertEquals(2, StaffSeat.SEATS_PER_TABLE);
    }

    @Test
    void needsATable() {
        assertThrows(IllegalArgumentException.class, () -> new StaffSeat(null, 0));
    }

    @Test
    void aTableHasOnlyALeftAndARightSeat() {
        assertThrows(IllegalArgumentException.class, () -> new StaffSeat(new Position(0, 0), -1));
        assertThrows(IllegalArgumentException.class, () -> new StaffSeat(new Position(0, 0), 2));
    }
}
