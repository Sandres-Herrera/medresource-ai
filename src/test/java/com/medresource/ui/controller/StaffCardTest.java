package com.medresource.ui.controller;

import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la ficha de un médico o de un enfermero.
 */
class StaffCardTest {

    @Test
    void aDoctorShowsHisSpecialtyAndANurseShowsNursing() {
        StaffCard doctor = new StaffCard("D01", Specialty.SURGERY, Sex.MALE, "Libre", null, List.of());
        StaffCard nurse = new StaffCard("E01", null, Sex.FEMALE, "Libre", null, List.of());

        assertTrue(doctor.isDoctor());
        assertEquals("Cirugía", doctor.role());
        assertFalse(nurse.isDoctor());
        assertEquals("Enfermería", nurse.role());
    }

    @Test
    void keepsItsOwnCopyOfThePatients() {
        List<StaffCard.Attended> attended = new ArrayList<>();
        attended.add(new StaffCard.Attended("P001", TriageLevel.II, 3, "En tratamiento"));
        StaffCard card = new StaffCard("D01", Specialty.SURGERY, Sex.MALE, "Atiende a P001", "P001", attended);

        attended.clear();

        assertEquals(1, card.attended().size());
        assertThrows(UnsupportedOperationException.class, () -> card.attended().clear());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class,
                () -> new StaffCard(" ", null, Sex.MALE, "Libre", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StaffCard("D01", null, null, "Libre", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StaffCard("D01", null, Sex.MALE, null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new StaffCard("D01", null, Sex.MALE, "Libre", null, null));
        assertThrows(IllegalArgumentException.class, () -> new StaffCard.Attended(null, null, 0, "Libre"));
        assertThrows(IllegalArgumentException.class, () -> new StaffCard.Attended("P001", null, -1, "Libre"));
        assertThrows(IllegalArgumentException.class, () -> new StaffCard.Attended("P001", null, 0, null));
    }
}
