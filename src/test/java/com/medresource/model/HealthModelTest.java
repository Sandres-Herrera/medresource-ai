package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las reglas de salud de los pacientes.
 */
class HealthModelTest {

    /** Dengue: deterioro 0,10 por minuto y tratamiento de 60 a 180 minutos. */
    private static final Disease DENGUE = DiseaseCatalog.findById(2).orElseThrow();

    private final HealthModel model = new HealthModel();

    private Patient patient(int age, Sex sex, double health) {
        return new Patient(1, age, sex, BloodType.O_POSITIVE, DENGUE, DENGUE.getSymptoms(), 0, health, false);
    }

    private static void assertClose(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) < 1e-9, "esperado " + expected + " pero fue " + actual);
    }

    @Test
    void anUnattendedPatientLosesTheDiseaseRate() {
        assertClose(0.10, model.deteriorationPerMinute(patient(30, Sex.MALE, 80), 0.0));
    }

    @Test
    void thePrimarySpecialistStopsTheDeterioration() {
        assertClose(0.0, model.deteriorationPerMinute(patient(30, Sex.MALE, 80), 1.0));
    }

    @Test
    void aSecondarySpecialistOnlyReducesTheDeterioration() {
        assertClose(0.04, model.deteriorationPerMinute(patient(30, Sex.MALE, 80), 0.6));
    }

    @Test
    void babiesAndOlderAdultsDeteriorateFaster() {
        assertClose(0.125, model.deteriorationPerMinute(patient(4, Sex.MALE, 80), 0.0));
        assertClose(0.125, model.deteriorationPerMinute(patient(65, Sex.MALE, 80), 0.0));
        assertClose(0.10, model.deteriorationPerMinute(patient(5, Sex.MALE, 80), 0.0));
        assertClose(0.10, model.deteriorationPerMinute(patient(64, Sex.MALE, 80), 0.0));
    }

    @Test
    void sexNeverChangesTheDeterioration() {
        for (int age : new int[]{2, 30, 70}) {
            assertClose(model.deteriorationPerMinute(patient(age, Sex.MALE, 80), 0.0),
                    model.deteriorationPerMinute(patient(age, Sex.FEMALE, 80), 0.0));
        }
    }

    @Test
    void aHealthyPatientFinishesInTheMinimumDuration() {
        assertEquals(60, model.treatmentMinutes(patient(30, Sex.MALE, 100), 1.0));
    }

    @Test
    void aVerySickPatientTakesAlmostTheMaximumDuration() {
        assertEquals(179, model.treatmentMinutes(patient(30, Sex.MALE, 1), 1.0));
    }

    @Test
    void treatmentMinutesAreNotInflatedByDecimalRounding() {
        // Gripa: 15 + (30 - 15) * 0,04 = 15,6 minutos base; entre 0,6 son exactamente 26.
        Disease flu = DiseaseCatalog.findById(1).orElseThrow();
        Patient patient = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, flu, flu.getSymptoms(), 0, 96.0, false);

        assertEquals(26, model.treatmentMinutes(patient, 0.6));
    }

    @Test
    void theSickerThePatientTheLongerTheTreatment() {
        assertTrue(model.treatmentMinutes(patient(30, Sex.MALE, 40), 1.0)
                > model.treatmentMinutes(patient(30, Sex.MALE, 90), 1.0));
    }

    @Test
    void aSecondarySpecialistTakesLonger() {
        Patient patient = patient(30, Sex.MALE, 100);

        assertEquals(60, model.treatmentMinutes(patient, 1.0));
        assertEquals(100, model.treatmentMinutes(patient, 0.6));
    }

    @Test
    void thePrimarySpecialistRestoresAllTheMissingHealth() {
        assertClose(30.0, model.recoveryPoints(patient(30, Sex.MALE, 70), 1.0));
    }

    @Test
    void aSecondarySpecialistRestoresOnlyPartOfIt() {
        assertClose(18.0, model.recoveryPoints(patient(30, Sex.MALE, 70), 0.6));
    }

    @Test
    void noAttentionRestoresNothing() {
        assertClose(0.0, model.recoveryPoints(patient(30, Sex.MALE, 70), 0.0));
    }

    @Test
    void rejectsAMissingPatient() {
        assertThrows(IllegalArgumentException.class, () -> model.deteriorationPerMinute(null, 0.5));
        assertThrows(IllegalArgumentException.class, () -> model.treatmentMinutes(null, 0.5));
        assertThrows(IllegalArgumentException.class, () -> model.recoveryPoints(null, 0.5));
    }

    @Test
    void rejectsAnEffectivenessOutOfRange() {
        Patient patient = patient(30, Sex.MALE, 80);

        assertThrows(IllegalArgumentException.class, () -> model.deteriorationPerMinute(patient, -0.1));
        assertThrows(IllegalArgumentException.class, () -> model.deteriorationPerMinute(patient, 1.1));
        assertThrows(IllegalArgumentException.class, () -> model.recoveryPoints(patient, 1.5));
        assertThrows(IllegalArgumentException.class, () -> model.treatmentMinutes(patient, 0.0));
        assertThrows(IllegalArgumentException.class, () -> model.treatmentMinutes(patient, 1.2));
    }

    @Test
    void estimatesWithADiseaseAndAgeGiveTheSameAsWithAPatient() {
        Patient adult = patient(30, Sex.MALE, 40.0);
        Patient baby = patient(2, Sex.MALE, 40.0);

        assertClose(model.deteriorationPerMinute(adult, 0.6), model.deteriorationPerMinute(DENGUE, 30, 0.6));
        assertClose(model.deteriorationPerMinute(baby, 0.0), model.deteriorationPerMinute(DENGUE, 2, 0.0));
        assertEquals(model.treatmentMinutes(adult, 0.6), model.treatmentMinutes(DENGUE, 40.0, 0.6));
    }

    @Test
    void estimatesWithADiseaseRejectInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> model.deteriorationPerMinute(null, 30, 0.5));
        assertThrows(IllegalArgumentException.class, () -> model.deteriorationPerMinute(DENGUE, -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> model.deteriorationPerMinute(DENGUE, 30, 1.1));
        assertThrows(IllegalArgumentException.class, () -> model.treatmentMinutes(null, 50.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> model.treatmentMinutes(DENGUE, -0.1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> model.treatmentMinutes(DENGUE, 100.1, 1.0));
        assertThrows(IllegalArgumentException.class, () -> model.treatmentMinutes(DENGUE, 50.0, 0.0));
    }

    @Test
    void estimatesAcceptTheLimitsOfHealth() {
        assertEquals(60, model.treatmentMinutes(DENGUE, 100.0, 1.0));
        assertEquals(180, model.treatmentMinutes(DENGUE, 0.0, 1.0));
    }
}
