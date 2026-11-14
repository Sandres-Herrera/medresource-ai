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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                .filter(spot -> {
                    ZoneType zone = layout.areaAt(spot).orElseThrow().zone();
                    Position side = new Position(spot.col() + 1, spot.row());
                    return (zone == ZoneType.HOSPITALIZATION || zone == ZoneType.ICU)
                            && layout.areaAt(side).map(area -> area.zone() == zone).orElse(false)
                            && layout.resourceAt(side).isEmpty();
                })
                .count();
        long discharge = 4L * layout.areasOf(ZoneType.DISCHARGE).size();
        long expected = beds + 3 * besideFree + 2 * icu + 3 * operating + layout.getWaitingSpots().size()
                + 3L * layout.getTriageSpots().size() + layout.getBloodSpots().size() + layout.getMorgueSpots().size()
                + desks + tables.size() + layout.getStaffCodes().size() + furniture.propCount() + discharge;

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
    void theIcuMonitorStandsBehindTheHeadOfItsBedAwayFromTheWalls() {
        assertTrue(IsoFurniture.MONITOR_X > IsoFurniture.BED_LEFT && IsoFurniture.MONITOR_X < IsoFurniture.BED_RIGHT);
        assertTrue(IsoFurniture.MONITOR_Y < IsoFurniture.BED_TOP);
        Area icu = layout.areasOf(ZoneType.ICU).get(0);
        for (String code : layout.getResourceCodes()) {
            Position bed = layout.positionOf(code).orElseThrow();
            if (icu.covers(bed)) {
                Position behind = new Position(bed.col(), bed.row() - 1);
                assertTrue(icu.covers(behind), "detrás hay piso de la UCI, no un muro");
                assertTrue(layout.resourceAt(behind).isEmpty(), "y no hay otra cama");
            }
        }
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
    void theHeartBeatsFasterAsTheHealthGoesDown() {
        assertEquals(IsoFurniture.CALM_BPM, IsoFurniture.beatsPerMinute(100), 1e-9);
        assertEquals(IsoFurniture.GRAVE_BPM, IsoFurniture.beatsPerMinute(0), 1e-9);
        assertTrue(IsoFurniture.beatsPerMinute(30) > IsoFurniture.beatsPerMinute(80));
    }

    @Test
    void theTraceHasItsPeakInTheMiddleOfTheBeatAndRestsAtTheEnd() {
        assertEquals(1.0, IsoFurniture.heartTrace(0.34), 1e-9);
        assertEquals(0.0, IsoFurniture.heartTrace(0.9), 0.0);
        assertEquals(IsoFurniture.heartTrace(0.34), IsoFurniture.heartTrace(3.34), 1e-9);
    }

    @Test
    void theAlarmBlinksForAGravePatientAndStaysOnForADeadOne() {
        Position grave = layout.positionOf("UCI-C1").orElseThrow();
        Position dead = layout.positionOf("UCI-C2").orElseThrow();
        Position fine = layout.positionOf("UCI-C3").orElseThrow();
        Map<Position, Double> health = Map.of(grave, 10.0, dead, 0.0, fine, 80.0);

        furniture.showVitals(0.1, health);
        assertTrue(furniture.alarmOn(grave));
        assertTrue(furniture.alarmOn(dead));
        assertFalse(furniture.alarmOn(fine));

        furniture.showVitals(0.4, health);
        assertFalse(furniture.alarmOn(grave), "a mitad del parpadeo se apaga");
        assertTrue(furniture.alarmOn(dead));
        assertTrue(IsoFurniture.isGrave(Palette.POOR_HEALTH - 0.1));
        assertFalse(IsoFurniture.isGrave(Palette.POOR_HEALTH));
        assertThrows(IllegalArgumentException.class, () -> furniture.showVitals(Double.NaN, health));
        assertThrows(IllegalArgumentException.class, () -> furniture.showVitals(0, null));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(null, projection, flat));
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(layout, null, flat));
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(layout, projection, null));
        assertThrows(IllegalArgumentException.class, () -> new IsoFurniture(layout, projection, flat, null));
    }
}
