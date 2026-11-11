package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del ajuste de las ventanas al espacio libre de la pantalla.
 */
class ScreenFitTest {

    /** Portátil de 1366 × 768 con la barra de tareas de 40 píxeles abajo. */
    private static final Rectangle LAPTOP = new Rectangle(0, 0, 1366, 728);

    @Test
    void aWindowThatFitsKeepsItsSize() {
        assertEquals(new Dimension(900, 600), ScreenFit.fit(new Dimension(900, 600), LAPTOP));
    }

    @Test
    void aWindowTooBigIsCutToTheFreeSpace() {
        assertEquals(new Dimension(1366, 728), ScreenFit.fit(new Dimension(1600, 900), LAPTOP));
        assertEquals(new Dimension(1000, 728), ScreenFit.fit(new Dimension(1000, 900), LAPTOP));
    }

    @Test
    void theWindowIsCenteredInTheFreeSpace() {
        assertEquals(new Rectangle(233, 64, 900, 600), ScreenFit.centered(new Dimension(900, 600), LAPTOP));
        assertEquals(new Rectangle(0, 0, 1366, 728), ScreenFit.centered(new Dimension(1600, 900), LAPTOP));
    }

    @Test
    void theFreeSpaceCanStartAwayFromTheCorner() {
        Rectangle taskbarOnTheLeft = new Rectangle(60, 0, 1306, 768);

        assertEquals(new Rectangle(60, 0, 1306, 768), ScreenFit.centered(new Dimension(1600, 900), taskbarOnTheLeft));
    }

    @Test
    void theSmallestScreenIsALaptopOf1366By768() {
        assertEquals(new Dimension(1366, 768), ScreenFit.SMALL_SCREEN);
    }

    @Test
    void rejectsMissingSizes() {
        assertThrows(IllegalArgumentException.class, () -> ScreenFit.fit(null, LAPTOP));
        assertThrows(IllegalArgumentException.class, () -> ScreenFit.fit(new Dimension(10, 10), null));
        assertThrows(IllegalArgumentException.class, () -> ScreenFit.fit(new Dimension(0, 10), LAPTOP));
        assertThrows(IllegalArgumentException.class, () -> ScreenFit.centered(new Dimension(10, 10),
                new Rectangle(0, 0, 10, 0)));
    }
}
