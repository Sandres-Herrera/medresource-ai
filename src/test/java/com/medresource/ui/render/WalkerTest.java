package com.medresource.ui.render;

import com.medresource.model.Position;
import com.medresource.ui.render.Walker.Leg;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del caminante que sigue una ruta a velocidad constante.
 *
 * <p>Usa un recorrido de dos tramos: 1 m hacia la derecha hasta (10, 0) y 2 m hacia abajo hasta (10, 20). A
 * {@value Walker#WALKING_SPEED} m/s el recorrido completo de 3 m dura 1,2 s.</p>
 */
class WalkerTest {

    private static final double DELTA = 1e-9;

    private final Position start = new Position(0, 0);
    private final Position right = new Position(1, 0);
    private final Position below = new Position(1, 1);
    private final Walker walker = new Walker(start, new Point2D.Double(0, 0));
    private final List<Leg> trip = List.of(new Leg(right, new Point2D.Double(10, 0), 1.0),
            new Leg(below, new Point2D.Double(10, 20), 2.0));

    /** Segundos que tarda en caminar unos metros. */
    private static double secondsFor(double meters) {
        return meters / Walker.WALKING_SPEED;
    }

    @Test
    void startsStillAtItsStartingPoint() {
        assertEquals(new Point2D.Double(0, 0), walker.getCurrent());
        assertEquals(new Point2D.Double(0, 0), walker.getTarget());
        assertEquals(start, walker.getCell());
        assertFalse(walker.isMoving());
    }

    @Test
    void walksTwoAndAHalfMetersPerSimulatedSecond() {
        assertEquals(2.5, Walker.WALKING_SPEED, DELTA);
        assertEquals(8.0, secondsFor(20), DELTA);
    }

    @Test
    void walksEachLegAtTheSameSpeed() {
        walker.walk(trip, 100);

        walker.update(100 + secondsFor(0.5));
        assertEquals(5.0, walker.getCurrent().getX(), DELTA);
        assertEquals(0.0, walker.getCurrent().getY(), DELTA);

        walker.update(100 + secondsFor(2.0));
        assertEquals(10.0, walker.getCurrent().getX(), DELTA);
        assertEquals(10.0, walker.getCurrent().getY(), DELTA);
        assertTrue(walker.isMoving());
    }

    @Test
    void arrivesWhenItHasWalkedTheWholeTrip() {
        walker.walk(trip, 100);

        walker.update(100 + secondsFor(3.0));

        assertEquals(new Point2D.Double(10, 20), walker.getCurrent());
        assertEquals(below, walker.getCell());
        assertFalse(walker.isMoving());
        assertEquals(secondsFor(3.0), walker.tripSeconds(), DELTA);
    }

    @Test
    void knowsWhenItArrivesAtTheEndOfItsTrip() {
        assertEquals(0.0, walker.arrivalTime(), DELTA);

        walker.walk(trip, 100);

        assertEquals(100 + secondsFor(3.0), walker.arrivalTime(), DELTA);
    }

    @Test
    void staysAtTheStartUntilTheTripBegins() {
        walker.walk(trip, 100);

        walker.update(50);

        assertEquals(new Point2D.Double(0, 0), walker.getCurrent());
        assertEquals(new Point2D.Double(10, 20), walker.getTarget());
    }

    @Test
    void remembersTheCellOfTheLastPointItReached() {
        walker.walk(trip, 0);

        walker.update(secondsFor(0.5));
        assertEquals(start, walker.getCell());

        walker.update(secondsFor(1.5));
        assertEquals(right, walker.getCell());
    }

    @Test
    void walkingToTheSameDestinationAgainDoesNotRestartTheTrip() {
        walker.walk(trip, 0);
        walker.update(secondsFor(2.0));

        walker.walk(trip, secondsFor(2.0));
        walker.update(secondsFor(3.0));

        assertFalse(walker.isMoving());
    }

    @Test
    void aNewTripStartsFromWhereItIsNow() {
        walker.walk(trip, 0);
        walker.update(secondsFor(0.5));

        walker.walk(List.of(new Leg(start, new Point2D.Double(0, 0), 0.5)), secondsFor(0.5));
        walker.update(secondsFor(0.75));

        assertEquals(2.5, walker.getCurrent().getX(), DELTA);
        assertEquals(new Point2D.Double(0, 0), walker.getTarget());
    }

    @Test
    void withoutLegsItStaysWhereItIs() {
        walker.walk(List.of(), 10);
        walker.update(20);

        assertEquals(new Point2D.Double(0, 0), walker.getCurrent());
        assertFalse(walker.isMoving());
    }

    @Test
    void theLegsCannotBeChangedFromOutside() {
        Point2D point = new Point2D.Double(10, 0);
        Leg leg = new Leg(right, point, 1.0);

        point.setLocation(99, 99);
        leg.point().setLocation(99, 99);

        assertEquals(new Point2D.Double(10, 0), leg.point());
    }

    @Test
    void rejectsMissingDataAndEmptyLegs() {
        assertThrows(IllegalArgumentException.class, () -> new Walker(null, new Point2D.Double(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Walker(start, null));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(null, 0));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(Arrays.asList((Leg) null), 0));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(trip, -1));
        assertThrows(IllegalArgumentException.class, () -> new Leg(null, new Point2D.Double(0, 0), 1));
        assertThrows(IllegalArgumentException.class, () -> new Leg(start, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new Leg(start, new Point2D.Double(0, 0), 0));
    }
}
