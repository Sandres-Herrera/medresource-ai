package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la barra fina de medida, sin ventana.
 */
class MeterBarTest {

    private final MeterBar bar = new MeterBar(HomeTheme.GREEN);

    private BufferedImage paint() {
        bar.setSize(100, MeterBar.HEIGHT);
        BufferedImage image = new BufferedImage(100, MeterBar.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        bar.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void fillsThePartGivenWithItsColor() {
        bar.setFraction(0.5);
        BufferedImage image = paint();

        assertEquals(HomeTheme.GREEN.getRGB(), image.getRGB(25, MeterBar.HEIGHT / 2));
        assertEquals(MeterBar.TRACK.getRGB(), image.getRGB(75, MeterBar.HEIGHT / 2));
    }

    @Test
    void keepsThePartBetweenZeroAndOne() {
        bar.setFraction(1.7);
        assertEquals(1.0, bar.getFraction(), 0.0);

        bar.setFraction(-2);
        assertEquals(0.0, bar.getFraction(), 0.0);

        bar.setFraction(Double.NaN);
        assertEquals(0.0, bar.getFraction(), 0.0);
    }

    @Test
    void itsColorCanChange() {
        bar.setColor(HomeTheme.RED);

        assertEquals(HomeTheme.RED, bar.getColor());
        assertThrows(IllegalArgumentException.class, () -> bar.setColor(null));
        assertThrows(IllegalArgumentException.class, () -> new MeterBar(null));
    }

    @Test
    void takesTheWholeWidthWithAFixedHeight() {
        assertEquals(MeterBar.HEIGHT, bar.getPreferredSize().height);
        assertEquals(Integer.MAX_VALUE, bar.getMaximumSize().width);
    }
}
