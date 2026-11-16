package com.medresource.ui.view;

import com.medresource.ui.view.SavedResultsPanel.Tab;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las pestañas de la ventana de reportes guardados.
 */
class SavedResultsPanelTest {

    private final SavedResultsPanel panel = new SavedResultsPanel();

    @Test
    void startsOnTheSimulations() {
        assertEquals(Tab.SIMULATIONS, panel.selectedTab());
        assertTrue(panel.pill(Tab.SIMULATIONS).isSelected());
        assertFalse(panel.pill(Tab.COMPARISONS).isSelected());
        assertTrue(panel.isShowing(Tab.SIMULATIONS));
        assertFalse(panel.isShowing(Tab.COMPARISONS));
    }

    @Test
    void clickingATabShowsItAndTellsWhoListens() {
        List<Tab> heard = new ArrayList<>();
        panel.onTab(heard::add);

        panel.pill(Tab.COMPARISONS).doClick();

        assertEquals(Tab.COMPARISONS, panel.selectedTab());
        assertTrue(panel.pill(Tab.COMPARISONS).isSelected());
        assertFalse(panel.pill(Tab.SIMULATIONS).isSelected());
        assertTrue(panel.isShowing(Tab.COMPARISONS));
        assertFalse(panel.isShowing(Tab.SIMULATIONS));
        assertEquals(List.of(Tab.COMPARISONS), heard);
    }

    @Test
    void theTabsAreCalledSimulationsAndComparisons() {
        assertEquals("Simulaciones", panel.pill(Tab.SIMULATIONS).getText());
        assertEquals("Comparaciones", panel.pill(Tab.COMPARISONS).getText());
    }

    @Test
    void needsATabAndAnAction() {
        assertThrows(IllegalArgumentException.class, () -> panel.select(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onTab(null));
    }
}
