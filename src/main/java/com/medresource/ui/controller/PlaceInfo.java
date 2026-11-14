package com.medresource.ui.controller;

import com.medresource.model.TriageLevel;

import java.util.List;

/**
 * Lo que hay en un lugar del plano (una cama, una mesa de operaciones, un consultorio) en un instante de la
 * simulación: su nombre y los pacientes que están ahí, con desde hace cuánto y cuánto les falta.
 *
 * @param place     nombre del lugar, por ejemplo «Unidad de cuidados intensivos · UCI-C2»
 * @param occupants pacientes que están en ese lugar; vacía si está libre
 */
public record PlaceInfo(String place, List<Occupant> occupants) {

    /**
     * Valida la información y copia la lista.
     *
     * @throws IllegalArgumentException si falta el nombre del lugar o la lista
     */
    public PlaceInfo {
        if (place == null || place.isBlank() || occupants == null) {
            throw new IllegalArgumentException("Faltan datos del lugar.");
        }
        occupants = List.copyOf(occupants);
    }

    /** Indica si no hay nadie en el lugar. */
    public boolean isFree() {
        return occupants.isEmpty();
    }

    /**
     * Paciente que está en un lugar.
     *
     * @param patientCode      código del paciente
     * @param triage           nivel de triage; nulo si todavía no lo clasifican
     * @param activity         lo que está haciendo, por ejemplo «En tratamiento»
     * @param secondsThere     segundos simulados que lleva en su tratamiento; {@code NaN} si no se sabe
     * @param secondsRemaining segundos simulados que le faltan para terminarlo; {@code NaN} si no se sabe
     */
    public record Occupant(String patientCode, TriageLevel triage, String activity, double secondsThere,
                           double secondsRemaining) {

        /**
         * Valida el paciente.
         *
         * @throws IllegalArgumentException si falta el código o la actividad, o un tiempo conocido es negativo
         */
        public Occupant {
            if (patientCode == null || patientCode.isBlank() || activity == null) {
                throw new IllegalArgumentException("Faltan datos del paciente del lugar.");
            }
            if (secondsThere < 0 || secondsRemaining < 0) {
                throw new IllegalArgumentException("Los tiempos no pueden ser negativos.");
            }
        }

        /** Indica si se sabe desde hace cuánto está y cuánto le falta: solo durante su tratamiento. */
        public boolean hasTimes() {
            return !Double.isNaN(secondsThere) && !Double.isNaN(secondsRemaining);
        }
    }
}
