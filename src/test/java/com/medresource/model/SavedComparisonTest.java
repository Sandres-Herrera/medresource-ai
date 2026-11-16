package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la comparación guardada.
 */
class SavedComparisonTest {

    private static final LocalDateTime SAVED_AT = LocalDateTime.of(2026, 11, 16, 11, 55);
    private static final RunComparison COMPARISON = ComparisonFixtures.strategies(1, 2);

    @Test
    void keepsItsDataAndHasATitleForTheList() {
        SavedComparison saved = new SavedComparison(3, SAVED_AT, COMPARISON);

        assertEquals(SAVED_AT, saved.savedAt());
        assertEquals(COMPARISON, saved.comparison());
        assertEquals("Comparación #3 · Ejecución #1 vs #2 · Estrategias", saved.title());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new SavedComparison(0, SAVED_AT, COMPARISON));
        assertThrows(IllegalArgumentException.class, () -> new SavedComparison(1, null, COMPARISON));
        assertThrows(IllegalArgumentException.class, () -> new SavedComparison(1, SAVED_AT, null));
    }
}
