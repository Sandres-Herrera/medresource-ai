package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las fallas de la atención.
 */
class CareFailuresTest {

    @Test
    void keepsTheFailures() {
        CareFailures failures = new CareFailures(12, 3, 9);

        assertEquals(12, failures.overTarget());
        assertEquals(3, failures.criticalWithoutTimelyAid());
        assertEquals(9, failures.longestCriticalWithoutAidMinutes());
    }

    @Test
    void theFailuresCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new CareFailures(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CareFailures(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CareFailures(0, 0, -1));
    }
}
