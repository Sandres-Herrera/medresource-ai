package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del logo que late, dibujado sobre imágenes en memoria.
 */
class LogoBadgeTest {

    private static BufferedImage paint(LogoBadge logo) {
        logo.setSize(logo.getPreferredSize());
        BufferedImage image = new BufferedImage(LogoBadge.SIZE, LogoBadge.SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(HomeTheme.BACKGROUND);
        graphics.fillRect(0, 0, LogoBadge.SIZE, LogoBadge.SIZE);
        logo.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void isASquareWithTheTileColor() {
        LogoBadge logo = new LogoBadge();
        BufferedImage image = paint(logo);

        assertEquals(new Dimension(LogoBadge.SIZE, LogoBadge.SIZE), logo.getPreferredSize());
        assertEquals(HomeTheme.LOGO_TILE.getRGB(), image.getRGB(LogoBadge.SIZE / 2, 10));
    }

    @Test
    void thePulseMovesAlongTheLineAndStartsAgain() {
        LogoBadge logo = new LogoBadge();
        logo.setPhase(0.99);

        logo.advance();

        assertTrue(logo.getPhase() < 0.1, "volvió a empezar");
        double before = logo.getPhase();
        logo.advance();
        assertEquals(before + LogoBadge.STEP, logo.getPhase(), 1e-9);
    }

    @Test
    void theLogoLooksDifferentAsThePulseMoves() {
        LogoBadge logo = new LogoBadge();
        logo.setPhase(0.2);
        BufferedImage early = paint(logo);
        logo.setPhase(0.8);
        BufferedImage late = paint(logo);

        boolean changed = false;
        for (int col = 0; col < LogoBadge.SIZE && !changed; col++) {
            changed = early.getRGB(col, LogoBadge.SIZE / 2) != late.getRGB(col, LogoBadge.SIZE / 2);
        }
        assertTrue(changed);
    }

    @Test
    void doesNotAnimateWhileItIsNotOnScreen() {
        assertFalse(new LogoBadge().isAnimating());
    }

    @Test
    void givesTheStillLogoAsAnIconImage() {
        BufferedImage icon = LogoBadge.image(64);

        assertEquals(64, icon.getWidth());
        assertEquals(0, icon.getRGB(0, 0) >>> 24, "la esquina redondeada es transparente");
        assertNotEquals(0, icon.getRGB(32, 32) >>> 24);
    }

    @Test
    void rejectsAPhaseOutOfRangeAndAnImageWithoutSize() {
        LogoBadge logo = new LogoBadge();

        assertThrows(IllegalArgumentException.class, () -> logo.setPhase(1.0));
        assertThrows(IllegalArgumentException.class, () -> logo.setPhase(-0.1));
        assertThrows(IllegalArgumentException.class, () -> LogoBadge.image(0));
    }
}
