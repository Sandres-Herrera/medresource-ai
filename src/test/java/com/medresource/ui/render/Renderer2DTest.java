package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del dibujo del plano como tablero de salas.
 *
 * <p>Dibuja el hospital estándar en una imagen en memoria y comprueba el color de los píxeles.</p>
 */
class Renderer2DTest {

    private static final int CELL_W = Renderer2D.CELL_W;
    private static final int CELL_H = Renderer2D.CELL_H;
    private static final int BLACK = 0xFF000000;

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final BufferedImage image = draw();

    private BufferedImage draw() {
        Dimension size = renderer.worldSize(layout);
        BufferedImage picture = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = picture.createGraphics();
        renderer.render(graphics, layout);
        graphics.dispose();
        return picture;
    }

    private int pixelAtCenterOf(Position position) {
        Point2D center = renderer.centerOf(position);
        return image.getRGB((int) center.getX(), (int) center.getY());
    }

    /** Píxel de la esquina de arriba a la izquierda de una tarjeta grande, donde no hay texto. */
    private int cardCornerOf(Position position) {
        return image.getRGB(position.col() * CELL_W + 12, position.row() * CELL_H + 12);
    }

    /** Píxel de la esquina de arriba a la izquierda de una tarjeta pequeña, fuera de la flecha de la entrada. */
    private int smallCornerOf(Position position) {
        return image.getRGB(position.col() * CELL_W + 17, position.row() * CELL_H + 17);
    }

    @Test
    void theWorldIsOneCellBigForEachSquareOfThePlan() {
        Dimension size = renderer.worldSize(layout);

        assertEquals(layout.getCols() * CELL_W, size.width);
        assertEquals(layout.getRows() * CELL_H, size.height);
    }

    @Test
    void theCenterOfASquareIsInTheMiddleOfItsCell() {
        Point2D center = renderer.centerOf(new Position(2, 3));

        assertEquals(2 * CELL_W + CELL_W / 2.0, center.getX(), 0.0);
        assertEquals(3 * CELL_H + CELL_H / 2.0, center.getY(), 0.0);
    }

    @Test
    void doesNotDrawWithMissingData() {
        Graphics2D graphics = image.createGraphics();

        assertThrows(IllegalArgumentException.class, () -> renderer.worldSize(null));
        assertThrows(IllegalArgumentException.class, () -> renderer.centerOf(null));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(null, layout));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(graphics, null));
        graphics.dispose();
    }

    @Test
    void paintsEveryAreaWithTheColorOfItsZone() {
        for (Area area : layout.getAreas()) {
            int pixel = image.getRGB(area.col() * CELL_W + 40, area.row() * CELL_H + 5);

            assertEquals(Palette.zone(area.zone()).getRGB(), pixel, "Área " + area.title());
        }
    }

    @Test
    void doesNotPaintWhereThereIsNoArea() {
        // Fuera del borde de las cajas: la esquina del plano y la franja entre el triage y la sala de espera.
        assertEquals(BLACK, image.getRGB(0, 0));
        assertEquals(BLACK, image.getRGB(2 * CELL_W, 40));
    }

    @Test
    void drawsACardWhereEachBedIs() {
        Position bed = layout.positionOf("H1-C1").orElseThrow();

        assertEquals(Palette.CARD.getRGB(), image.getRGB(bed.col() * CELL_W + 8, bed.row() * CELL_H + 8));
        assertEquals(Palette.CARD.getRGB(), cardCornerOf(bed));
        assertEquals(Palette.CARD.getRGB(), cardCornerOf(layout.positionOf("UCI-C1").orElseThrow()));
    }

    @Test
    void drawsAnOperatingTableWhereEachOperatingRoomIs() {
        assertEquals(Palette.OPERATING_TABLE.getRGB(), cardCornerOf(layout.positionOf("Q1").orElseThrow()));
    }

    @Test
    void drawsASeatWhereEachMemberOfTheStaffIs() {
        assertEquals(Palette.STAFF_SEAT.getRGB(), cardCornerOf(layout.positionOf("D01").orElseThrow()));
        assertEquals(Palette.STAFF_SEAT.getRGB(), cardCornerOf(layout.positionOf("E01").orElseThrow()));
    }

    @Test
    void drawsASlabForEachPlaceOfTheMorgue() {
        for (Position spot : layout.getMorgueSpots()) {
            assertEquals(Palette.MORGUE_SLAB.getRGB(), pixelAtCenterOf(spot));
        }
    }

    @Test
    void drawsAChairForEachSeatOfTheWaitingRoom() {
        for (Position spot : layout.getWaitingSpots()) {
            assertEquals(Palette.WAITING_SEAT.getRGB(), pixelAtCenterOf(spot));
        }
    }

    @Test
    void drawsAnOrangeStationForEachTriagePlace() {
        for (Position spot : layout.getTriageSpots()) {
            assertEquals(Palette.TRIAGE_STATION.getRGB(), pixelAtCenterOf(spot));
        }
    }

    @Test
    void drawsARedCardWithTheLabelForEachBloodType() {
        for (Position spot : layout.getBloodSpots()) {
            assertEquals(Palette.BLOOD_UNIT.getRGB(), cardCornerOf(spot));
        }
        Position first = layout.getBloodSpots().get(0);
        int white = 0;
        for (int y = 0; y < CELL_H; y++) {
            for (int x = 0; x < CELL_W; x++) {
                if (image.getRGB(first.col() * CELL_W + x, first.row() * CELL_H + y) == Palette.CARD.getRGB()) {
                    white++;
                }
            }
        }

        assertTrue(white > 0);
    }

    @Test
    void marksTheEntranceWithAGreenArrow() {
        Position entrance = layout.getEntrance();

        assertEquals(Palette.ENTRANCE.getRGB(), image.getRGB(entrance.col() * CELL_W + 11, entrance.row() * CELL_H + 24));
        assertEquals(Palette.zone(ZoneType.TRIAGE).getRGB(), smallCornerOf(entrance));
    }

    @Test
    void anUnusedSquareOfAnAreaHasNoCard() {
        Area hospitalization = layout.areasOf(ZoneType.HOSPITALIZATION).get(0);
        Position unused = hospitalization.slot(30);

        assertEquals(Palette.zone(ZoneType.HOSPITALIZATION).getRGB(), cardCornerOf(unused));
        assertEquals(0, countLabelPixels(unused.col() * CELL_W, unused.row() * CELL_H, CELL_W, CELL_H));
    }

    @Test
    void writesTheTitleOfEveryArea() {
        for (Area area : layout.getAreas()) {
            assertTrue(countLabelPixels(area.col() * CELL_W, area.row() * CELL_H, area.cols() * CELL_W, 30) > 0,
                    "Título de " + area.title());
        }
    }

    @Test
    void writesTheCodeOfTheStaffInWhiteOverTheirBlueCard() {
        Position doctor = layout.positionOf("D01").orElseThrow();
        int white = 0;
        for (int y = 0; y < CELL_H; y++) {
            for (int x = 0; x < CELL_W; x++) {
                if (image.getRGB(doctor.col() * CELL_W + x, doctor.row() * CELL_H + y) == Palette.CARD.getRGB()) {
                    white++;
                }
            }
        }

        assertTrue(white > 0);
    }

    @Test
    void writesTheCodeOnEachCard() {
        Position bed = layout.positionOf("H1-C1").orElseThrow();

        assertTrue(countLabelPixels(bed.col() * CELL_W, bed.row() * CELL_H, CELL_W, CELL_H) > 0);
    }

    @Test
    void drawsTheSameWhenItIsDrawnAgain() {
        BufferedImage second = draw();

        for (int y = 0; y < image.getHeight(); y += 7) {
            for (int x = 0; x < image.getWidth(); x += 5) {
                assertEquals(image.getRGB(x, y), second.getRGB(x, y));
            }
        }
        assertNotEquals(BLACK, image.getRGB(40, 5));
    }

    /** Cuenta los píxeles del color del texto dentro de un rectángulo de la imagen. */
    private int countLabelPixels(int x, int y, int width, int height) {
        int count = 0;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                if (image.getRGB(x + dx, y + dy) == Palette.LABEL.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }
}