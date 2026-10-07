package com.medresource.model;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo inicial de enfermedades que atiende el hospital simulado.
 *
 * <p>Sirve como datos de partida: más adelante este catálogo se guarda en la
 * base de datos y se puede administrar desde la aplicación.</p>
 */
public final class DiseaseCatalog {

    private static final List<Disease> DEFAULT_DISEASES = List.of(
            Disease.builder(1, "Gripa fuerte")
                    .mainSpecialty(Specialty.GENERAL_MEDICINE)
                    .secondarySpecialties(Specialty.PEDIATRICS, Specialty.INTERNAL_MEDICINE)
                    .treatment(TreatmentType.CONSULTATION)
                    .deteriorationRate(0.02)
                    .irreducibleMortality(0.0)
                    .durationMinutes(15, 30)
                    .frequency(30)
                    .symptoms(Symptom.FEVER, Symptom.COUGH, Symptom.CONGESTION)
                    .build(),

            Disease.builder(2, "Síndrome febril (dengue)")
                    .mainSpecialty(Specialty.INTERNAL_MEDICINE)
                    .secondarySpecialties(Specialty.PEDIATRICS)
                    .treatment(TreatmentType.HOSPITALIZATION)
                    .deteriorationRate(0.10)
                    .irreducibleMortality(0.01)
                    .durationMinutes(60, 180)
                    .frequency(12)
                    .symptoms(Symptom.HIGH_FEVER, Symptom.GENERALIZED_PAIN, Symptom.FATIGUE)
                    .build(),

            Disease.builder(3, "Bronquiolitis")
                    .mainSpecialty(Specialty.PEDIATRICS)
                    .secondarySpecialties(Specialty.GENERAL_MEDICINE)
                    .treatment(TreatmentType.HOSPITALIZATION)
                    .deteriorationRate(0.08)
                    .irreducibleMortality(0.01)
                    .durationMinutes(60, 180)
                    .ageRange(0, 2)
                    .frequency(5)
                    .symptoms(Symptom.FEVER, Symptom.COUGH, Symptom.FATIGUE)
                    .build(),

            Disease.builder(4, "Neumonía")
                    .mainSpecialty(Specialty.INTERNAL_MEDICINE)
                    .secondarySpecialties(Specialty.GENERAL_MEDICINE, Specialty.PEDIATRICS)
                    .treatment(TreatmentType.HOSPITALIZATION)
                    .deteriorationRate(0.12)
                    .irreducibleMortality(0.03)
                    .durationMinutes(90, 240)
                    .frequency(10)
                    .symptoms(Symptom.HIGH_FEVER, Symptom.COUGH, Symptom.FATIGUE)
                    .build(),

            Disease.builder(5, "Fractura simple")
                    .mainSpecialty(Specialty.TRAUMATOLOGY)
                    .secondarySpecialties(Specialty.GENERAL_MEDICINE, Specialty.EMERGENCY_MEDICINE)
                    .treatment(TreatmentType.CONSULTATION)
                    .deteriorationRate(0.03)
                    .irreducibleMortality(0.0)
                    .durationMinutes(30, 60)
                    .frequency(12)
                    .symptoms(Symptom.LIMB_DEFORMITY, Symptom.LEG_PAIN)
                    .build(),

            Disease.builder(6, "Trombosis venosa profunda")
                    .mainSpecialty(Specialty.INTERNAL_MEDICINE)
                    .treatment(TreatmentType.HOSPITALIZATION)
                    .deteriorationRate(0.15)
                    .irreducibleMortality(0.02)
                    .durationMinutes(60, 150)
                    .ageRange(20, 100)
                    .frequency(5)
                    .symptoms(Symptom.LEG_PAIN, Symptom.UNABLE_TO_WALK)
                    .build(),

            Disease.builder(7, "Fractura expuesta")
                    .mainSpecialty(Specialty.TRAUMATOLOGY)
                    .secondarySpecialties(Specialty.SURGERY)
                    .treatment(TreatmentType.SURGERY)
                    .requiresBlood(true)
                    .deteriorationRate(0.35)
                    .irreducibleMortality(0.05)
                    .durationMinutes(90, 200)
                    .frequency(4)
                    .symptoms(Symptom.EXPOSED_BONE, Symptom.BLEEDING, Symptom.LIMB_DEFORMITY, Symptom.LEG_PAIN)
                    .build(),

            Disease.builder(8, "Apendicitis")
                    .mainSpecialty(Specialty.SURGERY)
                    .treatment(TreatmentType.SURGERY)
                    .deteriorationRate(0.20)
                    .irreducibleMortality(0.02)
                    .durationMinutes(60, 120)
                    .ageRange(5, 60)
                    .frequency(6)
                    .symptoms(Symptom.ABDOMINAL_PAIN, Symptom.FEVER, Symptom.FATIGUE)
                    .build(),

            Disease.builder(9, "Infarto")
                    .mainSpecialty(Specialty.INTERNAL_MEDICINE)
                    .secondarySpecialties(Specialty.EMERGENCY_MEDICINE)
                    .treatment(TreatmentType.HOSPITALIZATION)
                    .requiresIcu(true)
                    .deteriorationRate(0.60)
                    .irreducibleMortality(0.25)
                    .durationMinutes(90, 240)
                    .ageRange(35, 100)
                    .frequency(4)
                    .symptoms(Symptom.CHEST_PAIN, Symptom.BREATHING_DIFFICULTY, Symptom.FATIGUE)
                    .build(),

            Disease.builder(10, "Trauma craneal grave")
                    .mainSpecialty(Specialty.SURGERY)
                    .secondarySpecialties(Specialty.EMERGENCY_MEDICINE)
                    .treatment(TreatmentType.SURGERY)
                    .requiresBlood(true)
                    .requiresIcu(true)
                    .deteriorationRate(0.80)
                    .irreducibleMortality(0.30)
                    .durationMinutes(120, 300)
                    .frequency(3)
                    .symptoms(Symptom.UNCONSCIOUS, Symptom.BLEEDING)
                    .build(),

            Disease.builder(11, "Cáncer avanzado")
                    .mainSpecialty(Specialty.INTERNAL_MEDICINE)
                    .treatment(TreatmentType.HOSPITALIZATION)
                    .curable(false)
                    .deteriorationRate(0.05)
                    .irreducibleMortality(0.0)
                    .durationMinutes(120, 240)
                    .ageRange(30, 100)
                    .frequency(4)
                    .symptoms(Symptom.FATIGUE, Symptom.WEIGHT_LOSS, Symptom.GENERALIZED_PAIN)
                    .build()
    );

    private DiseaseCatalog() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Devuelve las enfermedades del catálogo inicial.
     *
     * @return lista inmodificable de enfermedades
     */
    public static List<Disease> defaultDiseases() {
        return DEFAULT_DISEASES;
    }

    /**
     * Busca una enfermedad del catálogo inicial por su identificador.
     *
     * @param id identificador de la enfermedad
     * @return la enfermedad, o vacío si no existe
     */
    public static Optional<Disease> findById(int id) {
        return DEFAULT_DISEASES.stream()
                .filter(disease -> disease.getId() == id)
                .findFirst();
    }
}