package com.medresource.ui.view;

import com.medresource.ui.render.IsoTheme;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del selector del tema de la vista 2.5D, sin ventana.
 */
class ThemeToggleTest {

    @Test
    void startsInTheLightTheme() {
        ThemeToggle toggle = new ThemeToggle();

        assertEquals(IsoTheme.LIGHT, toggle.getTheme());
        assertTrue(toggle.lightPill().isSelected());
        assertFalse(toggle.darkPill().isSelected());
        assertEquals("Claro", toggle.lightPill().getText());
        assertEquals("Oscuro", toggle.darkPill().getText());
    }

    @Test
    void choosingAThemeMarksItAndTellsTheListenerOnlyWhenItChanges() {
        ThemeToggle toggle = new ThemeToggle();
        List<IsoTheme> chosen = new ArrayList<>();
        toggle.onChange(chosen::add);

        toggle.darkPill().doClick();
        toggle.darkPill().doClick();
        toggle.lightPill().doClick();

        assertEquals(List.of(IsoTheme.DARK, IsoTheme.LIGHT), chosen);
    }

    @Test
    void markingAThemeDoesNotTellAnyone() {
        ThemeToggle toggle = new ThemeToggle();
        List<IsoTheme> chosen = new ArrayList<>();
        toggle.onChange(chosen::add);

        toggle.setTheme(IsoTheme.DARK);

        assertEquals(IsoTheme.DARK, toggle.getTheme());
        assertTrue(chosen.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> toggle.onChange(null));
        assertThrows(IllegalArgumentException.class, () -> toggle.setTheme(null));
    }
}
