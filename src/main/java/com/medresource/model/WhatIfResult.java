package com.medresource.model;

/**
 * Resultado de probar una alternativa en el análisis «¿y si…?»: un cambio de recursos o una estrategia de
 * atención distinta, simulada sobre los mismos casos que el hospital tal como está.
 *
 * <p>Las diferencias son pareadas: en cada caso se resta el resultado del hospital tal como está, y después se
 * promedia. Así cada caso se compara consigo mismo y el ruido de un caso más difícil que otro no cuenta. Una
 * diferencia es clara si su intervalo de confianza del 95 % no incluye el cero
 * ({@link Statistic#excludesZero()}).</p>
 *
 * <p>Además de clara, una mejora tiene que valer la pena (supuestos del análisis): evitar al menos
 * {@value #MIN_DEATHS_AVOIDED_PER_100} muertes por cada 100 pacientes, o acortar la espera promedio en al menos
 * {@value #MIN_WAIT_SAVED_MINUTES} minutos. Así no se recomienda un médico más por ahorrar segundos.</p>
 *
 * @param label              descripción de la alternativa, por ejemplo {@code 1 quirófano más}
 * @param change             cambio de recursos; {@code null} si la alternativa es otra estrategia
 * @param strategyName       estrategia con la que se simuló la alternativa
 * @param mortality          mortalidad de la alternativa en cada caso, de 0 a 1
 * @param mortalityChange    mortalidad de la alternativa menos la del hospital tal como está, caso por caso
 * @param waitChange         espera promedio de la alternativa menos la del hospital tal como está, en minutos
 */
public record WhatIfResult(String label, ResourceChange change, String strategyName, Statistic mortality,
                           Statistic mortalityChange, Statistic waitChange) {

    /** Muertes evitadas por cada 100 pacientes desde las que bajar la mortalidad vale la pena (supuesto). */
    public static final double MIN_DEATHS_AVOIDED_PER_100 = 0.25;

    /** Minutos de espera promedio ahorrados desde los que acortar la espera vale la pena (supuesto). */
    public static final int MIN_WAIT_SAVED_MINUTES = 5;

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta algún dato o si las estadísticas no son de la misma cantidad de
     *                                  casos
     */
    public WhatIfResult {
        if (label == null || label.isBlank() || strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("La alternativa y su estrategia son obligatorias.");
        }
        if (mortality == null || mortalityChange == null || waitChange == null) {
            throw new IllegalArgumentException("Faltan los resultados de la alternativa.");
        }
        if (mortality.count() != mortalityChange.count() || mortality.count() != waitChange.count()) {
            throw new IllegalArgumentException("Los resultados deben venir de los mismos casos.");
        }
    }

    /**
     * Indica si la alternativa es otra estrategia de atención en lugar de un cambio de recursos.
     *
     * @return {@code true} si no cambia recursos
     */
    public boolean isPolicy() {
        return change == null;
    }

    /**
     * Calcula cuántas muertes se evitarían por cada 100 pacientes, en promedio.
     *
     * @return muertes evitadas por cada 100 pacientes; negativo si la alternativa causa más muertes
     */
    public double deathsAvoidedPer100() {
        return -100 * mortalityChange.mean();
    }

    /**
     * Indica si la alternativa baja la mortalidad con claridad y lo suficiente.
     *
     * @return {@code true} si evita al menos {@value #MIN_DEATHS_AVOIDED_PER_100} muertes por cada 100
     *         pacientes y el intervalo de confianza no incluye el cero
     */
    public boolean lowersMortality() {
        return deathsAvoidedPer100() >= MIN_DEATHS_AVOIDED_PER_100 && mortalityChange.excludesZero();
    }

    /**
     * Indica si la alternativa sube la mortalidad con claridad.
     *
     * @return {@code true} si la mortalidad sube y el intervalo de confianza no incluye el cero
     */
    public boolean raisesMortality() {
        return mortalityChange.mean() > 0 && mortalityChange.excludesZero();
    }

    /**
     * Indica si la alternativa acorta la espera con claridad y lo suficiente.
     *
     * @return {@code true} si la espera promedio baja al menos {@value #MIN_WAIT_SAVED_MINUTES} minutos y el
     *         intervalo de confianza no incluye el cero
     */
    public boolean lowersWait() {
        return -waitChange.mean() >= MIN_WAIT_SAVED_MINUTES && waitChange.excludesZero();
    }

    /**
     * Indica si la alternativa mejora el hospital: baja la mortalidad, o acorta la espera sin sumar muertes en
     * promedio. Una alternativa que acorta la espera pero podría costar vidas no se recomienda.
     *
     * @return {@code true} si conviene recomendarla
     */
    public boolean isImprovement() {
        return lowersMortality() || (lowersWait() && mortalityChange.mean() <= 0);
    }
}
