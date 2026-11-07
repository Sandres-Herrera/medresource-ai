package com.medresource.ui.controller;

import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.RandomStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Pruebas de las estrategias que el usuario puede elegir.
 */
class StrategyOptionTest {

    @Test
    void eachOptionCreatesItsStrategy() {
        assertInstanceOf(ClinicalPriorityStrategy.class, StrategyOption.CLINICAL_PRIORITY.create(1L));
        assertInstanceOf(FifoStrategy.class, StrategyOption.FIFO.create(1L));
        assertInstanceOf(RandomStrategy.class, StrategyOption.RANDOM.create(1L));
    }

    @Test
    void showsItsDisplayName() {
        assertEquals("Prioridad clínica (triage)", StrategyOption.CLINICAL_PRIORITY.toString());
        assertEquals("Aleatoria", StrategyOption.RANDOM.getDisplayName());
    }
}
