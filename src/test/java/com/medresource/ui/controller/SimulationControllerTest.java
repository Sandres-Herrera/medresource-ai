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
        assertFalse(controller.isRunning());
        assertEquals(SimulationController.DEFAULT_SPEED, controller.getSpeed());
        assertTrue(controller.markers().isEmpty());
    }

    @Test
    void doesNotAdvanceWhilePaused() {
        SimulationController controller = controller();

        controller.advance(5000);

        assertEquals(0, controller.getMinute());
    }

    @Test
    void advancesAsManyMinutesAsTheSpeedSays() {
        SimulationController controller = controller();
        controller.setSpeed(5);
        controller.play();

        controller.advance(1000);

        assertEquals(5, controller.getMinute());
    }

    @Test
    void aLongFrameNeverSimulatesMoreThanTheLimit() {
        SimulationController controller = controller();
        controller.setSpeed(60);
        controller.play();

        controller.advance(60_000);

        assertEquals(SimulationController.MAX_MINUTES_PER_FRAME, controller.getMinute());
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
        controller.setSpeed(60);
        controller.play();
        while (!controller.isFinished()) {
            controller.advance(1000);
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
    void metricsAreEmptyUntilTheEnd() {
        assertTrue(controller().metrics().isEmpty());
    }

    @Test
    void newEventsAreDeliveredOnlyOnce() {
        SimulationController controller = controller();
        controller.setSpeed(60);
        controller.play();
        controller.advance(1000);

        List<SimulationEvent> first = controller.drainNewEvents();

        assertFalse(first.isEmpty());
        assertTrue(controller.drainNewEvents().isEmpty());
    }

    @Test
    void resetGoesBackToMinuteZero() {
        SimulationController controller = controller();
        controller.play();
        controller.advance(1000);

        controller.reset();

        assertEquals(0, controller.getMinute());
        assertFalse(controller.isRunning());
        assertEquals(0, controller.counters().arrived());
    }

    @Test
    void rejectsASpeedOutsideTheList() {
        assertThrows(IllegalArgumentException.class, () -> controller().setSpeed(7));
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
