package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.Door;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del edificio de la vista 2.5D sobre el hospital estándar.
 */
class IsoStructureTest {

    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final IsoProjection projection = IsoProjection.of(layout, new Renderer2D());
    private final IsoStructure structure = new IsoStructure(layout, projection);

    private Area first(ZoneType zone) {
        return layout.areasOf(zone).get(0);
    }

    @Test
    void theOutsideOfTheBuildingIsClosedAndTheFrontWallsAreLow() {
        Area triage = first(ZoneType.TRIAGE);

        assertEquals(IsoStructure.WALL_HEIGHT, structure.wallHeight(true, triage.col(), triage.row()), 0.0);
        assertEquals(IsoStructure.LOW_WALL_HEIGHT, structure.wallHeight(true, 0, layout.getRows()), 0.0);
        assertEquals(IsoStructure.LOW_WALL_HEIGHT, structure.wallHeight(false, layout.getCols(), 0), 0.0);
    }

    @Test
    void seenFromBehindTheFacadeOfTheBackIsTheLowOne() {
        IsoStructure turned = new IsoStructure(layout, projection.withView(180, IsoProjection.DEFAULT_ELEVATION));

        assertEquals(IsoStructure.WALL_HEIGHT, turned.wallHeight(true, 0, layout.getRows()), 0.0);
        assertEquals(IsoStructure.WALL_HEIGHT, turned.wallHeight(false, layout.getCols(), 0), 0.0);
        assertEquals(IsoStructure.LOW_WALL_HEIGHT, turned.wallHeight(true, 0, 0), 0.0);
        assertEquals(IsoStructure.LOW_WALL_HEIGHT, turned.wallHeight(false, 0, 0), 0.0);
    }

    @Test
    void theDepthOfTheWallsFollowsTheAngleOfTheCamera() {
        IsoProjection back = projection.withView(180, IsoProjection.DEFAULT_ELEVATION);
        List<IsoItem> items = new IsoStructure(layout, back).items();

        double min = items.stream().mapToDouble(IsoItem::depth).min().orElseThrow();
        double max = items.stream().mapToDouble(IsoItem::depth).max().orElseThrow();
        assertTrue(min >= back.depth(layout.getCols() + IsoProjection.SITE_MARGIN,
                layout.getRows() + IsoProjection.SITE_MARGIN) - 1e-9);
        assertTrue(max <= back.depth(-IsoProjection.SITE_MARGIN, -IsoProjection.SITE_MARGIN) + 1.5);
    }

    @Test
    void theBuildingCanBeDrawnFromAnyAngle() {
        for (double yaw = 0; yaw < 360; yaw += 60) {
            IsoProjection view = projection.withView(yaw, 45);
            IsoStructure building = new IsoStructure(layout, view);
            BufferedImage image = new BufferedImage(view.size().width, view.size().height,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();

            building.paintGround(graphics);
            building.items().forEach(item -> item.paint(graphics));

            graphics.dispose();
            Point2D center = view.at(layout.getCols() / 2.0, layout.getRows() / 2.0, 0);
            assertTrue(image.getRGB((int) center.getX(), (int) center.getY()) != 0, "giro " + yaw);
        }
    }

    @Test
    void thereIsAWallBetweenTwoRoomsButNotInsideARoomNorBetweenTwoCorridors() {
        Area waiting = first(ZoneType.WAITING_ROOM);

        assertTrue(structure.hasWall(false, waiting.col(), waiting.row() + 1), "entre el triage y la sala de espera");
        assertFalse(structure.hasWall(false, waiting.col() + 1, waiting.row() + 1), "dentro de la sala de espera");
        for (Area corridor : layout.areasOf(ZoneType.CORRIDOR)) {
            for (Area other : layout.areasOf(ZoneType.CORRIDOR)) {
                if (corridor.bottom() == other.row() && corridor.col() <= other.col()
                        && other.col() < corridor.right()) {
                    assertFalse(structure.hasWall(true, other.col(), other.row()), "entre dos pasillos");
                }
            }
        }
    }

    @Test
    void theDoorsAreOpeningsInTheWalls() {
        for (Door door : layout.getDoors()) {
            boolean horizontal = door.inside().col() == door.outside().col();
            int col = horizontal ? door.inside().col() : Math.max(door.inside().col(), door.outside().col());
            int row = horizontal ? Math.max(door.inside().row(), door.outside().row()) : door.inside().row();
            assertFalse(structure.hasWall(horizontal, col, row), door.toString());
        }
    }

    @Test
    void thePatientsComeInThroughAnOpeningAtTheEntrance() {
        Position entrance = layout.getEntrance();

        assertFalse(structure.hasWall(false, entrance.col(), entrance.row()));
        assertTrue(structure.hasWall(false, entrance.col(), entrance.row() + 1));
    }

    @Test
    void thereIsOneItemPerWallPerCornerPostAndPerDoorBesidesTheSignsAndTheOutdoors() {
        int posts = 0;
        for (int col = 0; col <= layout.getCols(); col++) {
            for (int row = 0; row <= layout.getRows(); row++) {
                boolean touched = structure.hasWall(true, col, row) || structure.hasWall(true, col - 1, row)
                        || structure.hasWall(false, col, row) || structure.hasWall(false, col, row - 1);
                if (touched) {
                    posts++;
                }
            }
        }

        assertTrue(structure.items().size() > structure.wallCount() + posts + layout.getDoors().size());
    }

    @Test
    void theWallsThatFaceACorridorAreWindows() {
        Area waiting = first(ZoneType.WAITING_ROOM);
        Area corridor = first(ZoneType.CORRIDOR);

        boolean window = false;
        for (int col = waiting.col(); col < waiting.right(); col++) {
            window |= structure.isWindow(true, col, corridor.row());
        }
        assertTrue(window, "la sala de espera da al pasillo");
        assertFalse(structure.isWindow(false, waiting.col(), waiting.row() + 3), "entre el triage y la sala");
        assertFalse(structure.isWindow(true, 0, layout.getRows()), "la fachada del frente es baja");
    }

    @Test
    void eachRoomHasItsSignWithTheNumberWhenThereAreSeveral() {
        assertEquals("UCI", structure.signOf(first(ZoneType.ICU)).orElseThrow());
        assertEquals("2 QUIRÓFANO", structure.signOf(layout.areasOf(ZoneType.OPERATING_ROOM).get(1)).orElseThrow());
        assertTrue(structure.signOf(first(ZoneType.CORRIDOR)).isEmpty());
    }

    @Test
    void findsTheAreaOfACellAlsoOutsideThePlan() {
        assertTrue(structure.areaCovering(-1, 0).isEmpty());
        assertTrue(structure.areaCovering(layout.getCols(), 0).isEmpty());
        assertEquals(ZoneType.TRIAGE, structure.areaCovering(0, 0).orElseThrow().zone());
    }

    @Test
    void theFloorsAndTheGrassTakeTheColorsOfTheTheme() {
        for (IsoTheme theme : IsoTheme.values()) {
            IsoStructure building = new IsoStructure(layout, projection, theme);
            BufferedImage image = new BufferedImage(projection.size().width, projection.size().height,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();

            building.paintGround(graphics);

            Area icu = first(ZoneType.ICU);
            Point2D floor = projection.at(icu.col() + 1.25, icu.row() + 2.25, 0);
            Color seen = new Color(image.getRGB((int) floor.getX(), (int) floor.getY()));
            Color expected = theme.floor(ZoneType.ICU);
            assertTrue(Math.abs(seen.getRed() - expected.getRed()) < 40, theme + ": " + seen);
            Point2D grass = projection.at(layout.getCols() + 1.6, layout.getRows() / 2.0, 0);
            Color lawn = new Color(image.getRGB((int) grass.getX(), (int) grass.getY()));
            assertTrue(lawn.getGreen() > lawn.getRed() && lawn.getGreen() > lawn.getBlue(), theme + ": " + lawn);
            assertEquals(theme, building.getTheme());
        }
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new IsoStructure(null, projection));
        assertThrows(IllegalArgumentException.class, () -> new IsoStructure(layout, null));
        assertThrows(IllegalArgumentException.class, () -> new IsoStructure(layout, projection, null));
    }
}
