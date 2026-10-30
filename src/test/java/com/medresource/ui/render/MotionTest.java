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

    private final Motion motion = new Motion(new Point2D.Double(0, 0));

    @Test
    void startsStillAtItsStartingPoint() {
        assertEquals(new Point2D.Double(0, 0), motion.getCurrent());
        assertEquals(new Point2D.Double(0, 0), motion.getTarget());
        assertFalse(motion.isMoving());
    }

    @Test
    void doesNotStartWithoutAPoint() {
        assertThrows(IllegalArgumentException.class, () -> new Motion(null));
        assertThrows(IllegalArgumentException.class, () -> motion.moveTo(null));
    }

    @Test
    void staysWhereItWasUntilItAdvances() {
        motion.moveTo(new Point2D.Double(10, 20));

        assertEquals(new Point2D.Double(0, 0), motion.getCurrent());
        assertEquals(new Point2D.Double(10, 20), motion.getTarget());
        assertTrue(motion.isMoving());
    }

    @Test
    void startsAndEndsSlowlyHalfwayIsTheMiddle() {
        motion.moveTo(new Point2D.Double(10, 20));

        motion.advance(0.25);
        assertEquals(10 * 0.15625, motion.getCurrent().getX(), DELTA);
        assertEquals(20 * 0.15625, motion.getCurrent().getY(), DELTA);

        motion.advance(0.5);
        assertEquals(5, motion.getCurrent().getX(), DELTA);
        assertEquals(10, motion.getCurrent().getY(), DELTA);
    }

    @Test
    void arrivesAtTheTargetWhenTheProgressIsComplete() {
        motion.moveTo(new Point2D.Double(10, 20));

        motion.advance(1.0);

        assertEquals(new Point2D.Double(10, 20), motion.getCurrent());
        assertFalse(motion.isMoving());
    }

    @Test
    void theProgressMustBeBetweenZeroAndOne() {
        assertThrows(IllegalArgumentException.class, () -> motion.advance(-0.01));
        assertThrows(IllegalArgumentException.class, () -> motion.advance(1.01));
        motion.advance(0.0);
        motion.advance(1.0);
    }

    @Test
    void aNewTargetStartsFromWhereItIsNow() {
        motion.moveTo(new Point2D.Double(10, 0));
        motion.advance(0.5);

        motion.moveTo(new Point2D.Double(5, 10));
        motion.advance(0.0);

        assertEquals(5, motion.getCurrent().getX(), DELTA);
        assertEquals(0, motion.getCurrent().getY(), DELTA);
        motion.advance(1.0);
        assertEquals(new Point2D.Double(5, 10), motion.getCurrent());
    }

    @Test
    void theSameTargetAgainDoesNotRestartTheTrip() {
        motion.moveTo(new Point2D.Double(10, 0));
        motion.advance(0.5);

        motion.moveTo(new Point2D.Double(10, 0));
        motion.advance(0.5);

        assertEquals(5, motion.getCurrent().getX(), DELTA);
    }

    @Test
    void theOutsideCannotMoveItByChangingTheReturnedPoints() {
        motion.moveTo(new Point2D.Double(10, 0));
        motion.getCurrent().setLocation(99, 99);
        motion.getTarget().setLocation(99, 99);

        assertEquals(new Point2D.Double(0, 0), motion.getCurrent());
        assertEquals(new Point2D.Double(10, 0), motion.getTarget());
    }
}