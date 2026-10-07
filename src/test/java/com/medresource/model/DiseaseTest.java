package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la construcción y validación de {@link Disease}.
 */
class DiseaseTest {

    private Disease.Builder validDisease() {
        return Disease.builder(99, "Enfermedad de prueba")
                .mainSpecialty(Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.CONSULTATION)
                .deteriorationRate(0.1)
                .irreducibleMortality(0.05)
                .durationMinutes(10, 20)
                .ageRange(10, 50)
                .symptoms(Symptom.FEVER, Symptom.BLEEDING);
    }

    @Test
    void validDiseaseIsBuilt() {
        Disease disease = validDisease().build();
        assertEquals("Enfermedad de prueba", disease.getName());
    }

    @Test
    void typicalTriageIsTheMostUrgentSymptom() {
        Disease disease = validDisease().build();
        assertEquals(TriageLevel.II, disease.typicalTriage());
    }

    @Test
    void affectsOnlyAgesWithinItsRange() {
        Disease disease = validDisease().build();
        assertTrue(disease.affectsAge(10));
        assertTrue(disease.affectsAge(50));
        assertFalse(disease.affectsAge(9));
        assertFalse(disease.affectsAge(51));
    }

    @Test
    void diseaseWithoutSymptomsIsRejected() {
        Disease.Builder builder = Disease.builder(99, "Sin síntomas")
                .mainSpecialty(Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.CONSULTATION)
                .durationMinutes(10, 20);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void minimumDurationGreaterThanMaximumIsRejected() {
        Disease.Builder builder = validDisease().durationMinutes(30, 20);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void mortalityAboveOneIsRejected() {
        Disease.Builder builder = validDisease().irreducibleMortality(1.5);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void mainSpecialtyCannotAlsoBeSecondary() {
        Disease.Builder builder = validDisease().secondarySpecialties(Specialty.GENERAL_MEDICINE);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void diseasesWithSameIdAreEqual() {
        Disease first = validDisease().build();
        Disease second = Disease.builder(99, "Otro nombre")
                .mainSpecialty(Specialty.SURGERY)
                .treatment(TreatmentType.SURGERY)
                .durationMinutes(5, 5)
                .symptoms(Symptom.COUGH)
                .build();
        assertEquals(first, second);
    }
}