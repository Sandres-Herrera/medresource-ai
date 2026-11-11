package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la opción que se elige en el formulario, sin ventana.
 */
class ChoiceTileTest {

    private final ChoiceTile tile = new ChoiceTile(HomeIcon.WARNING, HomeTheme.DANGER, "Catástrofe", "70 % en 48 min");

    @Test
    void keepsItsTitleAndItsLine() {
        assertEquals("Catástrofe", tile.getTitle());
        assertEquals("70 % en 48 min", tile.getDetail());
        assertEquals("Catástrofe: 70 % en 48 min", tile.getToolTipText());
    }

    @Test
    void itsLineCanChange() {
        tile.setDetail("70 % en 12 min");

        assertEquals("70 % en 12 min", tile.getDetail());
        assertEquals("70 % en 12 min", tile.getAccessibleContext().getAccessibleDescription());
    }

    @Test
    void theChosenOneHasItsColorOnTheBorderAndATintedBackground() {
        assertFalse(tile.isSelected());
        assertEquals(HomeTheme.FIELD, tile.background());
        assertEquals(HomeTheme.CARD_BORDER, tile.border());

        tile.setSelected(true);

        assertTrue(tile.isSelected());
        assertEquals(HomeTheme.DANGER, tile.border());
        assertNotEquals(HomeTheme.FIELD, tile.background());
        assertTrue(tile.background().getRed() > HomeTheme.FIELD.getRed(), "el fondo se tiñe de rojo");
    }

    @Test
    void withTheMouseOnTopItLightsUp() {
        tile.setHover(true);

        assertEquals(HomeTheme.CARD_HOVER, tile.background());
        assertNotEquals(HomeTheme.CARD_BORDER, tile.border());
    }

    @Test
    void isPressedLikeAButton() {
        List<String> clicks = new ArrayList<>();
        tile.addActionListener(event -> clicks.add(tile.getTitle()));

        tile.doClick();

        assertEquals(List.of("Catástrofe"), clicks);
    }

    @Test
    void paintsItsBackgroundAndKeepsItsHeight() {
        tile.setSelected(true);
        tile.setSize(220, ChoiceTile.HEIGHT);
        BufferedImage image = new BufferedImage(220, ChoiceTile.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        tile.paint(graphics);
        graphics.dispose();

        assertEquals(tile.background().getRGB(), image.getRGB(200, 6));
        assertEquals(ChoiceTile.HEIGHT, tile.getPreferredSize().height);
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new ChoiceTile(null, HomeTheme.ACCENT, "Normal", "x"));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceTile(HomeIcon.HEART, null, "Normal", "x"));
        assertThrows(IllegalArgumentException.class, () -> new ChoiceTile(HomeIcon.HEART, HomeTheme.ACCENT, "Normal", " "));
        assertThrows(IllegalArgumentException.class, () -> tile.setDetail(null));
    }
}
