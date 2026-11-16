package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;

/**
 * Todo lo que el usuario elige antes de simular: el hospital, el caso y la estrategia. Si la simulación vuelve a
 * simular el caso de una ejecución guardada, también dice cuál.
 *
 * @param hospital configuración del hospital
 * @param caseConfig configuración del caso (escenario, semilla, pacientes y duración)
 * @param strategy estrategia de asignación
 * @param baseRunId ejecución guardada cuyo caso se vuelve a simular; {@code null} si es una simulación nueva
 */
public record SimulationSetup(HospitalConfig hospital, CaseConfig caseConfig, StrategyOption strategy,
                              Integer baseRunId) {

    /**
     * Valida la configuración.
     *
     * @throws IllegalArgumentException si falta el hospital, el caso o la estrategia, o si la ejecución base no es
     *                                  positiva
     */
    public SimulationSetup {
        if (hospital == null || caseConfig == null || strategy == null) {
            throw new IllegalArgumentException("El hospital, el caso y la estrategia son obligatorios.");
        }
        if (baseRunId != null && baseRunId < 1) {
            throw new IllegalArgumentException("El identificador de la ejecución base debe ser positivo.");
        }
    }

    /**
     * Crea la configuración de una simulación nueva, que no se basa en una ejecución guardada.
     *
     * @param hospital   configuración del hospital
     * @param caseConfig configuración del caso
     * @param strategy   estrategia de asignación
     * @throws IllegalArgumentException si falta alguno de los tres datos
     */
    public SimulationSetup(HospitalConfig hospital, CaseConfig caseConfig, StrategyOption strategy) {
        this(hospital, caseConfig, strategy, null);
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
