package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
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

import java.util.List;

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

    @Test
    void startsPausedAtMinuteZero() {
        SimulationController controller = controller();

        assertEquals(0, controller.getMinute());
        assertEquals(0, controller.getSecond());
        assertFalse(controller.isRunning());
        assertEquals(SimulationClock.DEFAULT_SPEED, controller.getSpeed());
        assertTrue(controller.markers().isEmpty());
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

        controller.advance(30_000);
        assertEquals(0, controller.getMinute());
        assertEquals(30, controller.getSecond());

        controller.advance(30_000);
        assertEquals(1, controller.getMinute());
        assertEquals(0, controller.getSecond());
    }

    @Test
    void atTenTimesSpeedAMinuteTakesSixSeconds() {
        SimulationController controller = controller();
        controller.setSpeed(10);
        controller.play();

        controller.advance(6_000);

        assertEquals(1, controller.getMinute());
    }

    @Test
    void aVeryLateFrameSimulatesOnlyOneMinute() {
        SimulationController controller = controller();
        controller.setSpeed(10);
        controller.play();

        controller.advance(600_000);

        assertEquals(1, controller.getMinute());
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
            controller.advance(2_000);
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
    void skipToNextEventStopsAtTheNextMinuteWithEvents() {
        SimulationController controller = controller();
        controller.drainNewEvents();

        int skipped = controller.skipToNextEvent();

        assertTrue(skipped >= 1);
        assertEquals(skipped, controller.getMinute());
        assertEquals(0, controller.getSecond());
        List<SimulationEvent> events = controller.drainNewEvents();
        assertFalse(events.isEmpty());
        int lastMinute = controller.getMinute() - 1;
        assertTrue(events.stream().allMatch(event -> event.minute() == lastMinute));
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
            controller.advance(6_000);
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
        controller.advance(6_000);
        controller.advance(1_000);

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
}
