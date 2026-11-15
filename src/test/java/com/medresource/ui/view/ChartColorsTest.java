package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los colores de las gráficas del reporte.
 */
class ChartColorsTest {

    @Test
    void onPaperTheTextsAreDarkAndOnScreenTheyAreLight() {
        assertTrue(brightness(ChartColors.PAPER.text()) < 80);
        assertTrue(brightness(ChartColors.PAPER.muted()) < 120);
        assertTrue(brightness(ChartColors.SCREEN.text()) > 200);
        assertTrue(brightness(ChartColors.PAPER.track()) > 200);
    }

    @Test
    void noColorCanBeMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChartColors(null, Color.GRAY, Color.GRAY, Color.GRAY, Color.GRAY));
        assertThrows(IllegalArgumentException.class,
                () -> new ChartColors(Color.BLACK, Color.GRAY, Color.GRAY, Color.GRAY, null));
    }

    private static int brightness(Color color) {
        return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
    }
}
