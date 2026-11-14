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
    void thereIsOneItemPerWallAndOnePerCornerPost() {
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

        assertEquals(structure.wallCount() + posts, structure.items().size());
    }

    @Test
    void findsTheAreaOfACellAlsoOutsideThePlan() {
        assertTrue(structure.areaCovering(-1, 0).isEmpty());
        assertTrue(structure.areaCovering(layout.getCols(), 0).isEmpty());
        assertEquals(ZoneType.TRIAGE, structure.areaCovering(0, 0).orElseThrow().zone());
    }

    @Test
    void theRoomsHaveANeonBorderAndTheirTag() {
        BufferedImage image = new BufferedImage(projection.size().width, projection.size().height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        structure.paintGround(graphics);
        structure.paintTags(graphics);

        Area icu = first(ZoneType.ICU);
        Point2D tag = projection.at(icu.col() + 0.4, icu.row() + 0.4, IsoStructure.TAG_HEIGHT);
        assertEquals(IsoPalette.accent(ZoneType.ICU).getRGB(),
                image.getRGB((int) tag.getX() - 20, (int) tag.getY() - 8));
        Point2D floor = projection.at(icu.col() + 1.5, icu.row() + 2.5, 0);
        Color seen = new Color(image.getRGB((int) floor.getX(), (int) floor.getY()));
        assertTrue(seen.getRed() >= IsoPalette.FLOOR.getRed() && seen.getRed() < 60,
                "el piso es oscuro, con el tinte del área: " + seen);
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new IsoStructure(null, projection));
        assertThrows(IllegalArgumentException.class, () -> new IsoStructure(layout, null));
    }
}
