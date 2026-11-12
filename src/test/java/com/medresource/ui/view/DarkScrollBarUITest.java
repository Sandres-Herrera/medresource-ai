package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la barra de desplazamiento oscura, sin ventana.
 */
class DarkScrollBarUITest {

    @Test
    void makesBothBarsDarkAndThin() {
        JScrollPane scroll = new JScrollPane(new JPanel());

        DarkScrollBarUI.apply(scroll);

        assertInstanceOf(DarkScrollBarUI.class, scroll.getVerticalScrollBar().getUI());
        assertInstanceOf(DarkScrollBarUI.class, scroll.getHorizontalScrollBar().getUI());
        assertEquals(DarkScrollBarUI.THICKNESS, scroll.getVerticalScrollBar().getPreferredSize().width);
    }

    @Test
    void rejectsAMissingPanel() {
        assertThrows(IllegalArgumentException.class, () -> DarkScrollBarUI.apply(null));
    }
}
