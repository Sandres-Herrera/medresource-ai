package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del plano del hospital con un plano pequeño armado a mano (6 columnas por 3 filas):
 *
 * <pre>
 * E W W T M .
 * H H . . M .
 * . . . . . .
 * </pre>
 */
class HospitalLayoutTest {

    private static final Position ENTRANCE = new Position(0, 0);
    private static final Position TRIAGE = new Position(3, 0);
    private static final List<Position> WAITING = List.of(new Position(1, 0), new Position(2, 0));
    private static final List<Position> MORGUE = List.of(new Position(4, 0), new Position(4, 1));

    private static ZoneType[][] zones() {
        ZoneType[][] zones = new ZoneType[3][6];
        for (ZoneType[] row : zones) {
            Arrays.fill(row, ZoneType.CORRIDOR);
        }
        zones[0][0] = ZoneType.ENTRANCE;
        zones[0][1] = ZoneType.WAITING_ROOM;
        zones[0][2] = ZoneType.WAITING_ROOM;
        zones[0][3] = ZoneType.TRIAGE;
        zones[0][4] = ZoneType.MORGUE;
        zones[1][4] = ZoneType.MORGUE;
        zones[1][0] = ZoneType.HOSPITALIZATION;
        zones[1][1] = ZoneType.HOSPITALIZATION;
        return zones;
    }

    private static Map<String, Position> spots() {
        Map<String, Position> spots = new HashMap<>();
        spots.put("H1-C1", new Position(0, 1));
        spots.put("H1-C2", new Position(1, 1));
        return spots;
    }

    private static HospitalLayout layout() {
        return new HospitalLayout(zones(), spots(), ENTRANCE, TRIAGE, WAITING, MORGUE);
    }

    @Test
    void knowsItsSize() {
        HospitalLayout layout = layout();

        assertEquals(6, layout.getCols());
        assertEquals(3, layout.getRows());
    }

    @Test
    void knowsTheZoneOfEachSquare() {
        HospitalLayout layout = layout();

        assertEquals(ZoneType.ENTRANCE, layout.zoneAt(new Position(0, 0)));
        assertEquals(ZoneType.HOSPITALIZATION, layout.zoneAt(new Position(1, 1)));
        assertEquals(ZoneType.CORRIDOR, layout.zoneAt(new Position(5, 2)));
    }

    @Test
    void doesNotAnswerForSquaresOutsideThePlan() {
        HospitalLayout layout = layout();

        assertThrows(IllegalArgumentException.class, () -> layout.zoneAt(new Position(6, 0)));
        assertThrows(IllegalArgumentException.class, () -> layout.zoneAt(new Position(0, 3)));
        assertThrows(IllegalArgumentException.class, () -> layout.zoneAt(null));
    }

    @Test
    void knowsWhichSquaresAreInsideThePlan() {
        HospitalLayout layout = layout();

        assertTrue(layout.contains(new Position(5, 2)));
        assertFalse(layout.contains(new Position(6, 2)));
        assertFalse(layout.contains(new Position(5, 3)));
        assertFalse(layout.contains(null));
    }

    @Test
    void findsAResourceByItsCode() {
        HospitalLayout layout = layout();

        assertEquals(Optional.of(new Position(1, 1)), layout.positionOf("H1-C2"));
        assertEquals(Optional.empty(), layout.positionOf("H9-C1"));
        assertEquals(Optional.empty(), layout.positionOf(null));
        assertEquals(Set.of("H1-C1", "H1-C2"), layout.getResourceCodes());
    }

    @Test
    void countsTheSquaresOfEachZone() {
        HospitalLayout layout = layout();

        assertEquals(2, layout.countOf(ZoneType.MORGUE));
        assertEquals(2, layout.countOf(ZoneType.WAITING_ROOM));
        assertEquals(1, layout.countOf(ZoneType.TRIAGE));
        assertEquals(0, layout.countOf(ZoneType.ICU));
        assertEquals(10, layout.countOf(ZoneType.CORRIDOR));
    }

    @Test
    void keepsTheSpecialPlaces() {
        HospitalLayout layout = layout();

        assertEquals(ENTRANCE, layout.getEntrance());
        assertEquals(TRIAGE, layout.getTriagePoint());
        assertEquals(WAITING, layout.getWaitingSpots());
        assertEquals(MORGUE, layout.getMorgueSpots());
    }

    @Test
    void changingTheDataItWasBuiltWithDoesNotChangeThePlan() {
        ZoneType[][] zones = zones();
        Map<String, Position> spots = spots();
        HospitalLayout layout = new HospitalLayout(zones, spots, ENTRANCE, TRIAGE, WAITING, MORGUE);

        zones[2][5] = ZoneType.ICU;
        spots.put("X", new Position(5, 2));

        assertEquals(ZoneType.CORRIDOR, layout.zoneAt(new Position(5, 2)));
        assertEquals(Optional.empty(), layout.positionOf("X"));
    }

    @Test
    void doesNotAllowChangingTheLists() {
        HospitalLayout layout = layout();

        assertThrows(UnsupportedOperationException.class, () -> layout.getWaitingSpots().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.getMorgueSpots().clear());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(null, spots(), ENTRANCE, TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), null, ENTRANCE, TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), null, TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, null, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, TRIAGE, null, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, TRIAGE, WAITING, null));
    }

    @Test
    void rejectsAMalformedGrid() {
        ZoneType[][] ragged = zones();
        ragged[1] = Arrays.copyOf(ragged[1], ragged[1].length + 1);
        ragged[1][ragged[1].length - 1] = ZoneType.CORRIDOR;
        ZoneType[][] withHole = zones();
        withHole[2][2] = null;

        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(new ZoneType[0][0], spots(), ENTRANCE, TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(new ZoneType[2][0], spots(), ENTRANCE, TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(ragged, spots(), ENTRANCE, TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(withHole, spots(), ENTRANCE, TRIAGE, WAITING, MORGUE));
    }

    @Test
    void rejectsSpecialPlacesInTheWrongZone() {
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), new Position(5, 2), TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, new Position(5, 2), WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, TRIAGE, List.of(new Position(5, 2)), MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, TRIAGE, WAITING, List.of(new Position(5, 2))));
    }

    @Test
    void rejectsSpecialPlacesOutsideThePlan() {
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), new Position(9, 9), TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, new Position(9, 9), WAITING, MORGUE));
    }

    @Test
    void needsAtLeastOneWaitingSpot() {
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots(), ENTRANCE, TRIAGE, List.of(), MORGUE));
    }

    @Test
    void acceptsAPlanWithoutMorgueSpots() {
        HospitalLayout layout = new HospitalLayout(zones(), spots(), ENTRANCE, TRIAGE, WAITING, List.of());

        assertTrue(layout.getMorgueSpots().isEmpty());
    }

    @Test
    void rejectsAResourceOutsideThePlan() {
        Map<String, Position> spots = spots();
        spots.put("Q1", new Position(6, 0));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots, ENTRANCE, TRIAGE, WAITING, MORGUE));

        assertTrue(error.getMessage().contains("Q1"));
    }

    @Test
    void rejectsTwoResourcesInTheSameSquare() {
        Map<String, Position> spots = spots();
        spots.put("H1-C3", new Position(0, 1));
        Map<String, Position> overMorgue = spots();
        overMorgue.put("H1-C3", MORGUE.get(0));

        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), spots, ENTRANCE, TRIAGE, WAITING, MORGUE));
        assertThrows(IllegalArgumentException.class,
                () -> new HospitalLayout(zones(), overMorgue, ENTRANCE, TRIAGE, WAITING, MORGUE));
    }

    @Test
    void describesItsSize() {
        assertEquals("Plano de 6 columnas por 3 filas", layout().toString());
    }
}