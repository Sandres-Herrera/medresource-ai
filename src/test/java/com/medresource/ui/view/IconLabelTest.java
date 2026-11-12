package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del icono sin acción.
 */
class IconLabelTest {

    @Test
    void keepsItsIconSizeAndColor() {
        IconLabel label = new IconLabel(HomeIcon.INFO, 16, HomeTheme.MUTED);

        assertEquals(HomeIcon.INFO, label.getIcon());
        assertEquals(new Dimension(16, 16), label.getPreferredSize());

        label.setColor(HomeTheme.ACCENT);

        assertEquals(HomeTheme.ACCENT, label.getColor());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new IconLabel(null, 16, HomeTheme.MUTED));
        assertThrows(IllegalArgumentException.class, () -> new IconLabel(HomeIcon.INFO, 0, HomeTheme.MUTED));
        assertThrows(IllegalArgumentException.class, () -> new IconLabel(HomeIcon.INFO, 16, HomeTheme.MUTED)
                .setColor(null));
    }
}
