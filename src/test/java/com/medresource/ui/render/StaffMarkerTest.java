package com.medresource.ui.render;

import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del marcador de un médico o enfermero.
 */
class StaffMarkerTest {

    @Test
    void aDoctorHasASpecialtyAndANurseDoesNot() {
        assertTrue(new StaffMarker("D01", Specialty.SURGERY, Sex.MALE, true, 1, 2).isDoctor());
        assertFalse(new StaffMarker("E01", null, Sex.FEMALE, true, 1, 2).isDoctor());
    }

    @Test
    void needsACodeAndASex() {
        assertThrows(IllegalArgumentException.class, () -> new StaffMarker(null, null, Sex.MALE, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StaffMarker(" ", null, Sex.MALE, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StaffMarker("E01", null, null, true, 0, 0));
    }

    @Test
    void keepsHowMuchTheFigureBobs() {
        assertEquals(0.8, new StaffMarker("E01", null, Sex.FEMALE, true, 1, 2, 0.8).bob(), 0.0);
        assertEquals(0.0, new StaffMarker("E01", null, Sex.FEMALE, true, 1, 2).bob(), 0.0);
    }
}
