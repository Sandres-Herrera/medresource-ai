package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la carga de trabajo del personal.
 */
class StaffLoadTest {

    @Test
    void aDoctorBusyAlmostAllTheTimeIsSaturated() {
        StaffLoad load = new StaffLoad("D02", Specialty.SURGERY, 9, 900, 0.9);

        assertTrue(load.isDoctor());
        assertTrue(load.isSaturated());
        assertFalse(load.isIdle());
    }

    @Test
    void aNurseRarelyBusyIsIdle() {
        StaffLoad load = new StaffLoad("E05", null, 1, 60, 0.1);

        assertFalse(load.isDoctor());
        assertFalse(load.isSaturated());
        assertTrue(load.isIdle());
    }

    @Test
    void rejectsIncoherentData() {
        assertThrows(IllegalArgumentException.class, () -> new StaffLoad(" ", null, 0, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new StaffLoad("D01", null, -1, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new StaffLoad("D01", null, 0, 0, 1.5));
    }
}
