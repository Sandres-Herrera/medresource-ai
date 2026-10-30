package com.medresource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Enfermedades de ejemplo para las pruebas de acceso a datos y de servicios.
 *
 * <p>No es una prueba: solo arma enfermedades y compara que dos tengan exactamente los mismos
 * datos (el {@code equals} de {@link Disease} solo mira el identificador).</p>
 */
public final class DiseaseFixtures {

    private DiseaseFixtures() {
    }

    /** Enfermedad completa: dos especialidades secundarias, tres síntomas, sangre y UCI. */
    public static Disease full(int id, String name) {
        return Disease.builder(id, name)
                .mainSpecialty(Specialty.INTERNAL_MEDICINE)
                .secondarySpecialties(Specialty.PEDIATRICS, Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.HOSPITALIZATION)
                .curable(false)
                .requiresBlood(true)
                .requiresIcu(true)
                .deteriorationRate(0.125)
                .irreducibleMortality(0.25)
                .durationMinutes(45, 120)
                .ageRange(2, 90)
                .frequency(7.5)
                .symptoms(Symptom.CHEST_PAIN, Symptom.FEVER, Symptom.COUGH)
                .build();
    }

    /** Enfermedad sencilla: sin especialidades secundarias, un síntoma, sin sangre ni UCI. */
    public static Disease simple(int id, String name) {
        return Disease.builder(id, name)
                .mainSpecialty(Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.CONSULTATION)
                .deteriorationRate(0.02)
                .irreducibleMortality(0.0)
                .durationMinutes(10, 20)
                .frequency(3)
                .symptoms(Symptom.FEVER)
                .build();
    }

    /** Comprueba que dos enfermedades tienen los mismos datos, en el mismo orden de síntomas. */
    public static void assertSameDisease(Disease expected, Disease actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getMainSpecialty(), actual.getMainSpecialty());
        assertEquals(expected.getSecondarySpecialties(), actual.getSecondarySpecialties());
        assertEquals(expected.getTreatment(), actual.getTreatment());
        assertEquals(expected.isCurable(), actual.isCurable());
        assertEquals(expected.requiresBlood(), actual.requiresBlood());
        assertEquals(expected.requiresIcu(), actual.requiresIcu());
        assertEquals(expected.getDeteriorationRate(), actual.getDeteriorationRate(), 0.0);
        assertEquals(expected.getIrreducibleMortality(), actual.getIrreducibleMortality(), 0.0);
        assertEquals(expected.getMinDurationMinutes(), actual.getMinDurationMinutes());
        assertEquals(expected.getMaxDurationMinutes(), actual.getMaxDurationMinutes());
        assertEquals(expected.getMinAge(), actual.getMinAge());
        assertEquals(expected.getMaxAge(), actual.getMaxAge());
        assertEquals(expected.getFrequency(), actual.getFrequency(), 0.0);
        assertEquals(expected.getSymptoms(), actual.getSymptoms());
    }
}