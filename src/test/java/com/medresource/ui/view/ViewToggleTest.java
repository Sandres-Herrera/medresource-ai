package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del selector de la vista del plano, sin ventana.
 */
class ViewToggleTest {

    @Test
    void startsInTheFlatView() {
        ViewToggle toggle = new ViewToggle();

        assertFalse(toggle.isIsometric());
        assertTrue(toggle.flatPill().isSelected());
        assertFalse(toggle.isometricPill().isSelected());
        assertEquals(ViewToggle.FLAT, toggle.flatPill().getText());
        assertEquals(ViewToggle.ISOMETRIC, toggle.isometricPill().getText());
    }

    @Test
    void choosingAViewMarksItAndTellsTheListenerOnlyWhenItChanges() {
        ViewToggle toggle = new ViewToggle();
        List<Boolean> chosen = new ArrayList<>();
        toggle.onChange(chosen::add);

        toggle.isometricPill().doClick();
        toggle.isometricPill().doClick();
        toggle.flatPill().doClick();

        assertEquals(List.of(true, false), chosen);
        assertFalse(toggle.isIsometric());
    }

    @Test
    void markingAViewDoesNotTellAnyone() {
        ViewToggle toggle = new ViewToggle();
        List<Boolean> chosen = new ArrayList<>();
        toggle.onChange(chosen::add);

        toggle.setIsometric(true);

        assertTrue(toggle.isIsometric());
        assertTrue(chosen.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> toggle.onChange(null));
    }
}
