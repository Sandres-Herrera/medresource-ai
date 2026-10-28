package com.medresource.services.strategy;

import com.medresource.model.Disease;
import com.medresource.model.Patient;

/**
 * Paciente que espera atención, junto con el diagnóstico que la estrategia
 * debe usar.
 *
 * <p>Las estrategias deciden con el diagnóstico y no con la enfermedad real del
 * paciente, que solo conoce el simulador: así un diagnóstico equivocado puede
 * llevar a asignar un especialista que no es el ideal.</p>
 *
 * @param patient   paciente en espera
 * @param diagnosis enfermedad que se cree que tiene
 */
public record WaitingPatient(Patient patient, Disease diagnosis) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta el paciente o el diagnóstico
     */
    public WaitingPatient {
        if (patient == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        if (diagnosis == null) {
            throw new IllegalArgumentException("El diagnóstico es obligatorio.");
        }
    }
}