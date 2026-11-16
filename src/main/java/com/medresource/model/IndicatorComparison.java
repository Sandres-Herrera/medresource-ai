package com.medresource.model;

import java.util.OptionalDouble;

/**
 * Un indicador de dos ejecuciones puesto lado a lado: su valor en cada una, la diferencia y si la segunda mejoró.
 *
 * @param name          nombre del indicador, por ejemplo {@code Fallecidos}
 * @param unit          unidad para mostrar el valor, por ejemplo {@code min} o {@code %}; vacía si es un conteo
 * @param valueA        valor en la primera ejecución
 * @param valueB        valor en la segunda ejecución
 * @param lowerIsBetter {@code true} si un valor más bajo es mejor (fallecidos, espera); {@code false} si es mejor
 *                      uno más alto (pacientes atendidos)
 */
public record IndicatorComparison(String name, String unit, double valueA, double valueB, boolean lowerIsBetter) {

    /** Diferencia por debajo de la cual dos valores se consideran iguales. */
    private static final double TOLERANCE = 1e-9;

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta el nombre o la unidad, o si algún valor no es un número
     */
    public IndicatorComparison {
        if (name == null || name.isBlank() || unit == null) {
            throw new IllegalArgumentException("El indicador necesita nombre y unidad (vacía si es un conteo).");
        }
        if (!Double.isFinite(valueA) || !Double.isFinite(valueB)) {
            throw new IllegalArgumentException("Los valores del indicador deben ser números.");
        }
    }

    /**
     * Calcula la diferencia absoluta: valor de la segunda menos valor de la primera.
     *
     * @return la diferencia, negativa si bajó
     */
    public double difference() {
        return valueB - valueA;
    }

    /**
     * Calcula la variación relativa frente a la primera ejecución, en porcentaje.
     *
     * @return la variación (por ejemplo −50 si bajó a la mitad), o vacío si el valor de la primera es cero y no se
     *         puede dividir
     */
    public OptionalDouble relativeChangePercent() {
        if (Math.abs(valueA) < TOLERANCE) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(100.0 * difference() / valueA);
    }

    /**
     * Dice si la segunda ejecución mejoró, empeoró o quedó igual en este indicador.
     *
     * @return la tendencia
     */
    public Trend trend() {
        double difference = difference();
        if (Math.abs(difference) < TOLERANCE) {
            return Trend.UNCHANGED;
        }
        boolean lower = difference < 0;
        return lower == lowerIsBetter ? Trend.IMPROVED : Trend.WORSENED;
    }
}
