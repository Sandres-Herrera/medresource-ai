package com.medresource.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resultado del análisis «¿y si…?»: cómo le habría ido al hospital con cada cambio de recursos y con cada otra
 * estrategia de atención, sobre los mismos casos.
 *
 * <p>Las recomendaciones son las alternativas que mejoran el hospital
 * ({@link WhatIfResult#isImprovement()}), de la que más muertes evita a la que menos; a igual mortalidad, la
 * que más acorta la espera. Se muestran hasta {@value #MAX_RECOMMENDATIONS}.</p>
 *
 * @param cases        casos simulados por cada alternativa (semillas distintas del mismo escenario)
 * @param strategyName estrategia con la que funciona hoy el hospital
 * @param mortality    mortalidad del hospital tal como está en cada caso, de 0 a 1
 * @param waitMinutes  espera promedio del hospital tal como está en cada caso
 * @param resources    resultado de cada cambio de recursos, en el orden en que se probaron
 * @param policies     resultado de cada otra estrategia de atención
 */
public record WhatIfReport(int cases, String strategyName, Statistic mortality, Statistic waitMinutes,
                           List<WhatIfResult> resources, List<WhatIfResult> policies) {

    /** Máximo de recomendaciones que se muestran. */
    public static final int MAX_RECOMMENDATIONS = 5;

    /** De la que más muertes evita a la que menos; a igual mortalidad, la que más acorta la espera. */
    private static final Comparator<WhatIfResult> BEST_FIRST = Comparator
            .comparingDouble((WhatIfResult result) -> result.mortalityChange().mean())
            .thenComparingDouble(result -> result.waitChange().mean());

    /**
     * Valida los datos y guarda copias inmutables de las listas.
     *
     * @throws IllegalArgumentException si falta algún dato, si los casos no son al menos dos, si una lista
     *                                  mezcla cambios de recursos con estrategias o si algún resultado no viene
     *                                  de la misma cantidad de casos
     */
    public WhatIfReport {
        if (cases < 2) {
            throw new IllegalArgumentException("El análisis necesita al menos dos casos.");
        }
        if (strategyName == null || strategyName.isBlank() || mortality == null || waitMinutes == null
                || resources == null || policies == null) {
            throw new IllegalArgumentException("Faltan datos del análisis.");
        }
        if (resources.stream().anyMatch(WhatIfResult::isPolicy)
                || policies.stream().anyMatch(result -> !result.isPolicy())) {
            throw new IllegalArgumentException("Los cambios de recursos y las estrategias van en listas aparte.");
        }
        List<WhatIfResult> all = new ArrayList<>(resources);
        all.addAll(policies);
        if (mortality.count() != cases || waitMinutes.count() != cases
                || all.stream().anyMatch(result -> result.mortality().count() != cases)) {
            throw new IllegalArgumentException("Todos los resultados deben venir de los mismos casos.");
        }
        resources = List.copyOf(resources);
        policies = List.copyOf(policies);
    }

    /**
     * Devuelve los recursos que faltaron: los cambios de recursos que bajan la mortalidad con claridad, del que
     * más muertes evita al que menos.
     *
     * @return cambios que bajan la mortalidad; vacío si a ninguno le alcanzó
     */
    public List<WhatIfResult> missingResources() {
        return resources.stream().filter(WhatIfResult::lowersMortality).sorted(BEST_FIRST).toList();
    }

    /**
     * Devuelve la mejor estrategia distinta de la actual, si mejora el hospital.
     *
     * @return la estrategia que más mejora; vacío si ninguna mejora a la actual
     */
    public Optional<WhatIfResult> betterPolicy() {
        return policies.stream().filter(WhatIfResult::isImprovement).min(BEST_FIRST);
    }

    /**
     * Devuelve las recomendaciones, de la que más mejora a la que menos.
     *
     * @return hasta {@value #MAX_RECOMMENDATIONS} alternativas que mejoran el hospital
     */
    public List<WhatIfResult> recommendations() {
        List<WhatIfResult> all = new ArrayList<>(resources);
        all.addAll(policies);
        return all.stream().filter(WhatIfResult::isImprovement).sorted(BEST_FIRST)
                .limit(MAX_RECOMMENDATIONS).toList();
    }
}
