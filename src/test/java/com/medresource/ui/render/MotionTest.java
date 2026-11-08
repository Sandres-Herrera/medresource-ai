package com.medresource.ui.render;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del movimiento suave de un paciente.
 */
class MotionTest {

    private static final double DELTA = 1e-9;
    private static final double TRIP = Motion.TRAVEL_SECONDS;

    private final Motion motion = new Motion(new Point2D.Double(0, 0));

    @Test
    void startsStillAtItsStartingPoint() {
        assertEquals(new Point2D.Double(0, 0), motion.getCurrent());
        assertEquals(new Point2D.Double(0, 0), motion.getTarget());
        assertFalse(motion.isMoving());
    }

    @Test
    void aTripLastsTwelveSimulatedSeconds() {
        assertEquals(12.0, Motion.TRAVEL_SECONDS, DELTA);
    }

    @Test
    void doesNotStartWithoutAPointOrWithANegativeMoment() {
        assertThrows(IllegalArgumentException.class, () -> new Motion(null));
        assertThrows(IllegalArgumentException.class, () -> motion.moveTo(null, 0));
        assertThrows(IllegalArgumentException.class, () -> motion.moveTo(new Point2D.Double(1, 1), -1));
    }

    @Test
    void staysWhereItWasUntilTheTripStarts() {
        motion.moveTo(new Point2D.Double(10, 20), 100);

        motion.update(90);

        assertEquals(new Point2D.Double(0, 0), motion.getCurrent());
        assertEquals(new Point2D.Double(10, 20), motion.getTarget());
        assertTrue(motion.isMoving());
    }

    @Test
    void startsAndEndsSlowlyHalfwayIsTheMiddle() {
        motion.moveTo(new Point2D.Double(10, 20), 100);

        motion.update(100 + TRIP / 4);
        assertEquals(10 * 0.15625, motion.getCurrent().getX(), DELTA);
        assertEquals(20 * 0.15625, motion.getCurrent().getY(), DELTA);

        motion.update(100 + TRIP / 2);
        assertEquals(5, motion.getCurrent().getX(), DELTA);
        assertEquals(10, motion.getCurrent().getY(), DELTA);
    }

    @Test
    void arrivesWhenTheTripTimeHasPassed() {
        motion.moveTo(new Point2D.Double(10, 20), 100);

        motion.update(100 + TRIP);

        assertEquals(new Point2D.Double(10, 20), motion.getCurrent());
        assertFalse(motion.isMoving());

        motion.update(1_000);
        assertEquals(new Point2D.Double(10, 20), motion.getCurrent());
    }

    @Test
    void aNewTargetStartsFromWhereItIsNow() {
        motion.moveTo(new Point2D.Double(10, 0), 0);
        motion.update(TRIP / 2);

        motion.moveTo(new Point2D.Double(5, 10), TRIP / 2);
        motion.update(TRIP / 2);
        assertEquals(5, motion.getCurrent().getX(), DELTA);
        assertEquals(0, motion.getCurrent().getY(), DELTA);

        motion.update(TRIP / 2 + TRIP);
        assertEquals(new Point2D.Double(5, 10), motion.getCurrent());
    }

    @Test
    void theSameTargetAgainDoesNotRestartTheTrip() {
        motion.moveTo(new Point2D.Double(10, 0), 0);
        motion.update(TRIP / 2);

        motion.moveTo(new Point2D.Double(10, 0), TRIP / 2);
        motion.update(TRIP);

        assertEquals(new Point2D.Double(10, 0), motion.getCurrent());
    }

    @Test
    void theOutsideCannotMoveItByChangingTheReturnedPoints() {
        motion.moveTo(new Point2D.Double(10, 0), 0);

        motion.getCurrent().setLocation(99, 99);
        motion.getTarget().setLocation(99, 99);

        assertEquals(new Point2D.Double(0, 0), motion.getCurrent());
        assertEquals(new Point2D.Double(10, 0), motion.getTarget());
    }
}
