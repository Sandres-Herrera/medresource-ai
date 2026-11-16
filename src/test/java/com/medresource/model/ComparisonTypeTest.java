package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del tipo de comparación.
 */
class ComparisonTypeTest {

    @Test
    void theTypeDependsOnWhatChanged() {
        assertEquals(ComparisonType.STRATEGY, ComparisonType.of(true, false));
        assertEquals(ComparisonType.RESOURCES, ComparisonType.of(false, true));
        assertEquals(ComparisonType.MIXED, ComparisonType.of(true, true));
    }

    @Test
    void ifNothingChangedThereIsNothingToCompare() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ComparisonType.of(false, false));

        assertEquals("Las dos ejecuciones tienen la misma estrategia y los mismos recursos: darían los mismos "
                + "resultados.", error.getMessage());
    }

    @Test
    void everyTypeHasANameAndADescription() {
        assertEquals("Estrategias", ComparisonType.STRATEGY.getDisplayName());
        assertEquals("Recursos", ComparisonType.RESOURCES.getDisplayName());
        assertEquals("Mixta", ComparisonType.MIXED.getDisplayName());
        for (ComparisonType type : ComparisonType.values()) {
            assertFalse(type.getDescription().isBlank());
        }
    }
}
