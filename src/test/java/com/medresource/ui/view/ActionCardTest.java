package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
    void theDemoHasARedBadgeInsteadOfTheArrow() {
        BufferedImage withBadge = paint(demo());
        BufferedImage withArrow = paint(quick());
        int x = ActionCard.WIDTH - 22;
        int y = ActionCard.HEIGHT / 2 - 8;

        assertEquals(HomeTheme.DANGER_TILE.getRGB(), withBadge.getRGB(x, y));
        assertNotEquals(HomeTheme.DANGER_TILE.getRGB(), withArrow.getRGB(x, y));
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
