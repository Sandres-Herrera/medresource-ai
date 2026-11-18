package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los botones de los accesos rápidos, dibujados sobre imágenes en memoria.
 */
class QuickAccessButtonTest {

    private static BufferedImage paint(QuickAccessButton button) {
        button.setSize(button.getPreferredSize());
        BufferedImage image = new BufferedImage(QuickAccessButton.WIDTH, QuickAccessButton.HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(HomeTheme.BACKGROUND);
        graphics.fillRect(0, 0, QuickAccessButton.WIDTH, QuickAccessButton.HEIGHT);
        button.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** Cuenta los píxeles con el color de acento: el icono, y el borde cuando se ilumina. */
    private static int accentPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == HomeTheme.ACCENT.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void allHaveTheSameSizeAndKeepTheirIconAndText() {
        QuickAccessButton button = new QuickAccessButton(HomeIcon.CHART, "Reportes guardados");

        assertEquals(new Dimension(QuickAccessButton.WIDTH, QuickAccessButton.HEIGHT), button.getPreferredSize());
        assertEquals(button.getPreferredSize(),
                new QuickAccessButton(HomeIcon.EXIT, "Salir").getPreferredSize());
        assertEquals(HomeIcon.CHART, button.getIcon());
        assertEquals("Reportes guardados", button.getText());
        assertEquals("Reportes guardados", button.getAccessibleContext().getAccessibleName());
    }

    @Test
    void paintsACardWithItsIconAndLightsUpWithTheMouse() {
        QuickAccessButton button = new QuickAccessButton(HomeIcon.SWAP, "Comparar simulaciones");
        BufferedImage still = paint(button);

        assertEquals(HomeTheme.CARD.getRGB(), still.getRGB(QuickAccessButton.WIDTH / 2, 4));
        assertTrue(accentPixels(still) > 0);

        button.setHover(true);
        BufferedImage lit = paint(button);

        assertEquals(HomeTheme.CARD_HOVER.getRGB(), lit.getRGB(QuickAccessButton.WIDTH / 2, 4));
        assertTrue(accentPixels(lit) > accentPixels(still));
    }

    @Test
    void notifiesWhenItIsPressed() {
        QuickAccessButton button = new QuickAccessButton(HomeIcon.EXIT, "Salir");
        AtomicInteger clicks = new AtomicInteger();
        button.addActionListener(event -> clicks.incrementAndGet());

        button.doClick();

        assertEquals(1, clicks.get());
    }

    @Test
    void needsAnIconAndAText() {
        assertThrows(IllegalArgumentException.class, () -> new QuickAccessButton(null, "Salir"));
        assertThrows(IllegalArgumentException.class, () -> new QuickAccessButton(HomeIcon.EXIT, " "));
    }
}
