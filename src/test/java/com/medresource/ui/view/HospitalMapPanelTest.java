package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.Renderer2D;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del panel que muestra el plano del hospital.
 *
 * <p>Dibuja en imágenes en memoria, así no necesita pantalla.</p>
 */
class HospitalMapPanelTest {

    private static final int MARGIN = HospitalMapPanel.MARGIN;
    private static final int CELL_W = Renderer2D.CELL_W;
    private static final int CELL_H = Renderer2D.CELL_H;

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout standard = layoutOf(HospitalConfig.standard());
    private final HospitalMapPanel panel = new HospitalMapPanel(standard, renderer);

    private static HospitalLayout layoutOf(HospitalConfig config) {
        return new LayoutFactory().create(new HospitalFactory().create(config));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new HospitalMapPanel(null, renderer));
        assertThrows(IllegalArgumentException.class, () -> new HospitalMapPanel(standard, null));
        assertThrows(IllegalArgumentException.class, () -> panel.showLayout(null));
    }

    @Test
    void thePictureHasTheRequestedSize() {
        BufferedImage image = panel.toImage(300, 200);

        assertEquals(300, image.getWidth());
        assertEquals(200, image.getHeight());
    }

    @Test
    void doesNotDrawAPictureWithoutSize() {
        assertThrows(IllegalArgumentException.class, () -> panel.toImage(0, 100));
        assertThrows(IllegalArgumentException.class, () -> panel.toImage(100, 0));
    }

    @Test
    void theBackgroundSurroundsThePlan() {
        BufferedImage image = panel.toImage(1000, 700);

        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(0, 0));
        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(999, 699));
    }

    @Test
    void thePlanKeepsItsRealSizeWhenThePictureJustFitsIt() {
        Dimension world = renderer.worldSize(standard);
        BufferedImage image = panel.toImage(world.width + 2 * MARGIN, world.height + 2 * MARGIN);

        Position bed = standard.positionOf("H1-C1").orElseThrow();

        assertEquals(Palette.zone(ZoneType.TRIAGE).getRGB(), image.getRGB(MARGIN + 40, MARGIN + 5));
        assertEquals(Palette.CARD.getRGB(), image.getRGB(MARGIN + bed.col() * CELL_W + 12, MARGIN + bed.row() * CELL_H + 12));
        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(MARGIN - 1, MARGIN - 1));
    }

    @Test
    void thePlanShrinksToFitASmallerPicture() {
        Dimension world = renderer.worldSize(standard);
        BufferedImage image = panel.toImage(world.width / 2 + 2 * MARGIN, world.height / 2 + 2 * MARGIN);

        assertEquals(Palette.zone(ZoneType.TRIAGE).getRGB(), image.getRGB(MARGIN + 20, MARGIN + 3));
        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(MARGIN - 1, MARGIN - 1));
    }

    @Test
    void aPictureNotBiggerThanTheMarginsOnlyHasTheBackground() {
        BufferedImage image = panel.toImage(2 * MARGIN, 2 * MARGIN);

        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(MARGIN, MARGIN));
    }

    @Test
    void paintsItselfWhenSwingAsksIt() {
        Dimension world = renderer.worldSize(standard);
        int width = world.width + 2 * MARGIN;
        int height = world.height + 2 * MARGIN;
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        panel.paint(graphics);
        graphics.dispose();

        assertEquals(Palette.zone(ZoneType.TRIAGE).getRGB(), image.getRGB(MARGIN + 40, MARGIN + 5));
    }

    @Test
    void showsTheNewPlanWhenItIsChanged() {
        Dimension before = panel.getPreferredSize();

        panel.showLayout(layoutOf(new HospitalConfig(1, 1, 1, 1, 1, 0, 1)));

        assertNotEquals(before, panel.getPreferredSize());
    }

    @Test
    void thePreferredSizeIsTheWorldPlusTheMarginsButNeverBiggerThanAScreen() {
        HospitalLayout small = layoutOf(new HospitalConfig(1, 1, 1, 1, 1, 0, 1));
        Dimension smallWorld = renderer.worldSize(small);

        panel.showLayout(small);

        assertEquals(new Dimension(smallWorld.width + 2 * MARGIN, smallWorld.height + 2 * MARGIN),
                panel.getPreferredSize());

        panel.showLayout(layoutOf(new HospitalConfig(200, 50, 20, 50, 100, 100, 50)));

        assertEquals(new Dimension(1200 + 2 * MARGIN, 640 + 2 * MARGIN), panel.getPreferredSize());
    }
}