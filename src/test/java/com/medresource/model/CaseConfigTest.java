package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la configuración de un caso.
 */
class CaseConfigTest {

    @Test
    void defaultConfigurationUsesTheStandardSizes() {
        CaseConfig config = CaseConfig.of(42L, Scenario.NORMAL);

        assertEquals(100, config.patientCount());
        assertEquals(480, config.durationMinutes());
        assertEquals(Scenario.NORMAL, config.scenario());
    }

    @Test
    void keepsTheValuesItWasCreatedWith() {
        CaseConfig config = new CaseConfig(7L, Scenario.CATASTROPHE, 250, 600);

        assertEquals(250, config.patientCount());
        assertEquals(600, config.durationMinutes());
    }

    @Test
    void configurationsWithTheSameDataAreEqual() {
        assertEquals(CaseConfig.of(5L, Scenario.HIGH_DEMAND), CaseConfig.of(5L, Scenario.HIGH_DEMAND));
    }

    @Test
    void rejectsAMissingScenario() {
        assertThrows(IllegalArgumentException.class, () -> new CaseConfig(1L, null, 100, 480));
    }

    @Test
    void rejectsAPatientCountOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new CaseConfig(1L, Scenario.NORMAL, 0, 480));
        assertThrows(IllegalArgumentException.class, () -> new CaseConfig(1L, Scenario.NORMAL, 1001, 480));
    }

    @Test
    void rejectsADurationOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new CaseConfig(1L, Scenario.NORMAL, 100, 59));
        assertThrows(IllegalArgumentException.class, () -> new CaseConfig(1L, Scenario.NORMAL, 100, 1441));
    }
}