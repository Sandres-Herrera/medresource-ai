package com.medresource.ui.controller;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.controller.Itinerary.Stop;
import com.medresource.ui.render.Renderer2D;
import com.medresource.ui.render.SceneState.Phase;
import com.medresource.ui.render.Walker;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del itinerario de un paciente sobre el plano del hospital estándar.
 */
class ItineraryTest {

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final WalkPlanner planner = new WalkPlanner(layout, renderer);
    private final Position entrance = layout.getEntrance();
    private final Itinerary itinerary = new Itinerary(entrance, planner, renderer);

    private final Stop station = new Stop(Phase.IN_TRIAGE, layout.getTriageSpots().get(0));
    private final Stop firstChair = new Stop(Phase.QUEUED, layout.getWaitingSpots().get(0));
    private final Stop secondChair = new Stop(Phase.QUEUED, layout.getWaitingSpots().get(1));
    private final Stop bed = new Stop(Phase.TREATING, layout.positionOf("H1-C1").orElseThrow());

    /** Segundos que tarda en caminar de una casilla a otra por los pasillos. */
    private double secondsBetween(Position from, Position to) {
        return planner.legs(from, renderer.centerOf(from), to, renderer.centerOf(to)).stream()
                .mapToDouble(leg -> leg.meters()).sum() / Walker.WALKING_SPEED;
    }

    /** Casilla del plano donde queda un punto del mundo. */
    private static Position cellOf(Point2D point) {
        return new Position((int) Math.floor(point.getX() / Renderer2D.CELL_W),
                (int) Math.floor(point.getY() / Renderer2D.CELL_H));
    }

    private Point2D centerOf(Stop stop) {
        return renderer.centerOf(stop.cell());
    }

    @Test
    void startsWhereThePatientAppearsWithNothingToDo() {
        assertEquals(renderer.centerOf(entrance), itinerary.getCurrent());
        assertTrue(itinerary.heading().isEmpty());
        assertTrue(itinerary.hasArrived());
    }

    @Test
    void walksToItsFirstStop() {
        itinerary.goTo(station, 10);

        itinerary.update(10 + secondsBetween(entrance, station.cell()));

        assertEquals(centerOf(station), itinerary.getCurrent());
        assertEquals(station, itinerary.heading().orElseThrow());
        assertTrue(itinerary.hasArrived());
    }

    @Test
    void aStopThatComesWhileWalkingWaitsItsTurn() {
        itinerary.goTo(station, 0);

        itinerary.goTo(firstChair, 0.1);
        itinerary.update(0.2);

        assertEquals(station, itinerary.heading().orElseThrow());
        assertEquals(List.of(firstChair), itinerary.pendingStops());
        assertFalse(itinerary.hasArrived());
    }

    @Test
    void theNextStopStartsJustWhenItArrivesEvenIfLookedAtLater() {
        double toStation = secondsBetween(entrance, station.cell());
        double toChair = secondsBetween(station.cell(), firstChair.cell());
        itinerary.goTo(station, 0);
        itinerary.goTo(firstChair, 0.1);

        itinerary.update(toStation + toChair - 0.5);
        assertFalse(itinerary.hasArrived());

        itinerary.update(toStation + toChair);
        assertEquals(centerOf(firstChair), itinerary.getCurrent());
        assertTrue(itinerary.hasArrived());
    }

    @Test
    void itPassesThroughEveryStopInOrder() {
        itinerary.goTo(station, 0);
        itinerary.goTo(firstChair, 0.1);
        itinerary.goTo(bed, 0.2);
        boolean sawStation = false;
        boolean sawChair = false;

        for (double at = 0; at < 120; at += 0.1) {
            itinerary.update(at);
            Position cell = cellOf(itinerary.getCurrent());
            sawStation |= cell.equals(station.cell());
            sawChair |= sawStation && cell.equals(firstChair.cell());
        }

        assertTrue(sawStation);
        assertTrue(sawChair);
        assertEquals(centerOf(bed), itinerary.getCurrent());
    }

    @Test
    void anotherPlaceInTheSameLineReplacesTheLastOne() {
        itinerary.goTo(station, 0);
        itinerary.goTo(secondChair, 0.1);

        itinerary.goTo(firstChair, 0.2);

        assertEquals(List.of(firstChair), itinerary.pendingStops());
    }

    @Test
    void movingUpInTheLineWhileWalkingChangesTheWayAtOnce() {
        itinerary.goTo(secondChair, 0);
        itinerary.update(1);

        itinerary.goTo(firstChair, 1);

        assertEquals(firstChair, itinerary.heading().orElseThrow());
        assertTrue(itinerary.pendingStops().isEmpty());
    }

    @Test
    void theSameStopTwiceChangesNothing() {
        itinerary.goTo(station, 0);
        itinerary.goTo(firstChair, 0.1);

        itinerary.goTo(firstChair, 0.2);

        assertEquals(List.of(firstChair), itinerary.pendingStops());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new Itinerary(null, planner, renderer));
        assertThrows(IllegalArgumentException.class, () -> new Itinerary(entrance, null, renderer));
        assertThrows(IllegalArgumentException.class, () -> new Itinerary(entrance, planner, null));
        assertThrows(IllegalArgumentException.class, () -> new Stop(null, entrance));
        assertThrows(IllegalArgumentException.class, () -> new Stop(Phase.QUEUED, null));
        assertThrows(IllegalArgumentException.class, () -> itinerary.goTo(null, 0));
        assertThrows(IllegalArgumentException.class, () -> itinerary.goTo(station, -1));
    }
}
