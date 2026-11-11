package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los iconos de la pantalla de inicio, dibujados sobre imágenes en memoria.
 */
class HomeIconTest {

    private static final int SIZE = 48;

    /** Dibuja el icono en blanco sobre negro y cuenta los píxeles que pintó. */
    private static int paintedPixels(HomeIcon icon, int x, int y, int size, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        icon.paint(graphics, x, y, size, Color.WHITE);
        graphics.dispose();
        int painted = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if ((image.getRGB(col, row) & 0xFFFFFF) != 0) {
                    painted++;
                }
            }
        }
        return painted;
    }

    @Test
    void everyIconPaintsSomething() {
        for (HomeIcon icon : HomeIcon.values()) {
            assertTrue(paintedPixels(icon, 0, 0, SIZE, SIZE, SIZE) > 20, icon.name());
        }
    }

    @Test
    void anIconStaysInsideItsBox() {
        for (HomeIcon icon : HomeIcon.values()) {
            int alone = paintedPixels(icon, 20, 20, SIZE, 3 * SIZE, 3 * SIZE);
            int clipped = paintedPixels(icon, 20, 20, SIZE, 20 + SIZE + 3, 20 + SIZE + 3);

            assertEquals(alone, clipped, icon.name() + " se sale de su cuadro");
        }
    }

    @Test
    void aBiggerIconPaintsMore() {
        assertTrue(paintedPixels(HomeIcon.PULSE, 0, 0, 64, 64, 64) > paintedPixels(HomeIcon.PULSE, 0, 0, 16, 64, 64));
    }

    @Test
    void theIconsAreDifferentFromEachOther() {
        Set<Integer> counts = new HashSet<>();
        for (HomeIcon icon : HomeIcon.values()) {
            counts.add(paintedPixels(icon, 0, 0, SIZE, SIZE, SIZE));
        }

        assertEquals(HomeIcon.values().length, counts.size());
    }

    @Test
    void doesNotPaintWithMissingData() {
        Graphics2D graphics = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();

        assertThrows(IllegalArgumentException.class, () -> HomeIcon.BOLT.paint(null, 0, 0, 10, Color.WHITE));
        assertThrows(IllegalArgumentException.class, () -> HomeIcon.BOLT.paint(graphics, 0, 0, 10, null));
        assertThrows(IllegalArgumentException.class, () -> HomeIcon.BOLT.paint(graphics, 0, 0, 0, Color.WHITE));
    }
}
