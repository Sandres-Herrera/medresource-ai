package com.medresource.model;

import java.util.List;

/**
 * Lo que hace falta para volver a simular el caso de una ejecución guardada: el mismo caso (semilla, escenario,
 * pacientes y duración), el hospital y la estrategia con los que se va a simular y los cambios que se hicieron
 * frente a la ejecución original, por ejemplo las recomendaciones de su reporte.
 *
 * <p>El caso no cambia nunca: así la ejecución nueva tiene exactamente los mismos pacientes y se puede comparar
 * con la original.</p>
 *
 * @param baseRunId    identificador de la ejecución que se vuelve a simular
 * @param caseConfig   caso de esa ejecución
 * @param hospital     recursos con los que se va a simular
 * @param strategyName nombre de la estrategia con la que se va a simular
 * @param changes      descripción de cada cambio frente a la ejecución original; vacía si se repite igual
 */
public record RerunPlan(int baseRunId, CaseConfig caseConfig, HospitalConfig hospital, String strategyName,
                        List<String> changes) {

    /**
     * Valida los datos y copia la lista de cambios.
     *
     * @throws IllegalArgumentException si el identificador no es positivo, si falta algún dato o si algún cambio
     *                                  está vacío
     */
    public RerunPlan {
        if (baseRunId < 1) {
            throw new IllegalArgumentException("El identificador de la ejecución base debe ser positivo.");
        }
        if (caseConfig == null || hospital == null || strategyName == null || strategyName.isBlank()
                || changes == null) {
            throw new IllegalArgumentException("El caso, el hospital, la estrategia y los cambios son obligatorios.");
        }
        if (changes.stream().anyMatch(change -> change == null || change.isBlank())) {
            throw new IllegalArgumentException("Cada cambio necesita una descripción.");
        }
        changes = List.copyOf(changes);
    }

    /**
     * Indica si el plan cambia algo frente a la ejecución original.
     *
     * @return {@code true} si hay al menos un cambio
     */
    public boolean hasChanges() {
        return !changes.isEmpty();
    }
}
