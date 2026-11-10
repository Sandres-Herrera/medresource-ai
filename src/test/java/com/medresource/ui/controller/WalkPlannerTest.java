package com.medresource.ui.controller;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Route;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.services.simulation.PathFinder;
import com.medresource.ui.render.Renderer2D;
import com.medresource.ui.render.Walker.Leg;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los tramos que arma el planificador sobre el plano del hospital estándar.
 */
class WalkPlannerTest {

    private static final double DELTA = 1e-9;

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final WalkPlanner planner = new WalkPlanner(layout, renderer);

    private final Position chair = layout.getWaitingSpots().get(0);
    private final Position desk = layout.consultDeskOf("D01").orElseThrow();

    private static double meters(List<Leg> legs) {
        return legs.stream().mapToDouble(Leg::meters).sum();
    }

    @Test
    void whoIsAlreadyThereHasNothingToWalk() {
        assertTrue(planner.legs(chair, renderer.centerOf(chair), chair, renderer.centerOf(chair)).isEmpty());
    }

    @Test
    void insideTheSameCellItGoesStraightToThePoint() {
        Point2D center = renderer.centerOf(chair);
        Point2D side = new Point2D.Double(center.getX() - 21, center.getY());

        List<Leg> legs = planner.legs(chair, center, chair, side);

        assertEquals(1, legs.size());
        assertEquals(side, legs.get(0).point());
        assertEquals(21.0 / Renderer2D.CELL_W, legs.get(0).meters(), DELTA);
    }

    @Test
    void goesFromCenterToCenterAlongTheShortestRouteThroughTheCorridors() {
        Route route = new PathFinder(layout).find(chair, desk).orElseThrow();

        List<Leg> legs = planner.legs(chair, renderer.centerOf(chair), desk, renderer.centerOf(desk));

        assertEquals(route.steps(), legs.size());
        for (int index = 0; index < legs.size(); index++) {
            Position cell = route.cells().get(index + 1);
            assertEquals(cell, legs.get(index).cell());
            assertEquals(renderer.centerOf(cell), legs.get(index).point());
            assertEquals(1.0, legs.get(index).meters(), DELTA);
        }
        assertEquals(route.lengthInMeters(), meters(legs), DELTA);
    }

    @Test
    void endsAtTheExactPointOfTheDestination() {
        Point2D center = renderer.centerOf(desk);
        Point2D besideTheDesk = new Point2D.Double(center.getX() - 32, center.getY());

        List<Leg> legs = planner.legs(chair, renderer.centerOf(chair), desk, besideTheDesk);

        assertEquals(besideTheDesk, legs.get(legs.size() - 1).point());
        assertEquals(desk, legs.get(legs.size() - 1).cell());
    }

    @Test
    void whoWasAlreadyOnTheWayToTheNextCellKeepsGoingWithoutTurningBack() {
        Route route = new PathFinder(layout).find(chair, desk).orElseThrow();
        Point2D here = renderer.centerOf(route.cells().get(0));
        Point2D next = renderer.centerOf(route.cells().get(1));
        Point2D halfway = new Point2D.Double((here.getX() + next.getX()) / 2, (here.getY() + next.getY()) / 2);

        List<Leg> legs = planner.legs(chair, halfway, desk, renderer.centerOf(desk));

        assertEquals(next, legs.get(0).point());
        assertEquals(0.5, legs.get(0).meters(), DELTA);
        assertEquals(route.lengthInMeters() - 0.5, meters(legs), DELTA);
    }

    @Test
    void rejectsMissingData() {
        Point2D center = renderer.centerOf(chair);

        assertThrows(IllegalArgumentException.class, () -> new WalkPlanner(null, renderer));
        assertThrows(IllegalArgumentException.class, () -> new WalkPlanner(layout, null));
        assertThrows(IllegalArgumentException.class, () -> planner.legs(null, center, chair, center));
        assertThrows(IllegalArgumentException.class, () -> planner.legs(chair, null, chair, center));
        assertThrows(IllegalArgumentException.class, () -> planner.legs(chair, center, null, center));
        assertThrows(IllegalArgumentException.class, () -> planner.legs(chair, center, chair, null));
    }
}
