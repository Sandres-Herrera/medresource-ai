package com.medresource.model;

import java.util.Optional;

/**
 * Una ejecución guardada: el resultado de una estrategia sobre un caso, junto con el caso al que
 * pertenece y los recursos del hospital con los que se ejecutó.
 *
 * <p>El {@link SimulationResult} no guarda los recursos del hospital, pero hacen falta para comparar y
 * repetir ejecuciones: el mismo caso da resultados distintos con un hospital más grande o más chico.</p>
 *
 * <p>Una ejecución puede estar basada en otra del mismo caso: la que se volvió a simular, por ejemplo para probar
 * las recomendaciones de su reporte. Así se sabe después con cuál compararla.</p>
 *
 * @param caseId    identificador del caso guardado sobre el que se ejecutó
 * @param hospital  recursos del hospital con los que se ejecutó
 * @param result    resultado de la ejecución
 * @param baseRunId identificador de la ejecución en la que se basa; {@code null} si es una simulación nueva
 */
public record RunRecord(int caseId, HospitalConfig hospital, SimulationResult result, Integer baseRunId) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si el identificador del caso o el de la ejecución base no son positivos,
     *                                  o si falta el hospital o el resultado
     */
    public RunRecord {
        if (caseId < 1) {
            throw new IllegalArgumentException("El identificador del caso debe ser positivo.");
        }
        if (hospital == null || result == null) {
            throw new IllegalArgumentException("El hospital y el resultado son obligatorios.");
        }
        if (baseRunId != null && baseRunId < 1) {
            throw new IllegalArgumentException("El identificador de la ejecución base debe ser positivo.");
        }
    }

    /**
     * Crea una ejecución que no se basa en otra.
     *
     * @param caseId   identificador del caso
     * @param hospital recursos del hospital
     * @param result   resultado
     * @throws IllegalArgumentException si el identificador del caso no es positivo o falta el hospital o el
     *                                  resultado
     */
    public RunRecord(int caseId, HospitalConfig hospital, SimulationResult result) {
        this(caseId, hospital, result, null);
    }

    /**
     * Devuelve la ejecución en la que se basa esta.
     *
     * @return su identificador, o vacío si es una simulación nueva
     */
    public Optional<Integer> basedOn() {
        return Optional.ofNullable(baseRunId);
    }
}
