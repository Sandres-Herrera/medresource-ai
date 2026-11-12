package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del botón de icono, sin ventana.
 */
class IconButtonTest {

    private final IconButton button = new IconButton(HomeIcon.PLUS, "Aumentar camas", 24);

    @Test
    void isSquareWithTheRequestedSize() {
        assertEquals(new Dimension(24, 24), button.getPreferredSize());
        assertEquals(button.getPreferredSize(), button.getMaximumSize());
        assertEquals(button.getPreferredSize(), button.getMinimumSize());
    }

    @Test
    void itsNameIsTheHelpAndWhatScreenReadersSay() {
        assertEquals("Aumentar camas", button.getToolTipText());
        assertEquals("Aumentar camas", button.getAccessibleContext().getAccessibleName());
        assertEquals(HomeIcon.PLUS, button.getIcon());
    }

    @Test
    void doesItsActionOnlyWhileEnabled() {
        List<String> clicks = new ArrayList<>();
        button.addActionListener(event -> clicks.add("+"));

        button.doClick();
        button.setEnabled(false);
        button.doClick();

        assertEquals(List.of("+"), clicks);
    }

    @Test
    void theIconIsGrayWhiteWithTheMouseAndFaintWhenDisabled() {
        assertEquals(HomeTheme.MUTED, button.iconColor());

        button.setHover(true);

        assertEquals(HomeTheme.TEXT, button.iconColor());

        button.setEnabled(false);

        assertEquals(HomeTheme.FAINT, button.iconColor());
    }

    @Test
    void paintsItsIcon() {
        button.setSize(24, 24);
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        button.paint(graphics);
        graphics.dispose();

        assertTrue((image.getRGB(12, 12) & 0xFFFFFF) != 0, "el centro del «+» se pinta");
    }

    @Test
    void rejectsMissingDataOrATinySize() {
        assertThrows(IllegalArgumentException.class, () -> new IconButton(null, "Cerrar", 24));
        assertThrows(IllegalArgumentException.class, () -> new IconButton(HomeIcon.CLOSE, " ", 24));
        assertThrows(IllegalArgumentException.class, () -> new IconButton(HomeIcon.CLOSE, "Cerrar", 8));
    }

    @Test
    void theFilledButtonIsPaintedWithTheAccentAndADarkIcon() {
        button.setFilled(true);
        button.setSize(24, 24);
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        button.paint(graphics);
        graphics.dispose();

        assertTrue(button.isFilled());
        assertEquals(HomeTheme.ACCENT_TEXT, button.iconColor());
        assertEquals(HomeTheme.ACCENT.getRGB(), image.getRGB(3, 12));
    }

    @Test
    void theOutlinedButtonHasACardBackgroundAndAWhiteIcon() {
        button.setOutlined(true);
        button.setSize(24, 24);
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        button.paint(graphics);
        graphics.dispose();

        assertTrue(button.isOutlined());
        assertEquals(HomeTheme.TEXT, button.iconColor());
        assertEquals(HomeTheme.CARD.getRGB(), image.getRGB(3, 12));
    }

    @Test
    void theIconCanChange() {
        button.setIcon(HomeIcon.PAUSE);

        assertEquals(HomeIcon.PAUSE, button.getIcon());
        assertThrows(IllegalArgumentException.class, () -> button.setIcon(null));
    }
}
