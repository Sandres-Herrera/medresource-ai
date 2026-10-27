package com.medresource.model;

import java.util.List;

/**
 * Caso de simulación: la configuración y los pacientes que van a llegar.
 *
 * <p>Es inmutable. Cada ejecución de una estrategia pide sus propios pacientes
 * con {@link #createPatients()}, así todas parten de la misma situación.</p>
 *
 * @param config   configuración con la que se generó el caso
 * @param profiles datos de los pacientes, ordenados por minuto de llegada
 */
public record SimulationCase(CaseConfig config, List<PatientProfile> profiles) {

    /**
     * Valida el caso.
     *
     * @throws IllegalArgumentException si falta algún dato, si la cantidad de
     *                                  pacientes no coincide con la configuración
     *                                  o si no están ordenados por llegada
     */
    public SimulationCase {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del caso es obligatoria.");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("La lista de pacientes es obligatoria.");
        }
        profiles = List.copyOf(profiles);
        if (profiles.size() != config.patientCount()) {
            throw new IllegalArgumentException("El caso debe tener " + config.patientCount() + " pacientes.");
        }
        for (int i = 1; i < profiles.size(); i++) {
            if (profiles.get(i).arrivalMinute() < profiles.get(i - 1).arrivalMinute()) {
                throw new IllegalArgumentException("Los pacientes deben estar ordenados por minuto de llegada.");
            }
        }
    }

    /**
     * Crea pacientes nuevos para una ejecución de la simulación.
     *
     * @return lista de pacientes en espera, ordenada por llegada; distinta en cada llamada
     */
    public List<Patient> createPatients() {
        return profiles.stream().map(PatientProfile::toPatient).toList();
    }
}