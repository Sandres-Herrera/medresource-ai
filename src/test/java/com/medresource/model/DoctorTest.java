package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la compatibilidad entre la especialidad del médico y la enfermedad.
 */
class DoctorTest {

    /** Gripa fuerte: principal medicina general; secundarias pediatría y medicina interna. */
    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    /** Bronquiolitis: principal pediatría; de 0 a 2 años. */
    private static final Disease BRONCHIOLITIS = DiseaseCatalog.findById(3).orElseThrow();

    /** Infarto: principal medicina interna; secundaria medicina de urgencias. */
    private static final Disease HEART_ATTACK = DiseaseCatalog.findById(9).orElseThrow();

    private Doctor doctor(Specialty specialty) {
        return new Doctor(1, specialty, 45, Sex.MALE);
    }

    @Test
    void doctorHasCodeAndDescription() {
        Doctor doctor = new Doctor(3, Specialty.SURGERY, 50, Sex.FEMALE);
        assertEquals("D03", doctor.getCode());
        assertEquals("D03 · Cirugía", doctor.describe());
    }

    @Test
    void doctorWithoutSpecialtyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Doctor(1, null, 45, Sex.MALE));
    }

    @Test
    void mainSpecialtyIsPrimary() {
        assertEquals(CompatibilityLevel.PRIMARY,
                doctor(Specialty.INTERNAL_MEDICINE).compatibilityWith(HEART_ATTACK, 60));
        assertEquals(1.0, doctor(Specialty.INTERNAL_MEDICINE).effectivenessFor(HEART_ATTACK, 60));
    }

    @Test
    void secondarySpecialtyIsSecondary() {
        assertEquals(CompatibilityLevel.SECONDARY,
                doctor(Specialty.EMERGENCY_MEDICINE).compatibilityWith(HEART_ATTACK, 60));
        assertEquals(0.6, doctor(Specialty.EMERGENCY_MEDICINE).effectivenessFor(HEART_ATTACK, 60));
    }

    @Test
    void unrelatedSpecialtyIsNotSuitable() {
        Doctor traumatologist = doctor(Specialty.TRAUMATOLOGY);
        assertEquals(CompatibilityLevel.NOT_SUITABLE, traumatologist.compatibilityWith(HEART_ATTACK, 60));
        assertFalse(traumatologist.canTreat(HEART_ATTACK, 60));
        assertEquals(0.0, traumatologist.effectivenessFor(HEART_ATTACK, 60));
    }

    @Test
    void pediatricianTreatsOnlyMinors() {
        Doctor pediatrician = doctor(Specialty.PEDIATRICS);
        assertEquals(CompatibilityLevel.SECONDARY, pediatrician.compatibilityWith(FLU, 8));
        assertEquals(CompatibilityLevel.NOT_SUITABLE, pediatrician.compatibilityWith(FLU, 30));
        assertTrue(pediatrician.canTreat(BRONCHIOLITIS, 1));
    }

    @Test
    void nullDiseaseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> doctor(Specialty.SURGERY).compatibilityWith(null, 30));
    }
}