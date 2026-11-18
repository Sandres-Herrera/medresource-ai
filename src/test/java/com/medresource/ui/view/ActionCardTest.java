package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la tarjeta de una opción, dibujada sobre imágenes en memoria.
 */
class ActionCardTest {

    private static ActionCard quick() {
        return new ActionCard(HomeIcon.BOLT, HomeTheme.QUICK, HomeTheme.QUICK_TILE, "Simulación rápida",
                "60 pacientes · 2 horas · velocidad ×10", null);
    }

    private static ActionCard demo() {
        return new ActionCard(HomeIcon.WARNING, HomeTheme.DANGER, HomeTheme.DANGER_TILE, "Demostración",
                "100 pacientes · 2 horas · velocidad ×10", "DEMO");
    }

    private static BufferedImage paint(ActionCard card) {
        card.setSize(card.getPreferredSize());
        BufferedImage image = new BufferedImage(ActionCard.WIDTH, ActionCard.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(HomeTheme.BACKGROUND);
        graphics.fillRect(0, 0, ActionCard.WIDTH, ActionCard.HEIGHT);
        card.paint(graphics);
        graphics.dispose();
        return image;
    }

    @Test
    void keepsItsTexts() {
        ActionCard card = demo();

        assertEquals("Demostración", card.getTitle());
        assertEquals("100 pacientes · 2 horas · velocidad ×10", card.getDetail());
        assertEquals("DEMO", card.getBadge());
        assertNull(quick().getBadge());
        assertEquals("Demostración", card.getAccessibleContext().getAccessibleName());
    }

    @Test
    void hasAFixedSize() {
        assertEquals(new Dimension(ActionCard.WIDTH, ActionCard.HEIGHT), quick().getPreferredSize());
        assertEquals(quick().getPreferredSize(), quick().getMaximumSize());
    }

    @Test
    void paintsTheCardAndTheTileOfItsIcon() {
        BufferedImage image = paint(quick());

        assertEquals(HomeTheme.CARD.getRGB(), image.getRGB(ActionCard.WIDTH / 2, 6));
        assertEquals(HomeTheme.QUICK_TILE.getRGB(), image.getRGB(19, ActionCard.HEIGHT / 2 - 16));
    }

    @Test
    void theCardLightsUpWithTheMouseOverIt() {
        ActionCard card = quick();
        card.setHover(true);
        BufferedImage image = paint(card);

        assertEquals(HomeTheme.CARD_HOVER.getRGB(), image.getRGB(ActionCard.WIDTH / 2, 6));
    }

    @Test
    void theDemoHasARedBadgeBeforeItsArrow() {
        BufferedImage withBadge = paint(demo());
        BufferedImage withoutBadge = paint(quick());
        int x = ActionCard.WIDTH - 70;
        int y = ActionCard.HEIGHT / 2 - 8;

        assertEquals(HomeTheme.DANGER_TILE.getRGB(), withBadge.getRGB(x, y));
        assertEquals(HomeTheme.CARD.getRGB(), withoutBadge.getRGB(x, y));
        assertNotEquals(HomeTheme.CARD.getRGB(), arrowColumn(withBadge));
        assertNotEquals(HomeTheme.CARD.getRGB(), arrowColumn(withoutBadge));
    }

    /** Color más distinto del fondo en la columna de la flecha: la flecha está si no es el fondo. */
    private static int arrowColumn(BufferedImage image) {
        int x = ActionCard.WIDTH - 16 - 9;
        for (int y = 0; y < ActionCard.HEIGHT; y++) {
            if (image.getRGB(x, y) != HomeTheme.CARD.getRGB() && y > 4 && y < ActionCard.HEIGHT - 4) {
                return image.getRGB(x, y);
            }
        }
        return HomeTheme.CARD.getRGB();
    }

    @Test
    void eachPartOfTheDetailsCanHaveItsIcon() {
        ActionCard card = quick();
        BufferedImage plain = paint(card);

        card.setDetailIcons(HomeIcon.USERS, HomeIcon.CLOCK, HomeIcon.GAUGE);
        BufferedImage withIcons = paint(card);

        assertEquals(List.of(HomeIcon.USERS, HomeIcon.CLOCK, HomeIcon.GAUGE), card.getDetailIcons());
        assertNotEquals(pixels(plain), pixels(withIcons));
        assertThrows(IllegalArgumentException.class, () -> card.setDetailIcons(HomeIcon.USERS));
        assertThrows(IllegalArgumentException.class, () -> card.setDetailIcons(HomeIcon.USERS, null, HomeIcon.GAUGE));
        assertEquals(List.of(), demo().getDetailIcons());
    }

    private static int pixels(BufferedImage image) {
        return Arrays.hashCode(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0,
                image.getWidth()));
    }

    @Test
    void notifiesWhenItIsPressed() {
        ActionCard card = quick();
        AtomicInteger clicks = new AtomicInteger();
        card.addActionListener(event -> clicks.incrementAndGet());

        card.doClick();

        assertEquals(1, clicks.get());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActionCard(null, HomeTheme.QUICK, HomeTheme.QUICK_TILE, "A", "B", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ActionCard(HomeIcon.BOLT, null, HomeTheme.QUICK_TILE, "A", "B", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ActionCard(HomeIcon.BOLT, HomeTheme.QUICK, null, "A", "B", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ActionCard(HomeIcon.BOLT, HomeTheme.QUICK, HomeTheme.QUICK_TILE, " ", "B", null));
        assertThrows(IllegalArgumentException.class,
                () -> new ActionCard(HomeIcon.BOLT, HomeTheme.QUICK, HomeTheme.QUICK_TILE, "A", "", null));
    }
}
