package com.medresource.ui.view;

import com.medresource.model.CollapsePeriod;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la línea de tiempo del reporte, sin ventana.
 */
class TimelineStripTest {

    @Test
    void eachMinuteFallsInItsPartOfTheWidth() {
        TimelineStrip strip = new TimelineStrip(600, 480, List.of(new CollapsePeriod(50, 180, 3, 90)));

        assertEquals(0.0, strip.fractionAt(0), 1e-9);
        assertEquals(0.5, strip.fractionAt(300), 1e-9);
        assertEquals(1.0, strip.fractionAt(900), 1e-9);
        assertEquals(1, strip.getCollapses().size());
    }

    @Test
    void writesTheHourEveryHourOrLessOftenIfTheSimulationIsLong() {
        assertEquals(60, new TimelineStrip(600, 480, List.of()).tickMinutes());
        assertEquals(60, new TimelineStrip(720, 480, List.of()).tickMinutes());
        assertEquals(120, new TimelineStrip(1000, 480, List.of()).tickMinutes());
        assertEquals(240, new TimelineStrip(1800, 1440, List.of()).tickMinutes());
    }

    @Test
    void canBePainted() {
        TimelineStrip strip = new TimelineStrip(530, 480, List.of(new CollapsePeriod(50, 180, 3, 90),
                new CollapsePeriod(300, 300, 1, 300)));
        strip.setSize(700, TimelineStrip.HEIGHT);
        BufferedImage image = new BufferedImage(700, TimelineStrip.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D surface = image.createGraphics();

        assertDoesNotThrow(() -> strip.paint(surface));
        surface.dispose();
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineStrip(0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TimelineStrip(600, -1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TimelineStrip(600, 480, null));
    }
}
