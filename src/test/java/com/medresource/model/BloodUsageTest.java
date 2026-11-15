package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del uso de un tipo de sangre.
 */
class BloodUsageTest {

    @Test
    void aTypeThatReachedZeroRanOut() {
        assertTrue(new BloodUsage(BloodType.O_NEGATIVE, 10, 10, 0, 220, 300).ranOut());
        assertFalse(new BloodUsage(BloodType.A_POSITIVE, 10, 4, 6, -1, 0).ranOut());
    }

    @Test
    void theUnitsMustAddUp() {
        assertThrows(IllegalArgumentException.class, () -> new BloodUsage(BloodType.A_POSITIVE, 10, 4, 5, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BloodUsage(null, 10, 4, 6, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BloodUsage(BloodType.A_POSITIVE, 10, 10, 0, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> new BloodUsage(BloodType.A_POSITIVE, 10, 10, 0, 5, 0));
    }
}
