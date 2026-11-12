package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la etiqueta en forma de pastilla, sin ventana.
 */
class ChipTest {

    @Test
    void aTintedChipHasTheColorOnTheTextAndAlmostTransparentOnTheBackground() {
        Chip chip = Chip.tinted("Triage III · Urgente", HomeTheme.YELLOW);

        assertEquals(HomeTheme.YELLOW, chip.textColor());
        assertEquals(Chip.TINT_ALPHA, chip.fillColor().getAlpha());
        assertEquals(HomeTheme.YELLOW.getRed(), chip.fillColor().getRed());
        assertNull(chip.borderColor());
    }

    @Test
    void itsTextAndColorsCanChange() {
        Chip chip = new Chip("En espera", HomeTheme.MUTED, HomeTheme.CHIP, null);

        chip.setText("En tratamiento");
        chip.setColors(HomeTheme.ACCENT, HomeTheme.SELECTED, HomeTheme.ACCENT);

        assertEquals("En tratamiento", chip.getText());
        assertEquals(HomeTheme.ACCENT, chip.borderColor());
    }

    @Test
    void isWiderThanItsTextAndPaintsItsBackground() {
        Chip chip = new Chip("Fiebre alta · III", HomeTheme.TEXT, HomeTheme.CHIP, null);
        chip.setSize(chip.getPreferredSize());
        BufferedImage image = new BufferedImage(chip.getWidth(), chip.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        chip.paint(graphics);
        graphics.dispose();

        assertEquals(Chip.HEIGHT, chip.getHeight());
        assertTrue(chip.getWidth() > 20);
        assertEquals(HomeTheme.CHIP.getRGB(), image.getRGB(4, Chip.HEIGHT / 2));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new Chip(" ", Color.WHITE, Color.BLACK, null));
        assertThrows(IllegalArgumentException.class, () -> new Chip("Hola", null, Color.BLACK, null));
        assertThrows(IllegalArgumentException.class, () -> Chip.tinted("Hola", null));
    }
}
