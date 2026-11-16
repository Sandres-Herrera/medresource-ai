package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la comparación de dos ejecuciones.
 */
class RunComparisonTest {

    private static final List<SettingChange> CHANGES =
            List.of(new SettingChange("Estrategia", "FIFO", "Prioridad clínica"));
    private static final List<IndicatorComparison> INDICATORS =
            List.of(new IndicatorComparison("Fallecidos", "", 8, 4, true));

    private static RunComparison comparison(int runA, int runB) {
        return new RunComparison(1, runA, runB, "FIFO", "Prioridad clínica", ComparisonType.STRATEGY, CHANGES,
                INDICATORS, "Prioridad clínica tuvo 4 fallecidos menos.", List.of("Un solo caso."));
    }

    @Test
    void keepsItsDataAndFindsAnIndicatorByName() {
        RunComparison comparison = comparison(1, 2);

        assertEquals(ComparisonType.STRATEGY, comparison.type());
        assertEquals(CHANGES, comparison.changes());
        assertEquals(Optional.of(INDICATORS.get(0)), comparison.indicator("Fallecidos"));
        assertEquals(Optional.empty(), comparison.indicator("Otro"));
        assertEquals("Ejecución #1 vs #2 · Estrategias", comparison.title());
    }

    @Test
    void theListsCannotBeChangedFromOutside() {
        List<String> limitations = new ArrayList<>(List.of("Un solo caso."));
        RunComparison comparison = new RunComparison(1, 1, 2, "FIFO", "FIFO", ComparisonType.RESOURCES, CHANGES,
                INDICATORS, "Conclusión.", limitations);
        limitations.add("Otra");

        assertEquals(1, comparison.limitations().size());
        assertThrows(UnsupportedOperationException.class, () -> comparison.changes().clear());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> comparison(2, 2));
        assertThrows(IllegalArgumentException.class, () -> comparison(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new RunComparison(1, 1, 2, "FIFO", "FIFO",
                ComparisonType.RESOURCES, List.of(), INDICATORS, "Conclusión.", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RunComparison(1, 1, 2, "FIFO", "FIFO",
                ComparisonType.RESOURCES, CHANGES, List.of(), "Conclusión.", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RunComparison(1, 1, 2, "FIFO", "FIFO",
                ComparisonType.RESOURCES, CHANGES, INDICATORS, " ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RunComparison(1, 1, 2, "FIFO", "FIFO",
                null, CHANGES, INDICATORS, "Conclusión.", List.of()));
    }
}
