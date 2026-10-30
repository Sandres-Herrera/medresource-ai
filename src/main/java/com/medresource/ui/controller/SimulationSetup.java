package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;

/**
 * Todo lo que el usuario elige antes de simular: el hospital, el caso y la estrategia.
 *
 * @param hospital configuración del hospital
 * @param caseConfig configuración del caso (escenario, semilla, pacientes y duración)
 * @param strategy estrategia de asignación
 */
public record SimulationSetup(HospitalConfig hospital, CaseConfig caseConfig, StrategyOption strategy) {

    /**
     * Valida la configuración.
     *
     * @throws IllegalArgumentException si falta alguno de los tres datos
     */
    public SimulationSetup {
        if (hospital == null || caseConfig == null || strategy == null) {
            throw new IllegalArgumentException("El hospital, el caso y la estrategia son obligatorios.");
        }
    }

    /**
     * Configuración rápida: hospital estándar, caso por defecto del escenario y prioridad clínica.
     *
     * @param caseConfig configuración del caso
     * @return configuración lista para simular
     */
    public static SimulationSetup standard(CaseConfig caseConfig) {
        return new SimulationSetup(HospitalConfig.standard(), caseConfig, StrategyOption.CLINICAL_PRIORITY);
    }
}
