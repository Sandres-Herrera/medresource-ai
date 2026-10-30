package com.medresource.model;

import java.util.List;
import java.util.Optional;

/**
 * Resultado de comparar varias estrategias sobre los mismos casos y el mismo hospital.
 *
 * @param hospital  recursos del hospital con los que se ejecutó cada caso
 * @param cases     cantidad de casos
 * @param summaries resumen de cada estrategia; la primera es la de referencia
 */
public record ComparisonReport(HospitalConfig hospital, int cases, List<StrategySummary> summaries) {

    /**
     * Valida el informe y guarda una copia inmutable de la lista.
     *
     * @throws IllegalArgumentException si falta el hospital o la lista, si no hay casos o resúmenes, o si
     *                                  dos estrategias tienen el mismo nombre
     */
    public ComparisonReport {
        if (hospital == null || summaries == null) {
            throw new IllegalArgumentException("Faltan datos del informe de comparación.");
        }
        if (cases < 1) {
            throw new IllegalArgumentException("El informe necesita al menos un caso.");
        }
        summaries = List.copyOf(summaries);
        if (summaries.isEmpty()) {
            throw new IllegalArgumentException("El informe necesita al menos una estrategia.");
        }
        if (summaries.stream().map(StrategySummary::strategyName).distinct().count() != summaries.size()) {
            throw new IllegalArgumentException("Las estrategias del informe deben tener nombres distintos.");
        }
    }

    /**
     * Devuelve el resumen de la estrategia de referencia, con la que se comparan las demás.
     *
     * @return el primer resumen
     */
    public StrategySummary baseline() {
        return summaries.get(0);
    }

    /**
     * Busca el resumen de una estrategia por su nombre.
     *
     * @param strategyName nombre de la estrategia
     * @return el resumen, o vacío si la estrategia no está en el informe
     */
    public Optional<StrategySummary> find(String strategyName) {
        return summaries.stream().filter(summary -> summary.strategyName().equals(strategyName)).findFirst();
    }
}