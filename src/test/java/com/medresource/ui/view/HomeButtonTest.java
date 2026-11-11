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
 * Pruebas de los botones de la pantalla de inicio, dibujados sobre imágenes en memoria.
 */
class HomeButtonTest {

    private static BufferedImage paint(HomeButton button) {
        Dimension size = button.getPreferredSize();
        button.setSize(size);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(HomeTheme.BACKGROUND);
        graphics.fillRect(0, 0, size.width, size.height);
        button.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** Cuenta los píxeles de la fila del medio que no son del fondo. */
    private static int paintedInTheMiddleRow(BufferedImage image) {
        int painted = 0;
        int row = image.getHeight() / 2;
        for (int col = 0; col < image.getWidth(); col++) {
            if (image.getRGB(col, row) != HomeTheme.BACKGROUND.getRGB()) {
                painted++;
            }
        }
        return painted;
    }

    @Test
    void thePrimaryButtonIsAsWideAsTheCardsAndFilledWithTheAccent() {
        HomeButton button = new HomeButton(HomeButton.Style.PRIMARY, "Nueva simulación", HomeIcon.PLUS);
        BufferedImage image = paint(button);

        assertEquals(new Dimension(HomeButton.PRIMARY_WIDTH, HomeButton.PRIMARY_HEIGHT), button.getPreferredSize());
        assertEquals(HomeTheme.ACCENT.getRGB(), image.getRGB(20, HomeButton.PRIMARY_HEIGHT / 2));
    }

    @Test
    void thePrimaryButtonLightsUpWithTheMouseOverIt() {
        HomeButton button = new HomeButton(HomeButton.Style.PRIMARY, "Nueva simulación", null);
        button.setHover(true);

        assertEquals(HomeTheme.ACCENT_HOVER.getRGB(), paint(button).getRGB(20, HomeButton.PRIMARY_HEIGHT / 2));
    }

    @Test
    void theLinkIsOnlyItsText() {
        HomeButton link = new HomeButton(HomeButton.Style.LINK, "Salir", HomeIcon.PLUS);
        BufferedImage image = paint(link);

        assertTrue(link.getPreferredSize().width < 100);
        assertEquals(HomeTheme.BACKGROUND.getRGB(), image.getRGB(1, 1));
        assertTrue(paintedInTheMiddleRow(image) > 0, "se ve el texto");
    }

    @Test
    void keepsItsStyleAndText() {
        HomeButton link = new HomeButton(HomeButton.Style.LINK, "Salir", null);

        assertEquals(HomeButton.Style.LINK, link.getStyle());
        assertEquals("Salir", link.getText());
    }

    @Test
    void notifiesWhenItIsPressed() {
        HomeButton button = new HomeButton(HomeButton.Style.PRIMARY, "Nueva simulación", HomeIcon.PLUS);
        AtomicInteger clicks = new AtomicInteger();
        button.addActionListener(event -> clicks.incrementAndGet());

        button.doClick();
        button.doClick();

        assertEquals(2, clicks.get());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new HomeButton(null, "Salir", null));
        assertThrows(IllegalArgumentException.class, () -> new HomeButton(HomeButton.Style.LINK, null, null));
    }
}
