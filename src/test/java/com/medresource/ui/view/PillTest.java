package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la pastilla que se elige en una fila, sin ventana.
 */
class PillTest {

    private final Pill pill = new Pill("Estándar");

    @Test
    void theChosenOneHasTheAccentText() {
        assertFalse(pill.isSelected());
        assertEquals(HomeTheme.MUTED, pill.textColor());

        pill.setSelected(true);

        assertTrue(pill.isSelected());
        assertEquals(HomeTheme.ACCENT, pill.textColor());
    }

    @Test
    void withTheMouseOnTopTheTextIsWhite() {
        pill.setHover(true);

        assertEquals(HomeTheme.TEXT, pill.textColor());
    }

    @Test
    void isPressedLikeAButton() {
        List<String> clicks = new ArrayList<>();
        pill.addActionListener(event -> clicks.add(pill.getText()));

        pill.doClick();

        assertEquals(List.of("Estándar"), clicks);
    }

    @Test
    void isWiderThanItsTextAndKeepsItsHeight() {
        assertEquals(Pill.HEIGHT, pill.getPreferredSize().height);
        assertTrue(pill.getPreferredSize().width > pill.getFontMetrics(HomeTheme.font(Font.PLAIN, 12.5f))
                .stringWidth("Estándar"));
    }

    @Test
    void theChosenOneIsPaintedWithTheDarkGreenBackground() {
        pill.setSelected(true);
        pill.setSize(90, Pill.HEIGHT);
        BufferedImage image = new BufferedImage(90, Pill.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        pill.paint(graphics);
        graphics.dispose();

        assertEquals(HomeTheme.SELECTED.getRGB(), image.getRGB(10, Pill.HEIGHT / 2));
    }

    @Test
    void rejectsAMissingText() {
        assertThrows(IllegalArgumentException.class, () -> new Pill(" "));
    }
}
