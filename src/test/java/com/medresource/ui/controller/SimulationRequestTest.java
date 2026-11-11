package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del pedido que deja el formulario de una simulación nueva.
 */
class SimulationRequestTest {

    private static final SimulationSetup SETUP = SimulationSetup.standard(CaseConfig.of(1L, Scenario.NORMAL));

    @Test
    void keepsTheSetupAndTheSpeed() {
        SimulationRequest request = new SimulationRequest(SETUP, 10);

        assertEquals(SETUP, request.setup());
        assertEquals(10, request.speed());
    }

    @Test
    void rejectsAMissingSetupOrAnUnknownSpeed() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationRequest(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new SimulationRequest(SETUP, 3));
    }
}
