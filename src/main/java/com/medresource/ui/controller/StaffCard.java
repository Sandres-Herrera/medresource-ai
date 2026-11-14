package com.medresource.ui.controller;

import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;

import java.util.List;

/**
 * Ficha de un médico o de un enfermero tal como se ve en un instante de la simulación: quién es, su especialidad,
 * qué está haciendo y los pacientes que ha atendido hasta ese momento, en el orden en que empezó a atenderlos.
 *
 * @param code           código del médico o del enfermero, por ejemplo «D03»
 * @param specialty      especialidad del médico; nula para un enfermero
 * @param sex            sexo
 * @param activity       lo que está haciendo, por ejemplo «Atiende a P012», «Caminando» o «Libre»
 * @param currentPatient código del paciente que atiende ahora; nulo si no atiende a nadie
 * @param attended       pacientes que ha atendido hasta ahora, el primero de primero
 */
public record StaffCard(String code, Specialty specialty, Sex sex, String activity, String currentPatient,
                        List<Attended> attended) {

    /**
     * Valida la ficha y copia la lista.
     *
     * @throws IllegalArgumentException si falta el código, el sexo, la actividad o la lista
     */
    public StaffCard {
        if (code == null || code.isBlank() || sex == null || activity == null || attended == null) {
            throw new IllegalArgumentException("Faltan datos de la ficha del personal.");
        }
        attended = List.copyOf(attended);
    }

    /** Indica si es un médico: solo los médicos tienen especialidad. */
    public boolean isDoctor() {
        return specialty != null;
    }

    /** Oficio que se muestra en la ficha: la especialidad del médico o «Enfermería». */
    public String role() {
        return isDoctor() ? specialty.getDisplayName() : "Enfermería";
    }

    /**
     * Paciente que atendió un médico o un enfermero.
     *
     * @param patientCode código del paciente
     * @param triage      nivel de triage del paciente; nulo si todavía no lo clasificaban
     * @param minute      minuto en que empezó a atenderlo
     * @param status      cómo está ahora, por ejemplo «En tratamiento», «Dado de alta» o «Fallecido»
     */
    public record Attended(String patientCode, TriageLevel triage, int minute, String status) {

        /**
         * Valida el paciente atendido.
         *
         * @throws IllegalArgumentException si falta el código o el estado, o el minuto es negativo
         */
        public Attended {
            if (patientCode == null || patientCode.isBlank() || status == null) {
                throw new IllegalArgumentException("Faltan datos del paciente atendido.");
            }
            if (minute < 0) {
                throw new IllegalArgumentException("El minuto no puede ser negativo.");
            }
        }
    }
}
