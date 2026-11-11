package com.medresource.ui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del movimiento de reposo.
 */
class IdleMotionTest {

    @Test
    void aRestingPersonMovesUpAndDownALittle() {
        double lowest = 0;
        double highest = 0;
        for (double time = 0; time < IdleMotion.PERIOD_SECONDS; time += 0.1) {
            double bob = IdleMotion.bob("P001", time, false);
            assertTrue(Math.abs(bob) <= IdleMotion.AMPLITUDE + 1e-9);
            lowest = Math.min(lowest, bob);
            highest = Math.max(highest, bob);
        }

        assertTrue(highest - lowest > IdleMotion.AMPLITUDE, "sube y baja");
    }

    @Test
    void theMotionRepeatsEveryBreath() {
        assertEquals(IdleMotion.bob("E03", 2.5, false), IdleMotion.bob("E03", 2.5 + IdleMotion.PERIOD_SECONDS, false),
                1e-9);
    }

    @Test
    void twoPeopleDoNotBreatheAtTheSameTime() {
        assertNotEquals(IdleMotion.bob("P001", 10, false), IdleMotion.bob("P002", 10, false), 1e-6);
    }

    @Test
    void someoneWhoWalksDoesNotBob() {
        assertEquals(0.0, IdleMotion.bob("P001", 1.3, true), 0.0);
    }

    @Test
    void theSamePersonAtTheSameMomentAlwaysBobsTheSame() {
        assertEquals(IdleMotion.bob("D07", 33.3, false), IdleMotion.bob("D07", 33.3, false), 0.0);
    }

    @Test
    void needsTheCodeOfThePerson() {
        assertThrows(IllegalArgumentException.class, () -> IdleMotion.bob(null, 0, false));
        assertThrows(IllegalArgumentException.class, () -> IdleMotion.bob(" ", 0, false));
    }
}
