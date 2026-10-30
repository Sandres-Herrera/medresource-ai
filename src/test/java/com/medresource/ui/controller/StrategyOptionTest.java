package com.medresource.ui.controller;

import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.ModelStore;
import com.medresource.services.strategy.OptimizationStrategy;
import com.medresource.services.strategy.RandomStrategy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las estrategias que el usuario puede elegir.
 */
class StrategyOptionTest {

    private final ModelStore projectModels = new ModelStore(ModelStore.DEFAULT_DIRECTORY);

    @Test
    void classicStrategiesDoNotNeedAModel() {
        assertInstanceOf(ClinicalPriorityStrategy.class, StrategyOption.CLINICAL_PRIORITY.create(1L, projectModels));
        assertInstanceOf(FifoStrategy.class, StrategyOption.FIFO.create(1L, projectModels));
        assertInstanceOf(RandomStrategy.class, StrategyOption.RANDOM.create(1L, projectModels));
        assertFalse(StrategyOption.FIFO.usesModel());
    }

    @Test
    void aiOptionsLoadTheTrainedNetworksOfTheProject() {
        for (StrategyOption option : StrategyOption.values()) {
            if (option.usesModel()) {
                assertInstanceOf(OptimizationStrategy.class, option.create(1L, projectModels));
            }
        }
        assertTrue(StrategyOption.AI_LIVES.usesModel());
    }

    @Test
    void aiOptionWithoutModelFileFailsWithAClearMessage() throws IOException {
        Path empty = Files.createTempDirectory("sin-modelos-");
        try {
            ModelStore store = new ModelStore(empty);
            assertThrows(IllegalStateException.class, () -> StrategyOption.AI_EQUITY.create(1L, store));
        } finally {
            Files.deleteIfExists(empty);
        }
    }

    @Test
    void rejectsANullStore() {
        assertThrows(IllegalArgumentException.class, () -> StrategyOption.FIFO.create(1L, null));
    }
}
