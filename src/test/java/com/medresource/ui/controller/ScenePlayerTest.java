package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Patient;
import com.medresource.model.Scenario;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.Renderer2D;
import com.medresource.ui.render.SceneState;
import com.medresource.ui.render.SceneState.Phase;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reproductor: simulaciones reales (sin azar, con semilla) reproducidas minuto a minuto.
 */
class ScenePlayerTest {

    private static final HospitalConfig TINY = new HospitalConfig(1, 1, 1, 1, 1, 0, 1);

    private final Renderer2D renderer = new Renderer2D();

    private SimulationEngine engineOf(HospitalConfig hospital, Scenario scenario, int patients) {
        return new SimulationEngine(new CaseGenerator().generate(new CaseConfig(1L, scenario, patients, 480)),
                new HospitalFactory().create(hospital), new ClinicalPriorityStrategy());
    }

    private ScenePlayer playerOf(HospitalConfig hospital, Scenario scenario, int patients) {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(hospital));
        return new ScenePlayer(engineOf(hospital, scenario, patients), layout, renderer);
    }

    private ScenePlayer standard() {
        return playerOf(HospitalConfig.standard(), Scenario.NORMAL, 30);
    }

    @Test
    void rejectsMissingData() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = engineOf(HospitalConfig.standard(), Scenario.NORMAL, 5);

        assertThrows(IllegalArgumentException.class, () -> new ScenePlayer(null, layout, renderer));
        assertThrows(IllegalArgumentException.class, () -> new ScenePlayer(engine, null, renderer));
        assertThrows(IllegalArgumentException.class, () -> new ScenePlayer(engine, layout, null));
    }

    @Test
    void doesNotTakeASimulationThatAlreadyStarted() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = engineOf(HospitalConfig.standard(), Scenario.NORMAL, 5);
        engine.step();

        assertThrows(IllegalStateException.class, () -> new ScenePlayer(engine, layout, renderer));
    }

    @Test
    void startsEmptyAtMinuteZero() {
        ScenePlayer player = standard();

        assertEquals(0, player.getMinute());
        assertTrue(player.markers().isEmpty());
        assertEquals(0, player.overflow());
        assertFalse(player.isFinished());
        assertNotNull(player.getState());
    }

    @Test
    void eachStepSimulatesOneMinute() {
        ScenePlayer player = standard();

        assertTrue(player.step());
        assertEquals(1, player.getMinute());
        assertTrue(player.step());
        assertEquals(2, player.getMinute());
    }

    @Test
    void stopsWhenTheSimulationIsFinished() {
        ScenePlayer player = standard();
        int steps = 0;
        while (player.step()) {
            steps++;
        }

        assertTrue(player.isFinished());
        assertEquals(steps, player.getMinute());
        assertFalse(player.step());
        assertEquals(steps, player.getMinute());
    }

    @Test
    void aNewPatientAppearsAtTheEntranceAndWalksToItsPlace() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.NORMAL, 30), layout, renderer);
        while (player.markers().isEmpty()) {
            player.step();
        }
        Point2D entrance = renderer.centerOf(layout.getEntrance());
        PatientMarker first = player.markers().get(0);

        assertEquals(entrance.getX(), first.x(), 0.0);
        assertEquals(entrance.getY(), first.y(), 0.0);

        player.animate(1.0);

        Point2D place = renderer.centerOf(player.getState().positionOf(first.code()).orElseThrow());
        assertEquals(place.getX(), player.markers().get(0).x(), 0.0);
        assertEquals(place.getY(), player.markers().get(0).y(), 0.0);
    }

    @Test
    void aPatientIsHalfwayWhenTheAnimationIsHalfDone() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.NORMAL, 30), layout, renderer);
        while (player.markers().isEmpty()) {
            player.step();
        }
        PatientMarker first = player.markers().get(0);
        Point2D entrance = renderer.centerOf(layout.getEntrance());
        Point2D place = renderer.centerOf(player.getState().positionOf(first.code()).orElseThrow());

        player.animate(0.5);

        assertEquals((entrance.getX() + place.getX()) / 2, player.markers().get(0).x(), 1e-9);
        assertEquals((entrance.getY() + place.getY()) / 2, player.markers().get(0).y(), 1e-9);
    }

    @Test
    void doesNotAnimateOutsideTheRangeOfProgress() {
        ScenePlayer player = standard();
        while (player.markers().isEmpty()) {
            player.step();
        }

        assertThrows(IllegalArgumentException.class, () -> player.animate(1.5));
        assertThrows(IllegalArgumentException.class, () -> player.animate(-0.5));
    }

    @Test
    void theMarkersCarryTheDataOfEachPatient() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = engineOf(HospitalConfig.standard(), Scenario.NORMAL, 30);
        Map<String, Patient> byCode = new HashMap<>();
        engine.getPatients().forEach(patient -> byCode.put(patient.getCode(), patient));
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);

        while (player.step()) {
            player.animate(1.0);
            for (PatientMarker marker : player.markers()) {
                Patient patient = byCode.get(marker.code());
                assertEquals(patient.getTriageLevel(), marker.triage());
                assertEquals(patient.getSex(), marker.sex());
                assertEquals(patient.isMinor(), marker.minor());
            }
        }
    }

    @Test
    void everyMarkerStaysInsideTheWorld() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        Dimension world = renderer.worldSize(layout);
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.CATASTROPHE, 150), layout, renderer);

        while (player.step()) {
            player.animate(1.0);
            for (PatientMarker marker : player.markers()) {
                assertTrue(marker.x() >= 0 && marker.x() <= world.width, marker.code());
                assertTrue(marker.y() >= 0 && marker.y() <= world.height, marker.code());
            }
        }
    }

    @Test
    void whenItFinishesNoOneIsWaitingOrBeingTreated() {
        for (Scenario scenario : Scenario.values()) {
            ScenePlayer player = playerOf(TINY, scenario, 80);
            while (player.step()) {
                player.animate(1.0);
            }
            Set<Phase> allowed = EnumSet.of(Phase.IN_MORGUE, Phase.LEAVING, Phase.DECEASED);

            for (SceneState.Placement placement : player.getState().placements()) {
                assertTrue(allowed.contains(placement.phase()), scenario + " " + placement);
            }
            assertEquals(0, player.getState().count(Phase.QUEUED));
            assertEquals(0, player.getState().count(Phase.TREATING));
        }
    }

    @Test
    void aCrowdedHospitalLeavesPeopleOutOfTheWaitingRoomAndHasDeceasedPatients() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 200);
        int mostWaiting = 0;
        boolean sawDeceased = false;
        while (player.step()) {
            player.animate(1.0);
            mostWaiting = Math.max(mostWaiting, player.overflow());
            sawDeceased |= player.markers().stream().anyMatch(PatientMarker::deceased);
        }

        assertTrue(mostWaiting > 0);
        assertTrue(sawDeceased);
    }

    @Test
    void theDeceasedMarkersAreTheOnesThatDiedOrAreInTheMorgue() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 120);
        while (player.step()) {
            player.animate(1.0);
            long expected = player.getState().count(Phase.DECEASED) + player.getState().count(Phase.IN_MORGUE);

            assertEquals(expected, player.markers().stream().filter(PatientMarker::deceased).count());
        }
    }

    @Test
    void thereIsOneMarkerForEachPatientInTheScene() {
        ScenePlayer player = playerOf(TINY, Scenario.HIGH_DEMAND, 100);
        while (player.step()) {
            player.animate(1.0);

            assertEquals(player.getState().placements().size(), player.markers().size());
        }
    }

    @Test
    void thePatientsWhoLeftAreRemovedOnTheNextMinute() {
        ScenePlayer player = playerOf(HospitalConfig.standard(), Scenario.NORMAL, 20);
        List<String> departed = new java.util.ArrayList<>();
        while (player.step()) {
            for (String code : departed) {
                assertTrue(player.getState().phaseOf(code).isEmpty(), code);
            }
            departed.clear();
            player.getState().placements().stream()
                    .filter(placement -> placement.phase() == Phase.LEAVING)
                    .forEach(placement -> departed.add(placement.patientCode()));
        }
    }

    @Test
    void theSameCaseIsAlwaysPlayedTheSame() {
        ScenePlayer first = playerOf(TINY, Scenario.CATASTROPHE, 60);
        ScenePlayer second = playerOf(TINY, Scenario.CATASTROPHE, 60);

        while (first.step()) {
            assertTrue(second.step());
            first.animate(0.5);
            second.animate(0.5);

            assertEquals(first.markers(), second.markers());
            assertEquals(first.overflow(), second.overflow());
        }
        assertFalse(second.step());
    }
}