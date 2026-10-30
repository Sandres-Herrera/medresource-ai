package com.medresource.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de los contadores en vivo.
 */
class LiveCountersTest {

    private static LiveCounters counters(int total, int discharged, int referred, int deceased, int unattended) {
        return new LiveCounters(10, total, total, 0, 0, 0, discharged, referred, deceased, unattended,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1);
    }

    @Test
    void finishedAddsEveryFinalOutcome() {
        assertEquals(10, counters(20, 4, 3, 2, 1).finished());
    }

    @Test
    void progressIsThePercentOfFinishedPatients() {
        assertEquals(50, counters(20, 4, 3, 2, 1).progressPercent());
        assertEquals(100, counters(0, 0, 0, 0, 0).progressPercent());
    }
}
