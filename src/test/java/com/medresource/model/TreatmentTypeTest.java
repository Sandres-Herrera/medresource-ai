package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void aConsultationKeepsTheStaffTheWholeTime() {
        assertEquals(25, TreatmentType.CONSULTATION.staffMinutes(25));
    }

    @Test
    void aSurgeryKeepsTheStaffOnlyDuringTheOperation() {
        assertEquals(100, TreatmentType.SURGERY.staffMinutes(200));
        assertEquals(TreatmentType.SURGERY.operationMinutes(91), TreatmentType.SURGERY.staffMinutes(91));
    }

    @Test
    void rejectsADurationOfLessThanOneMinute() {
        assertThrows(IllegalArgumentException.class, () -> TreatmentType.HOSPITALIZATION.staffMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> TreatmentType.SURGERY.staffMinutes(-5));
    }

    @Test
    void onlyASurgeryHasARecovery() {
        assertTrue(TreatmentType.SURGERY.hasRecovery());
        assertFalse(TreatmentType.HOSPITALIZATION.hasRecovery());
        assertFalse(TreatmentType.CONSULTATION.hasRecovery());
    }

    @Test
    void theOperationTakesHalfOfTheSurgeryRoundedUp() {
        assertEquals(0.5, TreatmentType.OPERATION_SHARE, 0.0);
        assertEquals(60, TreatmentType.SURGERY.operationMinutes(120));
        assertEquals(46, TreatmentType.SURGERY.operationMinutes(91));
        assertEquals(1, TreatmentType.SURGERY.operationMinutes(1));
    }

    @Test
    void onlyASurgeryHasAnOperation() {
        assertThrows(IllegalStateException.class, () -> TreatmentType.HOSPITALIZATION.operationMinutes(60));
        assertThrows(IllegalStateException.class, () -> TreatmentType.CONSULTATION.operationMinutes(20));
        assertThrows(IllegalArgumentException.class, () -> TreatmentType.SURGERY.operationMinutes(0));
    }
}
