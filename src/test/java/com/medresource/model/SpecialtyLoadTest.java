package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la carga de una especialidad.
 */
class SpecialtyLoadTest {

    @Test
    void keepsTheLoadOfTheSpecialty() {
        SpecialtyLoad load = new SpecialtyLoad(Specialty.SURGERY, 3, 21, 0.88, 640);

        assertEquals(Specialty.SURGERY, load.specialty());
        assertEquals(3, load.doctors());
        assertEquals(640, load.doctorWaitMinutes());
    }

    @Test
    void rejectsIncoherentData() {
        assertThrows(IllegalArgumentException.class, () -> new SpecialtyLoad(null, 1, 1, 0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpecialtyLoad(Specialty.SURGERY, -1, 1, 0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpecialtyLoad(Specialty.SURGERY, 1, 1, 1.1, 0));
    }
}
