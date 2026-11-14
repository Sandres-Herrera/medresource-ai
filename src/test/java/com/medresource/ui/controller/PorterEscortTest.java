package com.medresource.ui.controller;

import com.medresource.model.Area;
import com.medresource.model.Door;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.controller.Itinerary.Stop;
import com.medresource.ui.render.Renderer2D;
import com.medresource.ui.render.SceneState.Phase;
import com.medresource.ui.render.Walker;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del camillero que busca a un paciente en la sala de espera del hospital estándar.
 */
class PorterEscortTest {

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final WalkPlanner planner = new WalkPlanner(layout, renderer);
    private final Position chair = layout.getWaitingSpots().get(0);
    private final Area waitingRoom = layout.areasOf(ZoneType.WAITING_ROOM).get(0);
    private final Door door = layout.doorsOf(waitingRoom).get(0);

    private PorterEscort fetchAt(double at) {
        return PorterEscort.fetch("P001", chair, layout, planner, renderer, at).orElseThrow();
    }

    private Point2D besideChair() {
        Point2D center = renderer.centerOf(chair);
        return new Point2D.Double(center.getX() + PorterEscort.SIDE_OFFSET, center.getY());
    }

    /** Itinerario de un paciente sentado en la silla que sale hacia una cama cuando llega el camillero. */
    private Itinerary seatedPatient(double sitsAt) {
        Itinerary itinerary = new Itinerary(layout.getEntrance(), planner, renderer);
        itinerary.goTo(new Stop(Phase.QUEUED, chair), 0);
        itinerary.update(sitsAt);
        return itinerary;
    }

    @Test
    void comesOutOfTheDoorOfTheWaitingRoomAndReachesTheChairAtWalkingSpeed() {
        PorterEscort escort = fetchAt(100);
        double meters = planner.legs(door.outside(), renderer.centerOf(door.outside()), chair, besideChair())
                .stream().mapToDouble(Walker.Leg::meters).sum();

        escort.update(100, null);

        assertEquals(renderer.centerOf(door.outside()), escort.getPosition());
        assertEquals(100 + meters / Walker.WALKING_SPEED, escort.readyAt(), 1e-9);
        assertEquals("P001", escort.getPatientCode());
    }

    @Test
    void walksBesideThePatientAndLeavesWhenHeArrives() {
        Itinerary itinerary = seatedPatient(60);
        PorterEscort escort = fetchAt(100);
        Stop bed = new Stop(Phase.TREATING, layout.positionOf("H1-C1").orElseThrow());
        itinerary.goTo(bed, 100, escort.readyAt());

        itinerary.update(escort.readyAt() - 1);
        escort.update(escort.readyAt() - 1, itinerary);
        assertEquals(renderer.centerOf(chair), itinerary.getCurrent(), "el paciente espera al camillero");

        double walking = escort.readyAt() + 2;
        itinerary.update(walking);
        escort.update(walking, itinerary);
        Point2D patient = itinerary.getCurrent();
        assertEquals(new Point2D.Double(patient.getX() + PorterEscort.SIDE_OFFSET, patient.getY()),
                escort.getPosition());
        assertFalse(escort.isGone());

        double later = walking + 120;
        itinerary.update(later);
        escort.update(later, itinerary);
        escort.update(later + 120, itinerary);
        assertTrue(itinerary.hasArrived());
        assertTrue(escort.isGone(), "dejó al paciente y salió por la puerta");
    }

    @Test
    void leavesIfThePatientIsNoLongerInTheScene() {
        PorterEscort escort = fetchAt(0);

        escort.update(escort.readyAt() + 1, null);
        escort.update(escort.readyAt() + 300, null);

        assertTrue(escort.isGone());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class,
                () -> PorterEscort.fetch(null, chair, layout, planner, renderer, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PorterEscort.fetch("P001", null, layout, planner, renderer, 0));
        assertThrows(IllegalArgumentException.class,
                () -> PorterEscort.fetch("P001", chair, layout, planner, renderer, -1));
    }
}
