package com.medresource.ui.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del reloj de la simulación en pantalla.
 */
class SimulationClockTest {

    /** Avanza el reloj en cuadros cortos, como lo hace la ventana. */
    private static void play(SimulationClock clock, int millis) {
        for (int elapsed = 0; elapsed < millis; elapsed += 100) {
            clock.advance(100);
        }
    }

    @Test
    void startsInRealTimeAtSecondZero() {
        SimulationClock clock = new SimulationClock();

        assertEquals(1, clock.getSpeed());
        assertEquals(0.0, clock.getTime(), 1e-9);
        assertEquals(0, clock.getMinute());
        assertEquals(0, clock.getSecond());
        assertEquals(List.of(1, 2, 5, 10), SimulationClock.SPEEDS);
    }

    @Test
    void inRealTimeOneSecondIsOneSimulatedSecond() {
        SimulationClock clock = new SimulationClock();

        play(clock, 30_000);

        assertEquals(30.0, clock.getTime(), 1e-6);
        assertEquals(0, clock.getMinute());
        assertEquals(30, clock.getSecond());
    }

    @Test
    void sixtySecondsMakeAMinute() {
        SimulationClock clock = new SimulationClock();

        play(clock, 61_000);

        assertEquals(1, clock.getMinute());
        assertEquals(1, clock.getSecond());
    }

    @Test
    void theSpeedMultipliesTheRealTime() {
        SimulationClock clock = new SimulationClock();
        clock.setSpeed(10);

        play(clock, 6_000);

        assertEquals(60.0, clock.getTime(), 1e-6);
        assertEquals(1, clock.getMinute());
    }

    @Test
    void aVeryLateFrameAdvancesOnlyAFewSeconds() {
        SimulationClock clock = new SimulationClock();
        clock.setSpeed(10);

        clock.advance(600_000);

        assertEquals(SimulationClock.MAX_SECONDS_PER_FRAME, clock.getTime(), 1e-9);
    }

    @Test
    void jumpsForwardButNeverBack() {
        SimulationClock clock = new SimulationClock();

        clock.jumpTo(125.5);

        assertEquals(2, clock.getMinute());
        assertEquals(5, clock.getSecond());
        assertThrows(IllegalArgumentException.class, () -> clock.jumpTo(100));
    }

    @Test
    void resetGoesBackToZeroAndKeepsTheSpeed() {
        SimulationClock clock = new SimulationClock();
        clock.setSpeed(5);
        clock.jumpTo(300);

        clock.reset();

        assertEquals(0.0, clock.getTime(), 1e-9);
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
