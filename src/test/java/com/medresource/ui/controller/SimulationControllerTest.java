package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.EventType;
import com.medresource.model.HospitalConfig;
import com.medresource.model.RunMetrics;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationEvent;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.MetricsCalculator;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.ui.render.Renderer2D;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del controlador de la simulación en pantalla.
 */
class SimulationControllerTest {

    private static final CaseConfig SMALL_CASE = new CaseConfig(3L, Scenario.NORMAL, 15, 120);

    private static SimulationController controller() {
        return new SimulationController(SimulationSetup.standard(SMALL_CASE), new Renderer2D());
    }

    /** Avanza el controlador en cuadros cortos, como lo hace la ventana. */
    private static void play(SimulationController controller, int millis) {
        for (int elapsed = 0; elapsed < millis; elapsed += 100) {
            controller.advance(100);
        }
    }

    @Test
    void startsPausedAtMinuteZero() {
        SimulationController controller = controller();

        assertEquals(0, controller.getMinute());
        assertEquals(0, controller.getSecond());
        assertFalse(controller.isRunning());
        assertEquals(SimulationClock.DEFAULT_SPEED, controller.getSpeed());
        assertTrue(controller.markers().isEmpty());
        assertTrue(controller.porterMarkers().isEmpty());
    }

    @Test
    void doesNotAdvanceWhilePaused() {
        SimulationController controller = controller();

        controller.advance(5000);

        assertEquals(0, controller.getMinute());
    }

    @Test
    void inRealTimeAMinuteTakesSixtySeconds() {
        SimulationController controller = controller();
        controller.play();

        play(controller, 30_000);
        assertEquals(0, controller.getMinute());
        assertEquals(30, controller.getSecond());

        play(controller, 30_000);
        assertEquals(1, controller.getMinute());
        assertEquals(0, controller.getSecond());
    }

    @Test
    void atTenTimesSpeedAMinuteTakesSixSeconds() {
        SimulationController controller = controller();
        controller.setSpeed(10);
        controller.play();

        play(controller, 6_000);

        assertEquals(1, controller.getMinute());
    }

    @Test
    void aVeryLateFrameAdvancesOnlyAFewSeconds() {
        SimulationController controller = controller();
        controller.setSpeed(10);
        controller.play();

        controller.advance(600_000);

        assertEquals(0, controller.getMinute());
        assertEquals((int) SimulationClock.MAX_SECONDS_PER_FRAME, controller.getSecond());
    }

    @Test
    void stepOnceSimulatesOneMinuteAndPauses() {
        SimulationController controller = controller();
        controller.play();

        assertTrue(controller.stepOnce());

        assertEquals(1, controller.getMinute());
        assertFalse(controller.isRunning());
    }

    @Test
    void reachesTheSameResultAsTheEngineAloneAtAnySpeed() {
        SimulationController controller = controller();
        controller.setSpeed(10);
        controller.play();
        while (!controller.isFinished()) {
            controller.advance(500);
        }
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(SMALL_CASE),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        RunMetrics expected = new MetricsCalculator().calculate(engine.run());

        assertFalse(controller.isRunning());
        assertEquals(expected, controller.metrics().orElseThrow());
        LiveCounters counters = controller.counters();
        assertEquals(SMALL_CASE.patientCount(), counters.arrived());
        assertEquals(100, counters.progressPercent());
    }

    @Test
    void skipToNextEventShowsTheNextEventRightAway() {
        SimulationController controller = controller();

        int skipped = controller.skipToNextEvent();

        List<SimulationEvent> events = controller.drainNewEvents();
        assertFalse(events.isEmpty());
        assertEquals(controller.getMinute(), events.get(0).minute());
        assertEquals(skipped, controller.getMinute());
    }

    @Test
    void eachEventIsSeenAtItsOwnSecondInRealTime() {
        SimulationController controller = new SimulationController(SimulationSetup.standard(
                new CaseConfig(7L, Scenario.CATASTROPHE, 100, 120)), new Renderer2D());
        controller.play();
        Map<Integer, Set<Integer>> arrivalSeconds = new HashMap<>();
        for (int frame = 0; frame < 10 * 60; frame++) {
            controller.advance(1_000);
            for (SimulationEvent event : controller.drainNewEvents()) {
                if (event.type() == EventType.ARRIVAL) {
                    arrivalSeconds.computeIfAbsent(event.minute(), minute -> new HashSet<>())
                            .add(controller.getSecond());
                }
            }
        }

        assertTrue(arrivalSeconds.values().stream().anyMatch(seconds -> seconds.size() >= 3),
                "varios pacientes llegan en el mismo minuto, cada uno en un segundo distinto");
    }

    @Test
    void skipToNextEventKeepsPlayingOrPausedAsItWas() {
        SimulationController paused = controller();
        paused.skipToNextEvent();
        assertFalse(paused.isRunning());

        SimulationController playing = controller();
        playing.play();
        playing.skipToNextEvent();
        assertTrue(playing.isRunning());
    }

    @Test
    void skippingFromEventToEventReachesTheSameResultAsTheEngineAlone() {
        SimulationController controller = controller();
        while (!controller.isFinished()) {
            controller.skipToNextEvent();
        }
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(SMALL_CASE),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());

        assertEquals(new MetricsCalculator().calculate(engine.run()), controller.metrics().orElseThrow());
        assertEquals(0, controller.skipToNextEvent());
    }

    @Test
    void metricsAreEmptyUntilTheEnd() {
        assertTrue(controller().metrics().isEmpty());
    }

    @Test
    void newEventsAreDeliveredOnlyOnce() {
        SimulationController controller = controller();
        controller.setSpeed(10);
        controller.play();
        for (int minute = 0; minute < 60; minute++) {
            play(controller, 6_000);
        }

        List<SimulationEvent> first = controller.drainNewEvents();

        assertFalse(first.isEmpty());
        assertTrue(controller.drainNewEvents().isEmpty());
    }

    @Test
    void resetGoesBackToMinuteZero() {
        SimulationController controller = controller();
        controller.setSpeed(10);
        controller.play();
        play(controller, 7_000);

        controller.reset();

        assertEquals(0, controller.getMinute());
        assertEquals(0, controller.getSecond());
        assertEquals(10, controller.getSpeed());
        assertFalse(controller.isRunning());
        assertEquals(0, controller.counters().arrived());
    }

    @Test
    void rejectsASpeedOutsideTheList() {
        assertThrows(IllegalArgumentException.class, () -> controller().setSpeed(60));
    }

    @Test
    void rejectsNegativeTime() {
        assertThrows(IllegalArgumentException.class, () -> controller().advance(-1));
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationController(null, new Renderer2D()));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationController(SimulationSetup.standard(SMALL_CASE), null));
    }

    @Test
    void givesTheCardOfAPatientWhoIsInTheScene() {
        SimulationController controller = controller();

        assertTrue(controller.patientCard("P001").isEmpty());

        controller.skipToNextEvent();
        String code = controller.markers().get(0).code();

        assertEquals(code, controller.patientCard(code).orElseThrow().code());
    }
}
