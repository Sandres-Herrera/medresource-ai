package com.medresource.services.simulation;

import com.medresource.model.Patient;
import com.medresource.model.Symptom;
import com.medresource.model.TriageLevel;

import java.util.Comparator;

/**
 * Clasifica a los pacientes según el triage de la Resolución 5596 de 2015.
 *
 * <p>El nivel depende solo de los síntomas que el paciente presenta: el del
 * síntoma más grave. La edad, el sexo y la enfermedad real (que el paciente
 * no muestra) nunca cambian el resultado.</p>
 */
public class TriageService {

    /**
     * Calcula el nivel de triage de un paciente sin modificarlo; quien llama
     * decide cuándo asignarlo con {@link Patient#assignTriage(TriageLevel)}.
     *
     * @param patient paciente que se clasifica
     * @return nivel del síntoma más grave que presenta
     * @throws IllegalArgumentException si el paciente es nulo
     */
    public TriageLevel classify(Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        return patient.getSymptoms().stream()
                .map(Symptom::getTriageLevel)
                .min(Comparator.comparingInt(TriageLevel::getLevel))
                .orElseThrow();
    }
}