package com.medresource.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * Resumen de una estrategia en muchos casos: el valor de cada métrica y cuánto se diferencia de la
 * estrategia de referencia.
 *
 * <p>Todas las estrategias se ejecutan sobre los mismos casos, así que la diferencia se calcula caso por
 * caso (apareada): la variación de los casos no oculta la diferencia entre estrategias. Para la
 * estrategia de referencia las diferencias son todas cero.</p>
 *
 * @param strategyName nombre de la estrategia
 * @param cases        cantidad de casos en que se ejecutó
 * @param values       valor de cada métrica en los casos
 * @param differences  diferencia con la estrategia de referencia en cada métrica (esta menos la referencia)
 */
public record StrategySummary(String strategyName, int cases, Map<Metric, Statistic> values,
                              Map<Metric, Statistic> differences) {

    /**
     * Valida el resumen y guarda copias inmutables de los mapas.
     *
     * @throws IllegalArgumentException si falta el nombre, si no hay casos o si falta alguna métrica
     */
    public StrategySummary {
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la estrategia es obligatorio.");
        }
        if (cases < 1) {
            throw new IllegalArgumentException("El resumen necesita al menos un caso.");
        }
        values = copyOfAllMetrics(values);
        differences = copyOfAllMetrics(differences);
    }

    private static Map<Metric, Statistic> copyOfAllMetrics(Map<Metric, Statistic> source) {
        if (source == null) {
            throw new IllegalArgumentException("Faltan los valores de las métricas.");
        }
        Map<Metric, Statistic> copy = new EnumMap<>(Metric.class);
        copy.putAll(source);
        if (copy.size() != Metric.values().length || copy.containsValue(null)) {
            throw new IllegalArgumentException("Debe haber un valor para cada métrica.");
        }
        return Map.copyOf(copy);
    }

    /**
     * Indica si esta estrategia es mejor que la de referencia en una métrica, con confianza del 95 %.
     *
     * @param metric métrica que se mira
     * @return {@code true} si la diferencia es distinta de cero y va en el sentido que mejora la métrica
     */
    public boolean isBetterThanBaseline(Metric metric) {
        Statistic difference = differences.get(metric);
        return difference.excludesZero() && (metric.isLowerBetter() ? difference.mean() < 0 : difference.mean() > 0);
    }

    /**
     * Indica si esta estrategia es peor que la de referencia en una métrica, con confianza del 95 %.
     *
     * @param metric métrica que se mira
     * @return {@code true} si la diferencia es distinta de cero y va en el sentido que empeora la métrica
     */
    public boolean isWorseThanBaseline(Metric metric) {
        Statistic difference = differences.get(metric);
        return difference.excludesZero() && (metric.isLowerBetter() ? difference.mean() > 0 : difference.mean() < 0);
    }
}