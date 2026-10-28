package com.medresource.model;

import java.util.List;

/**
 * Datos de un paciente tal como lo generó el caso, antes de que la simulación
 * lo modifique.
 *
 * <p>Un {@link Patient} cambia durante la simulación (salud, estado, triage).
 * Para poder repetir el mismo caso con otra estrategia, el caso guarda estos
 * datos inmutables y fabrica un paciente nuevo cada vez con {@link #toPatient()}.</p>
 *
 * @param id            identificador del paciente, también su orden de llegada
 * @param age           edad en años
 * @param sex           sexo biológico
 * @param bloodType     tipo de sangre
 * @param disease       enfermedad real
 * @param symptoms      síntomas visibles al llegar
 * @param arrivalMinute minuto de llegada
 * @param initialHealth salud al llegar
 * @param irrecoverable {@code true} si morirá aunque se le atienda
 */
public record PatientProfile(int id, int age, Sex sex, BloodType bloodType, Disease disease,
                             List<Symptom> symptoms, int arrivalMinute, double initialHealth,
                             boolean irrecoverable) {

    /**
     * Valida los datos con las mismas reglas de {@link Patient}.
     *
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public PatientProfile {
        // Se crea un paciente de prueba para reutilizar las validaciones de Patient.
        new Patient(id, age, sex, bloodType, disease, symptoms, arrivalMinute, initialHealth, irrecoverable);
        symptoms = List.copyOf(symptoms);
    }

    /**
     * Fabrica un paciente nuevo, en espera y sin triage, con estos datos.
     *
     * @return un paciente distinto en cada llamada
     */
    public Patient toPatient() {
        return new Patient(id, age, sex, bloodType, disease, symptoms, arrivalMinute, initialHealth, irrecoverable);
    }
}