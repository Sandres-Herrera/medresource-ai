package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la tarjeta del tema oscuro, sin ventana.
 */
class ThemedCardTest {

    private final ThemedCard card = new ThemedCard(new BorderLayout(), 12);

    private BufferedImage paint() {
        card.setSize(120, 80);
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        card.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void hasTheCardBackgroundAndTheGivenPadding() {
        assertEquals(HomeTheme.CARD.getRGB(), paint().getRGB(60, 40));
        assertEquals(new Insets(12, 12, 12, 12), card.getInsets());
        assertFalse(card.isOpaque());
    }

    @Test
    void theHighlightedCardHasTheAccentBorder() {
        assertEquals(HomeTheme.CARD_BORDER, card.borderColor());

        card.setHighlighted(true);

        assertTrue(card.isHighlighted());
        assertEquals(HomeTheme.ACCENT, card.borderColor());
        assertEquals(HomeTheme.ACCENT.getRGB(), paint().getRGB(60, 1));
    }

    @Test
    void theHeadingHasTheIconAndTheTitle() {
        assertEquals(2, ThemedCard.heading(HomeIcon.MAP, "Plano del hospital", HomeTheme.ACCENT).getComponentCount());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new ThemedCard(null, 4));
        assertThrows(IllegalArgumentException.class, () -> new ThemedCard(new BorderLayout(), -1));
        assertThrows(IllegalArgumentException.class, () -> ThemedCard.heading(null, "Título", HomeTheme.ACCENT));
        assertThrows(IllegalArgumentException.class, () -> ThemedCard.heading(HomeIcon.MAP, " ", HomeTheme.ACCENT));
    }
}
