package com.medresource.ui.render;

import com.medresource.model.EventType;
import com.medresource.model.SimulationEvent;
import com.medresource.ui.render.SceneTimeline.TimedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la línea de tiempo que reparte los eventos dentro del minuto.
 */
class SceneTimelineTest {

    private static SimulationEvent event(int minute, EventType type, String patient) {
        return new SimulationEvent(minute, type, patient, null, "evento de prueba");
    }

    private static List<SimulationEvent> arrivals(int minute, int count) {
        List<SimulationEvent> events = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            events.add(event(minute, EventType.ARRIVAL, "P00" + i));
        }
        return events;
    }

    @Test
    void startsEmpty() {
        SceneTimeline timeline = new SceneTimeline();

        assertTrue(timeline.isEmpty());
        assertEquals(0, timeline.size());
        assertTrue(timeline.nextTime().isEmpty());
        assertTrue(timeline.releaseUntil(1_000).isEmpty());
    }

    @Test
    void spreadsThePatientsWhoArriveInTheSameMinuteOneAfterAnother() {
        SceneTimeline timeline = new SceneTimeline();
        timeline.schedule(2, arrivals(2, 4));

        List<TimedEvent> all = timeline.releaseUntil(180);

        assertEquals(4, all.size());
        for (int i = 0; i < 4; i++) {
            double slotStart = 120 + 15.0 * i;
            assertTrue(all.get(i).time() >= slotStart, "evento " + i);
            assertTrue(all.get(i).time() < slotStart + 12, "evento " + i);
            assertEquals("P00" + (i + 1), all.get(i).event().patientCode());
        }
    }

    @Test
    void theEventsThatMoveNoOneHappenWithThePreviousOne() {
        SceneTimeline timeline = new SceneTimeline();
        timeline.schedule(0, List.of(
                event(0, EventType.TREATMENT_FINISHED, "P009"),
                event(0, EventType.ARRIVAL, "P001"),
                event(0, EventType.EMERGENCY_ALERT, "P001"),
                event(0, EventType.DIAGNOSIS, "P001")));

        List<TimedEvent> all = timeline.releaseUntil(60);

        assertEquals(0.0, all.get(0).time(), 1e-9);
        assertEquals(all.get(1).time(), all.get(2).time(), 1e-9);
        assertEquals(all.get(1).time(), all.get(3).time(), 1e-9);
    }

    @Test
    void releasesOnlyTheEventsWhoseMomentHasComeAndOnlyOnce() {
        SceneTimeline timeline = new SceneTimeline();
        timeline.schedule(0, arrivals(0, 3));

        List<TimedEvent> first = timeline.releaseUntil(19.9);
        List<TimedEvent> rest = timeline.releaseUntil(60);

        assertEquals(List.of("P001"), first.stream().map(timed -> timed.event().patientCode()).toList());
        assertEquals(List.of("P002", "P003"), rest.stream().map(timed -> timed.event().patientCode()).toList());
        assertTrue(timeline.isEmpty());
        assertTrue(timeline.releaseUntil(60).isEmpty());
    }

    @Test
    void theMomentsNeverGoBack() {
        SceneTimeline timeline = new SceneTimeline();
        timeline.schedule(0, arrivals(0, 5));
        timeline.schedule(1, List.of(event(1, EventType.DISCHARGE, "P001"), event(1, EventType.ARRIVAL, "P006")));
        timeline.schedule(5, arrivals(5, 1));

        List<TimedEvent> all = timeline.releaseUntil(600);

        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).time() >= all.get(i - 1).time(), "evento " + i);
        }
        assertEquals(8, all.size());
    }

    @Test
    void theSameEventsAlwaysGetTheSameMoments() {
        SceneTimeline first = new SceneTimeline();
        SceneTimeline second = new SceneTimeline();
        first.schedule(7, arrivals(7, 3));
        second.schedule(7, arrivals(7, 3));

        assertEquals(first.releaseUntil(1_000), second.releaseUntil(1_000));
    }

    @Test
    void theMomentChangesWithTheMinuteAndThePatient() {
        double base = SceneTimeline.jitter(event(3, EventType.ARRIVAL, "P001"));

        assertNotEquals(base, SceneTimeline.jitter(event(4, EventType.ARRIVAL, "P001")));
        assertNotEquals(base, SceneTimeline.jitter(event(3, EventType.ARRIVAL, "P002")));
        assertTrue(base >= 0 && base < 1);
    }

    @Test
    void nextTimeIsTheMomentOfTheFirstPendingEvent() {
        SceneTimeline timeline = new SceneTimeline();
        timeline.schedule(10, arrivals(10, 2));

        double next = timeline.nextTime().orElseThrow();

        assertTrue(next >= 600 && next < 630);
        assertEquals(2, timeline.size());
        assertEquals(next, timeline.releaseUntil(next).get(0).time(), 1e-9);
        assertFalse(timeline.isEmpty());
    }

    @Test
    void aMinuteWithoutEventsAddsNothing() {
        SceneTimeline timeline = new SceneTimeline();

        timeline.schedule(3, List.of());

        assertTrue(timeline.isEmpty());
    }

    @Test
    void rejectsInvalidMinutesAndEvents() {
        SceneTimeline timeline = new SceneTimeline();
        timeline.schedule(4, List.of());

        assertThrows(IllegalArgumentException.class, () -> timeline.schedule(4, List.of()));
        assertThrows(IllegalArgumentException.class, () -> timeline.schedule(2, List.of()));
        assertThrows(IllegalArgumentException.class, () -> timeline.schedule(5, null));
        assertThrows(IllegalArgumentException.class, () -> timeline.schedule(5, arrivals(6, 1)));
        List<SimulationEvent> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> timeline.schedule(5, withNull));
        assertThrows(IllegalArgumentException.class, () -> new SceneTimeline().schedule(-1, List.of()));
    }
}
