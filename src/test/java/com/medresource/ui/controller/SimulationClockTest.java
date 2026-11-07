package com.medresource.ui.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reloj de la simulación en pantalla.
 */
class SimulationClockTest {

    @Test
    void startsInRealTimeAtSecondZero() {
        SimulationClock clock = new SimulationClock();

        assertEquals(1, clock.getSpeed());
        assertEquals(0, clock.getSecond());
        assertEquals(0.0, clock.minuteFraction(), 1e-9);
        assertEquals(List.of(1, 2, 5, 10), SimulationClock.SPEEDS);
    }

    @Test
    void inRealTimeOneSecondIsOneSimulatedSecond() {
        SimulationClock clock = new SimulationClock();

        assertFalse(clock.advance(30_000));

        assertEquals(30, clock.getSecond());
        assertEquals(0.5, clock.minuteFraction(), 1e-9);
    }

    @Test
    void completesAMinuteAfterSixtySimulatedSeconds() {
        SimulationClock clock = new SimulationClock();
        clock.advance(59_000);

        assertTrue(clock.advance(1_500));

        assertEquals(0, clock.getSecond());
        assertEquals(0.5 / 60, clock.minuteFraction(), 1e-9);
    }

    @Test
    void theSpeedMultipliesTheRealTime() {
        SimulationClock clock = new SimulationClock();
        clock.setSpeed(10);

        assertFalse(clock.advance(3_000));
        assertEquals(30, clock.getSecond());
        assertTrue(clock.advance(3_000));
        assertEquals(0, clock.getSecond());
    }

    @Test
    void aVeryLateFrameCompletesOnlyOneMinuteAndDropsTheRest() {
        SimulationClock clock = new SimulationClock();
        clock.setSpeed(10);

        assertTrue(clock.advance(600_000));

        assertEquals(0, clock.getSecond());
        assertFalse(clock.advance(0));
    }

    @Test
    void restartMinuteGoesBackToSecondZeroAndKeepsTheSpeed() {
        SimulationClock clock = new SimulationClock();
        clock.setSpeed(5);
        clock.advance(4_000);

        clock.restartMinute();

        assertEquals(0, clock.getSecond());
        assertEquals(5, clock.getSpeed());
    }

    @Test
    void formatsTheTimeAsDayHoursMinutesAndSeconds() {
        assertEquals("Día 1 · 00:00:00", SimulationClock.format(0, 0));
        assertEquals("Día 1 · 01:15:09", SimulationClock.format(75, 9));
        assertEquals("Día 1 · 23:59:59", SimulationClock.format(1439, 59));
        assertEquals("Día 2 · 01:15:30", SimulationClock.format(1515, 30));
    }

    @Test
    void rejectsASpeedOutsideTheList() {
        SimulationClock clock = new SimulationClock();

        assertThrows(IllegalArgumentException.class, () -> clock.setSpeed(3));
        assertThrows(IllegalArgumentException.class, () -> clock.setSpeed(60));
        assertEquals(1, clock.getSpeed());
    }

    @Test
    void rejectsNegativeTime() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock().advance(-1));
    }

    @Test
    void rejectsAnInvalidMomentToFormat() {
        assertThrows(IllegalArgumentException.class, () -> SimulationClock.format(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> SimulationClock.format(0, -1));
        assertThrows(IllegalArgumentException.class, () -> SimulationClock.format(0, 60));
    }
}
