package com.medresource.ui.render;

import com.medresource.model.Area;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los muebles de la vista 2.5D sobre el hospital estándar.
 */
class IsoFurnitureTest {

    private final Renderer2D flat = new Renderer2D();
    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final IsoProjection projection = IsoProjection.of(layout, flat);
    private final IsoFurniture furniture = new IsoFurniture(layout, projection, flat);

    private long resourcesIn(ZoneType zone) {
        return layout.getResourceCodes().stream()
                .filter(code -> layout.areaAt(layout.positionOf(code).orElseThrow()).map(Area::zone)
                        .orElseThrow() == zone).count();
    }

    @Test
    void thereIsAPieceOfFurnitureForEachPlaceOfThePlan() {
        long beds = resourcesIn(ZoneType.HOSPITALIZATION);
        long icu = resourcesIn(ZoneType.ICU);
        long operating = resourcesIn(ZoneType.OPERATING_ROOM);
        Set<Position> tables = new HashSet<>();
        long desks = 0;
        for (String code : layout.getStaffCodes()) {
            tables.add(layout.staffSeatOf(code).orElseThrow().table());
            if (layout.consultDeskOf(code).isPresent()) {
                desks++;
            }
        }
        long besideFree = layout.getResourceCodes().stream().map(code -> layout.positionOf(code).orElseThrow())
                .filter(spot -> layout.areaAt(spot).orElseThrow().zone() == ZoneType.HOSPITALIZATION)
                .map(spot -> new Position(spot.col() + 1, spot.row()))
                .filter(side -> layout.areaAt(side).map(area -> area.zone() == ZoneType.HOSPITALIZATION).orElse(false)
                        && layout.resourceAt(side).isEmpty())
                .count();
        long expected = beds + 3 * besideFree + 2 * icu + 3 * operating + layout.getWaitingSpots().size()
                + 3L * layout.getTriageSpots().size() + layout.getBloodSpots().size() + layout.getMorgueSpots().size()
                + desks + tables.size() + layout.getStaffCodes().size() + furniture.propCount();

        List<IsoItem> items = furniture.items();

        assertEquals(expected, items.size());
    }

    @Test
    void theRoomsHavePropsAgainstTheirBackWallAndTheCorridorsNone() {
        assertTrue(furniture.propCount() > layout.getAreas().size() / 2);
        IsoFurniture dark = new IsoFurniture(layout, projection, new Renderer2D(), IsoTheme.DARK);
        assertEquals(furniture.propCount(), dark.propCount(), "el tema no cambia los muebles");
    }

    @Test
    void knowsWhereAPatientLiesDownOrSits() {
        assertEquals(IsoFurniture.Kind.BED, furniture.kindAt(layout.positionOf("H1-C1").orElseThrow()).orElseThrow());
        assertEquals(IsoFurniture.Kind.BED,
                furniture.kindAt(layout.positionOf("UCI-C1").orElseThrow()).orElseThrow());
        assertEquals(IsoFurniture.Kind.OPERATING_TABLE,
                furniture.kindAt(layout.positionOf("Q1").orElseThrow()).orElseThrow());
        assertEquals(IsoFurniture.Kind.SLAB, furniture.kindAt(layout.getMorgueSpots().get(0)).orElseThrow());
        assertEquals(IsoFurniture.Kind.CHAIR, furniture.kindAt(layout.getWaitingSpots().get(0)).orElseThrow());
        assertTrue(furniture.kindAt(layout.getEntrance()).isEmpty());
        assertTrue(furniture.kindAt(layout.getTriageSpots().get(0)).isEmpty());
    }

    @Test
    void theBedLeavesItsLeftSideFreeForTheStaff() {
        double nurse = 0.5 - 16.0 / Renderer2D.CELL_W;
        assertTrue(nurse + IsoBrush.BODY_RADIUS <= IsoFurniture.BED_LEFT,
                "el enfermero junto a la cama no se mete en ella");
        assertTrue(IsoFurniture.BED_RIGHT < 1 && IsoFurniture.BED_TOP > 0 && IsoFurniture.BED_BOTTOM < 1,
                "la cama cabe en su casilla");
    }

    @Test
    void theBloodBankShowsItsFridges() {
        BufferedImage image = new BufferedImage(projection.size().width, projection.size().height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        furniture.items().forEach(item -> item.paint(graphics));

        Position fridge = layout.getBloodSpots().get(0);
        Point2D side = projection.at(fridge.col() + 0.5, fridge.row() + 0.22 + 0.56, 0.5);
        Color seen = new Color(image.getRGB((int) side.getX(), (int) side.getY()));
        assertNotEquals(Color.BLACK, seen);
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(null, projection, flat));
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(layout, null, flat));
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(layout, projection, null));
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(layout, projection, flat, null));
    }
}
