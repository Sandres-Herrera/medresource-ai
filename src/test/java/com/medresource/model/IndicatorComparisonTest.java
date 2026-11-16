package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de un indicador de dos ejecuciones lado a lado.
 */
class IndicatorComparisonTest {

    @Test
    void fewerDeathsIsAnImprovement() {
        IndicatorComparison deaths = new IndicatorComparison("Fallecidos", "", 8, 4, true);

        assertEquals(-4, deaths.difference(), 1e-9);
        assertEquals(-50, deaths.relativeChangePercent().orElseThrow(), 1e-9);
        assertEquals(Trend.IMPROVED, deaths.trend());
    }

    @Test
    void moreAttendedPatientsIsAnImprovement() {
        IndicatorComparison attended = new IndicatorComparison("Pacientes atendidos", "", 80, 92, false);

        assertEquals(12, attended.difference(), 1e-9);
        assertEquals(15, attended.relativeChangePercent().orElseThrow(), 1e-9);
        assertEquals(Trend.IMPROVED, attended.trend());
    }

    @Test
    void aLongerWaitIsWorseAndEqualValuesAreUnchanged() {
        assertEquals(Trend.WORSENED, new IndicatorComparison("Espera promedio", "min", 22, 35, true).trend());
        assertEquals(Trend.WORSENED, new IndicatorComparison("Pacientes atendidos", "", 92, 80, false).trend());
        assertEquals(Trend.UNCHANGED, new IndicatorComparison("Fallecidos", "", 4, 4, true).trend());
    }

    @Test
    void theRelativeChangeIsEmptyWhenTheFirstValueIsZero() {
        IndicatorComparison unattended = new IndicatorComparison("Sin atención", "", 0, 3, true);

        assertEquals(OptionalDouble.empty(), unattended.relativeChangePercent());
        assertEquals(3, unattended.difference(), 1e-9);
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new IndicatorComparison(" ", "", 1, 2, true));
        assertThrows(IllegalArgumentException.class, () -> new IndicatorComparison("A", null, 1, 2, true));
        assertThrows(IllegalArgumentException.class, () -> new IndicatorComparison("A", "", Double.NaN, 2, true));
        assertThrows(IllegalArgumentException.class,
                () -> new IndicatorComparison("A", "", 1, Double.POSITIVE_INFINITY, true));
    }
}
