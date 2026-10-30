package com.medresource.services.strategy;

import java.util.List;

/**
 * Resumen de un entrenamiento: cuántos ejemplos se usaron y cuánto error medio
 * tuvo la red en cada época.
 *
 * @param examples    cantidad de ejemplos de entrenamiento
 * @param epochLosses error medio de cada época, en orden
 */
public record TrainingReport(int examples, List<Double> epochLosses) {

    /**
     * Valida el resumen y guarda una copia de la lista.
     *
     * @throws IllegalArgumentException si no hubo ejemplos o épocas
     */
    public TrainingReport {
        if (examples < 1 || epochLosses == null || epochLosses.isEmpty()) {
            throw new IllegalArgumentException("El resumen necesita ejemplos y al menos una época.");
        }
        epochLosses = List.copyOf(epochLosses);
    }

    /**
     * Devuelve el error de la primera época.
     *
     * @return error medio inicial
     */
    public double firstLoss() {
        return epochLosses.get(0);
    }

    /**
     * Devuelve el error de la última época.
     *
     * @return error medio final
     */
    public double lastLoss() {
        return epochLosses.get(epochLosses.size() - 1);
    }
}