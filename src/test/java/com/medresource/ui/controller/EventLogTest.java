package com.medresource.ui.controller;

import com.medresource.model.EventType;
import com.medresource.model.SimulationEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del registro de eventos de la ventana.
 */
class EventLogTest {

    private static SimulationEvent event(int minute, EventType type, String patient) {
        return new SimulationEvent(minute, type, patient, null, type.getDisplayName() + " de " + patient);
    }

    @Test
    void writesTheTimeTheTypeAndTheDescription() {
        EventLog.Entry entry = new EventLog().add("08:05:12", event(5, EventType.ARRIVAL, "P001"));

        assertTrue(entry.line().startsWith("[08:05:12] Llegada de paciente"));
        assertTrue(entry.line().endsWith("Llegada de paciente de P001"));
    }

    @Test
    void returnsTheEventsThatPassTheFilterInOrder() {
        EventLog log = new EventLog();
        log.add("08:00:00", event(0, EventType.ARRIVAL, "P001"));
        log.add("08:00:10", event(0, EventType.ARRIVAL, "P002"));
        log.add("08:03:00", event(3, EventType.TREATMENT_STARTED, "P001"));

        List<EventLog.Entry> first = log.entries(new EventFilter(EventCategory.ALL, "P001"), 10);

        assertEquals(2, first.size());
        assertEquals(EventType.ARRIVAL, first.get(0).event().type());
        assertEquals(EventType.TREATMENT_STARTED, first.get(1).event().type());
        assertEquals(1, log.entries(new EventFilter(EventCategory.CARE, null), 10).size());
    }

    @Test
    void theLimitKeepsTheMostRecentEvents() {
        EventLog log = new EventLog();
        for (int minute = 0; minute < 5; minute++) {
            log.add("08:00:00", event(minute, EventType.ARRIVAL, "P00" + minute));
        }

        List<EventLog.Entry> last = log.entries(EventFilter.ALL, 2);

        assertEquals(2, last.size());
        assertEquals("P003", last.get(0).event().patientCode());
        assertEquals("P004", last.get(1).event().patientCode());
    }

    @Test
    void forgetsTheOldestEventsWhenItIsFull() {
        EventLog log = new EventLog();
        for (int index = 0; index <= EventLog.CAPACITY; index++) {
            log.add("08:00:00", event(index, EventType.ARRIVAL, "P" + index));
        }

        assertEquals(EventLog.CAPACITY, log.size());
        assertEquals("P1", log.entries(EventFilter.ALL, EventLog.CAPACITY).get(0).event().patientCode());
    }

    @Test
    void forgetsEverythingWhenCleared() {
        EventLog log = new EventLog();
        log.add("08:00:00", event(0, EventType.ARRIVAL, "P001"));

        log.clear();

        assertEquals(0, log.size());
        assertTrue(log.entries(EventFilter.ALL, 10).isEmpty());
    }

    @Test
    void rejectsMissingData() {
        EventLog log = new EventLog();

        assertThrows(IllegalArgumentException.class, () -> log.add(null, event(0, EventType.ARRIVAL, "P001")));
        assertThrows(IllegalArgumentException.class, () -> log.add("08:00:00", null));
        assertThrows(IllegalArgumentException.class, () -> log.entries(null, 10));
        assertThrows(IllegalArgumentException.class, () -> log.entries(EventFilter.ALL, 0));
    }
}
