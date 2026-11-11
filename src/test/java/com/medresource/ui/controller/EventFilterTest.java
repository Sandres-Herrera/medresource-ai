package com.medresource.ui.controller;

import com.medresource.model.EventType;
import com.medresource.model.SimulationEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del filtro del registro de eventos.
 */
class EventFilterTest {

    private static final SimulationEvent ARRIVAL = new SimulationEvent(0, EventType.ARRIVAL, "P001", null, "Llega");
    private static final SimulationEvent DEATH = new SimulationEvent(9, EventType.DEATH, "P002", "H1-C1", "Muere");

    @Test
    void theFilterOfEverythingAcceptsEveryEvent() {
        assertTrue(EventFilter.ALL.accepts(ARRIVAL));
        assertTrue(EventFilter.ALL.accepts(DEATH));
    }

    @Test
    void acceptsOnlyTheEventsOfItsGroup() {
        EventFilter deaths = new EventFilter(EventCategory.DEATHS, null);

        assertTrue(deaths.accepts(DEATH));
        assertFalse(deaths.accepts(ARRIVAL));
    }

    @Test
    void withAPatientAcceptsOnlyHisEvents() {
        EventFilter onlyFirst = new EventFilter(EventCategory.ALL, "P001");

        assertTrue(onlyFirst.accepts(ARRIVAL));
        assertFalse(onlyFirst.accepts(DEATH));
        assertFalse(new EventFilter(EventCategory.DEATHS, "P001").accepts(DEATH));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new EventFilter(null, null));
        assertThrows(IllegalArgumentException.class, () -> new EventFilter(EventCategory.ALL, " "));
        assertThrows(IllegalArgumentException.class, () -> EventFilter.ALL.accepts(null));
    }
}
