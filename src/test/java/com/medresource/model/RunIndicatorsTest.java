package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los indicadores de una ejecución.
 *
 * <p>El resultado de ejemplo tiene tres pacientes: uno atendido a los 12 minutos, otro atendido al llegar que
 * fallece y otro que espera 120 minutos y se queda sin atención.</p>
 */
class RunIndicatorsTest {

    private final RunIndicators indicators = RunIndicators.of(ResultFixtures.resultOf("FIFO", 77));

    @Test
    void countsThePatientsByWhatHappenedToThem() {
        assertEquals(3, indicators.patients());
        assertEquals(2, indicators.attended());
        assertEquals(1, indicators.deaths());
        assertEquals(1, indicators.unattended());
    }

    @Test
    void theWaitOfAnUnattendedPatientLastsUntilItsOutcome() {
        assertEquals((12 + 0 + 120) / 3.0, indicators.averageWait(), 1e-9);
        assertEquals(120, indicators.maxWait());
    }

    @Test
    void calculatesThePercentages() {
        assertEquals(100.0 / 3, indicators.mortalityPercent(), 1e-9);
        assertEquals(200.0 / 3, indicators.attendedPercent(), 1e-9);
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> RunIndicators.of(null));
        assertThrows(IllegalArgumentException.class, () -> new RunIndicators(0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunIndicators(3, 4, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunIndicators(3, 1, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunIndicators(3, 1, 1, 0, -2, 0));
    }
}
