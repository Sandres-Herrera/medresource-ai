package com.medresource.model;

import java.util.function.ToDoubleFunction;

/**
 * Métrica con la que se comparan las estrategias.
 *
 * <p>Cada métrica sabe sacar su valor de un {@link RunMetrics} y si es mejor cuando es menor (muertes,
 * esperas) o cuando es mayor (años de vida salvados).</p>
 */
public enum Metric {

    MORTALITY("Mortalidad", true, RunMetrics::mortalityRate),
    AVOIDABLE_DEATHS("Muertes evitables", true, RunMetrics::avoidableDeaths),
    AVERAGE_WAIT("Espera promedio (min)", true, RunMetrics::averageWaitMinutes),
    URGENT_WAIT("Espera de triage I y II (min)", true, RunMetrics::urgentAverageWaitMinutes),
    LIFE_YEARS_SAVED("Años de vida salvados", false, RunMetrics::lifeYearsSaved),
    MINOR_MORTALITY("Mortalidad de menores", true, RunMetrics::minorMortality),
    ELDERLY_MORTALITY("Mortalidad de adultos mayores", true, RunMetrics::elderlyMortality);

    private final String displayName;
    private final boolean lowerIsBetter;
    private final ToDoubleFunction<RunMetrics> reader;

    Metric(String displayName, boolean lowerIsBetter, ToDoubleFunction<RunMetrics> reader) {
        this.displayName = displayName;
        this.lowerIsBetter = lowerIsBetter;
        this.reader = reader;
    }

    /**
     * Devuelve el nombre que se muestra al usuario.
     *
     * @return nombre en español
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Indica en qué sentido mejora la métrica.
     *
     * @return {@code true} si es mejor un valor menor; {@code false} si es mejor uno mayor
     */
    public boolean isLowerBetter() {
        return lowerIsBetter;
    }

    /**
     * Saca el valor de esta métrica de los números de una ejecución.
     *
     * @param metrics números de la ejecución
     * @return valor de la métrica; {@link Double#NaN} si el grupo no tenía pacientes
     * @throws IllegalArgumentException si faltan los números
     */
    public double of(RunMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("Las métricas de la ejecución son obligatorias.");
        }
        return reader.applyAsDouble(metrics);
    }
}