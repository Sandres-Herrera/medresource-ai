package com.medresource.ui.view;

import com.medresource.ui.controller.LiveCounters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la tarjeta de recursos libres, sin ventana.
 */
class ResourcesPanelTest {

    private static LiveCounters counters(int freeBeds, int freeDoctors, int morgueOccupancy) {
        return new LiveCounters(20, 100, 10, 2, 5, 1, 2, 0, 0, 0, freeBeds, 30, 6, 6, 4, 4, freeDoctors, 16, 22, 24,
                80, morgueOccupancy, 5);
    }

    @Test
    void showsHowManyAreFreeOfHowMany() {
        ResourcesPanel panel = new ResourcesPanel();

        panel.show(counters(27, 13, 1));

        assertEquals("27 / 30", panel.valueOf("Camas"));
        assertEquals("13 / 16", panel.valueOf("Médicos"));
        assertEquals("4 / 5", panel.valueOf("Morgue (libre)"));
        assertEquals("80", panel.valueOf(ResourcesPanel.BLOOD));
        assertEquals(0.9, panel.barOf("Camas").getFraction(), 1e-9);
    }

    @Test
    void theBarIsTealOrangeOrRedAsTheResourceRunsOut() {
        assertEquals(HomeTheme.ACCENT, ResourcesPanel.barColor(16, 30));
        assertEquals(HomeTheme.ORANGE, ResourcesPanel.barColor(4, 16));
        assertEquals(HomeTheme.RED, ResourcesPanel.barColor(0, 16));

        ResourcesPanel panel = new ResourcesPanel();
        panel.show(counters(0, 3, 5));

        assertEquals(HomeTheme.RED, panel.barOf("Camas").getColor());
        assertEquals(HomeTheme.ORANGE, panel.barOf("Médicos").getColor());
        assertEquals(HomeTheme.RED, panel.barOf("Morgue (libre)").getColor());
    }

    @Test
    void theBloodHasNoBarAndUnknownRowsHaveNoText() {
        ResourcesPanel panel = new ResourcesPanel();

        assertNull(panel.barOf(ResourcesPanel.BLOOD));
        assertEquals("", panel.valueOf("Ambulancias"));
        assertThrows(IllegalArgumentException.class, () -> panel.show(null));
    }
}
