package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la configuración de una simulación.
 */
class SimulationSetupTest {

    private final CaseConfig caseConfig = CaseConfig.of(1L, Scenario.NORMAL);

    @Test
    void standardUsesTheStandardHospitalAndClinicalPriority() {
        SimulationSetup setup = SimulationSetup.standard(caseConfig);

        assertEquals(HospitalConfig.standard(), setup.hospital());
        assertEquals(caseConfig, setup.caseConfig());
        assertEquals(StrategyOption.CLINICAL_PRIORITY, setup.strategy());
    }

    @Test
    void rejectsMissingData() {
        HospitalConfig hospital = HospitalConfig.standard();
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationSetup(null, caseConfig, StrategyOption.FIFO));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationSetup(hospital, null, StrategyOption.FIFO));
        assertThrows(IllegalArgumentException.class, () -> new SimulationSetup(hospital, caseConfig, null));
    }
}
