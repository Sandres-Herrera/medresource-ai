package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del indicador grande, sin ventana.
 */
class KpiTileTest {

    @Test
    void showsItsNumberInItsColor() {
        KpiTile tile = new KpiTile("En espera", HomeTheme.AMBER);

        tile.setValue(3);

        assertEquals("En espera", tile.getTitle());
        assertEquals("3", tile.text());
        assertEquals(HomeTheme.AMBER, tile.valueColor());
    }

    @Test
    void showsTheTotalNextToTheNumber() {
        KpiTile tile = new KpiTile("Llegados", HomeTheme.TEXT);

        tile.setValue(12, 100);

        assertEquals("12 / 100", tile.text());
    }

    @Test
    void startsAtZero() {
        assertEquals("0", new KpiTile("Altas", HomeTheme.GREEN).text());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new KpiTile(" ", HomeTheme.GREEN));
        assertThrows(IllegalArgumentException.class, () -> new KpiTile("Altas", null));
    }
}
