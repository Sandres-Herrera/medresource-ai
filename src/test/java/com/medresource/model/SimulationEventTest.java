package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los eventos de la simulación.
 */
class SimulationEventTest {

    @Test
    void keepsTheValuesItWasCreatedWith() {
        SimulationEvent event = new SimulationEvent(12, EventType.ARRIVAL, "P-001", "B-01", "Llega un paciente.");

        assertEquals(12, event.minute());
        assertEquals(EventType.ARRIVAL, event.type());
        assertEquals("P-001", event.patientCode());
        assertEquals("B-01", event.resourceCode());
    }

    @Test
    void theResourceIsOptional() {
        SimulationEvent event = new SimulationEvent(0, EventType.ARRIVAL, "P-001", null, "Llega un paciente.");

        assertNull(event.resourceCode());
    }

    @Test
    void rejectsANegativeMinute() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEvent(-1, EventType.ARRIVAL, "P-001", null, "Llega."));
    }

    @Test
    void rejectsAMissingType() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEvent(0, null, "P-001", null, "Llega."));
    }

    @Test
    void rejectsAMissingOrBlankPatientCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEvent(0, EventType.ARRIVAL, null, null, "Llega."));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEvent(0, EventType.ARRIVAL, " ", null, "Llega."));
    }

    @Test
    void rejectsAMissingOrBlankDescription() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEvent(0, EventType.ARRIVAL, "P-001", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEvent(0, EventType.ARRIVAL, "P-001", null, "  "));
    }
}
