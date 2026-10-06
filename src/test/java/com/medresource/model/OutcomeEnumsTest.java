package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las enumeraciones de desenlaces: {@link CauseOfDeath},
 * {@link PatientStatus} y {@link CompatibilityLevel}.
 */
class OutcomeEnumsTest {

    @Test
    void onlyIrreducibleMortalityIsNotAvoidable() {
        for (CauseOfDeath cause : CauseOfDeath.values()) {
            boolean expected = cause != CauseOfDeath.IRREDUCIBLE_MORTALITY;
            assertEquals(expected, cause.isAvoidable(), cause.name());
        }
    }

    @Test
    void waitingAndTreatmentAreNotFinalStatuses() {
        assertFalse(PatientStatus.WAITING.isFinal());
        assertFalse(PatientStatus.IN_TREATMENT.isFinal());
        assertFalse(PatientStatus.IN_ICU.isFinal());
    }

    @Test
    void deceasedAndDischargedAreFinalStatuses() {
        assertTrue(PatientStatus.DECEASED.isFinal());
        assertTrue(PatientStatus.DISCHARGED.isFinal());
    }

    @Test
    void notSuitableDoctorHasNoEffectiveness() {
        assertFalse(CompatibilityLevel.NOT_SUITABLE.isSuitable());
        assertEquals(0.0, CompatibilityLevel.NOT_SUITABLE.getEffectiveness());
    }

    @Test
    void primarySpecialistIsFullyEffective() {
        assertTrue(CompatibilityLevel.PRIMARY.isSuitable());
        assertEquals(1.0, CompatibilityLevel.PRIMARY.getEffectiveness());
    }
}