package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del resultado de guardar una comparación.
 */
class ComparisonSaveResultTest {

    @Test
    void keepsTheSavedComparisonAndWhetherItWasAlreadySaved() {
        SavedComparison saved = new SavedComparison(1, LocalDateTime.of(2026, 11, 16, 12, 0),
                ComparisonFixtures.strategies(1, 2));

        ComparisonSaveResult result = new ComparisonSaveResult(saved, true);

        assertSame(saved, result.saved());
        assertTrue(result.alreadySaved());
        assertThrows(IllegalArgumentException.class, () -> new ComparisonSaveResult(null, false));
    }
}
