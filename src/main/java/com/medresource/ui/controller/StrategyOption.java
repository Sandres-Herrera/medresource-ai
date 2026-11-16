package com.medresource.ui.controller;

import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.RandomStrategy;

import java.util.Optional;

/**
 * Estrategias que el usuario puede elegir para una simulación.
 *
 * <p>Por ahora solo están las estrategias clásicas; las de inteligencia artificial se agregan cuando la
 * estrategia que consulta la API esté lista.</p>
 */
public enum StrategyOption {

    CLINICAL_PRIORITY("Prioridad clínica (triage)", "El más grave primero"),
    FIFO("FIFO (orden de llegada)", "Línea base: el que llegó primero"),
    RANDOM("Aleatoria", "Al azar, para comparar");

    private final String displayName;
    private final String description;

    StrategyOption(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Cómo decide la estrategia, en una línea corta para su tarjeta. */
    public String getDescription() {
        return description;
    }

    /**
     * Crea la estrategia que representa esta opción.
     *
     * @param seed semilla para la estrategia aleatoria (las demás la ignoran)
     * @return estrategia lista para el motor
     */
    public AllocationStrategy create(long seed) {
        return switch (this) {
            case CLINICAL_PRIORITY -> new ClinicalPriorityStrategy();
            case FIFO -> new FifoStrategy();
            case RANDOM -> new RandomStrategy(seed);
        };
    }

    /**
     * Busca la opción de una estrategia por el nombre con el que se guarda en los resultados.
     *
     * @param strategyName nombre de la estrategia, por ejemplo {@code FIFO (orden de llegada)}
     * @return la opción, o vacío si ninguna tiene ese nombre
     */
    public static Optional<StrategyOption> fromStrategyName(String strategyName) {
        for (StrategyOption option : values()) {
            if (option.create(0L).getName().equals(strategyName)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
