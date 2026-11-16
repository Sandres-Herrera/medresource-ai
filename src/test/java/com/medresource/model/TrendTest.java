package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de la tendencia de un indicador.
 */
class TrendTest {

    @Test
    void hasTheNamesThatAreShown() {
        assertEquals("Mejoró", Trend.IMPROVED.getDisplayName());
        assertEquals("Empeoró", Trend.WORSENED.getDisplayName());
        assertEquals("Igual", Trend.UNCHANGED.getDisplayName());
    }
}
