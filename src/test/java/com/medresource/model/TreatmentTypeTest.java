package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del tiempo que el personal pasa con el paciente según el tipo de tratamiento.
 */
class TreatmentTypeTest {

    @Test
    void aHospitalizedPatientKeepsTheStaffOnlyDuringTheAdmission() {
        assertEquals(30, TreatmentType.INITIAL_CARE_MINUTES);
        assertEquals(30, TreatmentType.HOSPITALIZATION.staffMinutes(240));
        assertEquals(30, TreatmentType.HOSPITALIZATION.staffMinutes(31));
    }

    @Test
    void aShortHospitalizationKeepsTheStaffTheWholeTime() {
        assertEquals(20, TreatmentType.HOSPITALIZATION.staffMinutes(20));
        assertEquals(30, TreatmentType.HOSPITALIZATION.staffMinutes(30));
    }

    @Test
    void consultationsAndSurgeriesKeepTheStaffTheWholeTime() {
        assertEquals(25, TreatmentType.CONSULTATION.staffMinutes(25));
        assertEquals(200, TreatmentType.SURGERY.staffMinutes(200));
    }

    @Test
    void rejectsADurationOfLessThanOneMinute() {
        assertThrows(IllegalArgumentException.class, () -> TreatmentType.HOSPITALIZATION.staffMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> TreatmentType.SURGERY.staffMinutes(-5));
    }
}
