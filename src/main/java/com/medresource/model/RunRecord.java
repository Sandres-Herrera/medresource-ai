package com.medresource.model;

/**
 * Una ejecución guardada: el resultado de una estrategia sobre un caso, junto con el caso al que
 * pertenece y los recursos del hospital con los que se ejecutó.
 *
 * <p>El {@link SimulationResult} no guarda los recursos del hospital, pero hacen falta para comparar y
 * repetir ejecuciones: el mismo caso da resultados distintos con un hospital más grande o más chico.</p>
 *
 * @param caseId   identificador del caso guardado sobre el que se ejecutó
 * @param hospital recursos del hospital con los que se ejecutó
 * @param result   resultado de la ejecución
 */
public record RunRecord(int caseId, HospitalConfig hospital, SimulationResult result) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si el identificador del caso no es positivo o falta el hospital
     *                                  o el resultado
     */
    public RunRecord {
        if (caseId < 1) {
            throw new IllegalArgumentException("El identificador del caso debe ser positivo.");
        }
        if (hospital == null || result == null) {
            throw new IllegalArgumentException("El hospital y el resultado son obligatorios.");
        }
    }
}