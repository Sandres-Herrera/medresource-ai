package com.medresource.ui.controller;

import com.medresource.model.Objective;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.ModelStore;
import com.medresource.services.strategy.NeuralNetwork;
import com.medresource.services.strategy.OptimizationStrategy;
import com.medresource.services.strategy.RandomStrategy;

/**
 * Estrategias que el usuario puede elegir para una simulación.
 *
 * <p>Las opciones de inteligencia artificial cargan la red de su objetivo desde la carpeta de modelos
 * ({@code models/<objetivo>.txt}), que genera el programa {@code TrainModels}.</p>
 */
public enum StrategyOption {

    CLINICAL_PRIORITY("Prioridad clínica (triage)", null),
    FIFO("FIFO (orden de llegada)", null),
    RANDOM("Aleatoria", null),
    AI_LIVES("IA: maximizar vidas salvadas", Objective.MAXIMIZE_LIVES),
    AI_LIFE_YEARS("IA: maximizar años de vida", Objective.MAXIMIZE_LIFE_YEARS),
    AI_EQUITY("IA: equidad entre pacientes", Objective.EQUITY),
    AI_WAIT("IA: minimizar la espera", Objective.MINIMIZE_WAIT);

    private final String displayName;
    private final Objective objective;

    StrategyOption(String displayName, Objective objective) {
        this.displayName = displayName;
        this.objective = objective;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Indica si la opción usa una red neuronal entrenada. */
    public boolean usesModel() {
        return objective != null;
    }

    /**
     * Crea la estrategia que representa esta opción.
     *
     * @param seed  semilla para la estrategia aleatoria (las demás la ignoran)
     * @param store almacén de donde se cargan las redes entrenadas
     * @return estrategia lista para el motor
     * @throws IllegalArgumentException si el almacén es nulo
     * @throws IllegalStateException    si la opción usa una red y su archivo no existe
     */
    public AllocationStrategy create(long seed, ModelStore store) {
        if (store == null) {
            throw new IllegalArgumentException("El almacén de modelos es obligatorio.");
        }
        return switch (this) {
            case CLINICAL_PRIORITY -> new ClinicalPriorityStrategy();
            case FIFO -> new FifoStrategy();
            case RANDOM -> new RandomStrategy(seed);
            default -> {
                NeuralNetwork network = store.load(objective).orElseThrow(() -> new IllegalStateException(
                        "No se encontró el modelo " + store.fileFor(objective)
                                + ". Ejecute TrainModels para generarlo."));
                yield new OptimizationStrategy(network, objective);
            }
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
