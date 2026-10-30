package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del plano del hospital con un plano pequeño armado a mano.
 *
 * <p>El plano es de 6 columnas por 7 filas: arriba el triage, la sala de espera y dos camas; abajo el banco de
 * sangre y la morgue.</p>
 */
class HospitalLayoutTest {

    private final Area triage = new Area(ZoneType.TRIAGE, "Triage", 0, 0, 2, 3);
    private final Area waiting = new Area(ZoneType.WAITING_ROOM, "Sala de espera", 2, 0, 2, 3);
    private final Area ward = new Area(ZoneType.HOSPITALIZATION, "Hospitalización", 4, 0, 2, 3);
    private final Area blood = new Area(ZoneType.BLOOD_BANK, "Banco de sangre", 0, 3, 3, 2);
    private final Area morgue = new Area(ZoneType.MORGUE, "Morgue", 3, 3, 3, 2);

    private List<Area> areas() {
        return new ArrayList<>(List.of(triage, waiting, ward, blood, morgue));
    }

    private Map<String, Position> spots() {
        Map<String, Position> spots = new HashMap<>();
        spots.put("B1", ward.slot(0));
        spots.put("B2", ward.slot(1));
        return spots;
    }

    private List<Position> triageSpots() {
        return new ArrayList<>(List.of(triage.slot(1), triage.slot(3)));
    }

    private List<Position> waitingSpots() {
        return new ArrayList<>(List.of(waiting.slot(0), waiting.slot(1), waiting.slot(2), waiting.slot(3)));
    }

    private List<Position> bloodSpots() {
        return new ArrayList<>(List.of(blood.slot(0), blood.slot(1), blood.slot(2)));
    }

    private List<Position> morgueSpots() {
        return new ArrayList<>(List.of(morgue.slot(0), morgue.slot(1)));
    }

    private HospitalLayout layout() {
        return new HospitalLayout(6, 7, areas(), spots(), triage.slot(0), triageSpots(), waitingSpots(),
                bloodSpots(), morgueSpots());
    }

    private HospitalLayout layoutWith(int rows, List<Area> areas, Map<String, Position> spots, Position entrance,
                                      List<Position> triageSpots, List<Position> waitingSpots,
                                      List<Position> bloodSpots, List<Position> morgueSpots) {
        return new HospitalLayout(6, rows, areas, spots, entrance, triageSpots, waitingSpots, bloodSpots, morgueSpots);
    }

    // ---------- datos ----------

    @Test
    void keepsItsData() {
        HospitalLayout layout = layout();

        assertEquals(6, layout.getCols());
        assertEquals(7, layout.getRows());
        assertEquals(List.of(triage, waiting, ward, blood, morgue), layout.getAreas());
        assertEquals(triage.slot(0), layout.getEntrance());
        assertEquals(triageSpots(), layout.getTriageSpots());
        assertEquals(waitingSpots(), layout.getWaitingSpots());
        assertEquals(bloodSpots(), layout.getBloodSpots());
        assertEquals(morgueSpots(), layout.getMorgueSpots());
    }

    @Test
    void findsTheAreaOfASlot() {
        HospitalLayout layout = layout();

        assertEquals(ward, layout.areaAt(new Position(5, 2)).orElseThrow());
        assertEquals(morgue, layout.areaAt(new Position(4, 4)).orElseThrow());
    }

    @Test
    void theTitleRowAndTheOutsideBelongToNoArea() {
        HospitalLayout layout = layout();

        assertTrue(layout.areaAt(new Position(4, 0)).isEmpty());
        assertTrue(layout.areaAt(new Position(9, 9)).isEmpty());
    }

    @Test
    void findsTheAreasOfAZoneInPlanOrder() {
        List<Area> withTwoWards = areas();
        withTwoWards.add(new Area(ZoneType.HOSPITALIZATION, "Otra", 0, 5, 2, 2));
        HospitalLayout layout = layoutWith(7, withTwoWards, spots(), triage.slot(0), triageSpots(), waitingSpots(),
                bloodSpots(), morgueSpots());

        assertEquals(List.of(ward, withTwoWards.get(5)), layout.areasOf(ZoneType.HOSPITALIZATION));
        assertEquals(List.of(), layout.areasOf(ZoneType.ICU));
    }

    @Test
    void findsTheSlotOfAResourceByItsCode() {
        HospitalLayout layout = layout();

        assertEquals(ward.slot(1), layout.positionOf("B2").orElseThrow());
        assertTrue(layout.positionOf("B9").isEmpty());
        assertTrue(layout.positionOf(null).isEmpty());
    }

    @Test
    void listsTheCodesOfTheResourcesOnThePlan() {
        assertEquals(Set.of("B1", "B2"), layout().getResourceCodes());
    }

    @Test
    void changingTheCodesReturnedDoesNotChangeThePlan() {
        HospitalLayout layout = layout();

        layout.getResourceCodes().clear();

        assertEquals(2, layout.getResourceCodes().size());
    }

    @Test
    void theListsItReturnsCannotBeModified() {
        HospitalLayout layout = layout();

        assertThrows(UnsupportedOperationException.class, () -> layout.getAreas().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.getTriageSpots().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.getWaitingSpots().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.getBloodSpots().clear());
        assertThrows(UnsupportedOperationException.class, () -> layout.getMorgueSpots().clear());
    }

    @Test
    void doesNotChangeWhenTheListsItWasBuiltWithChange() {
        List<Area> areas = areas();
        HospitalLayout layout = layoutWith(7, areas, spots(), triage.slot(0), triageSpots(), waitingSpots(),
                bloodSpots(), morgueSpots());

        areas.clear();

        assertEquals(5, layout.getAreas().size());
    }

    @Test
    void describesItselfWithItsSizeAndTheNumberOfAreas() {
        String text = layout().toString();

        assertTrue(text.contains("6 columnas"));
        assertTrue(text.contains("7 filas"));
        assertTrue(text.contains("5 áreas"));
    }

    // ---------- validación ----------

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, null, spots(), triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), null, triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), null,
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                null, waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), null, bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), waitingSpots(), null, morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), null));
    }

    @Test
    void rejectsAPlanWithoutColumnsOrRows() {
        assertThrows(IllegalArgumentException.class, () -> new HospitalLayout(0, 7, areas(), spots(),
                triage.slot(0), triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(0, areas(), spots(), triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsAnAreaOutsideThePlan() {
        assertThrows(IllegalArgumentException.class, () -> new HospitalLayout(5, 7, areas(), spots(),
                triage.slot(0), triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(4, areas(), spots(), triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void acceptsAnAreaThatEndsExactlyAtTheEdgeOfThePlan() {
        assertEquals(5, layoutWith(5, areas(), spots(), triage.slot(0), triageSpots(), waitingSpots(),
                bloodSpots(), morgueSpots()).getRows());
    }

    @Test
    void rejectsTwoAreasThatOverlap() {
        List<Area> overlapping = areas();
        overlapping.add(new Area(ZoneType.ICU, "UCI", 1, 2, 2, 2));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, overlapping, spots(), triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsAnAreaThatOverlapsOnlyTheLastOne() {
        List<Area> overlapping = areas();
        overlapping.add(new Area(ZoneType.ICU, "UCI", 4, 4, 2, 2));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, overlapping, spots(), triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsAnEntranceOutsideTheTriage() {
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), waiting.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), new Position(0, 0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsATriageOrAWaitingRoomWithoutPlaces() {
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                List.of(), waitingSpots(), bloodSpots(), morgueSpots()));
        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), List.of(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsATriageStationThatIsNotInTheTriage() {
        List<Position> stations = triageSpots();
        stations.add(ward.slot(2));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                stations, waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsASeatThatIsNotInTheWaitingRoom() {
        List<Position> seats = waitingSpots();
        seats.add(ward.slot(2));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), seats, bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsABloodPlaceThatIsNotInTheBloodBank() {
        List<Position> places = bloodSpots();
        places.add(ward.slot(2));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), waitingSpots(), places, morgueSpots()));
    }

    @Test
    void rejectsAMorguePlaceThatIsNotInTheMorgue() {
        List<Position> places = morgueSpots();
        places.add(ward.slot(2));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), places));
    }

    @Test
    void acceptsAPlanWithoutBloodOrMorguePlaces() {
        HospitalLayout layout = layoutWith(7, areas(), spots(), triage.slot(0), triageSpots(), waitingSpots(),
                List.of(), List.of());

        assertTrue(layout.getBloodSpots().isEmpty());
        assertTrue(layout.getMorgueSpots().isEmpty());
    }

    @Test
    void rejectsAResourceOutsideTheAreas() {
        Map<String, Position> outside = spots();
        outside.put("B3", new Position(5, 6));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), outside, triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsAResourceOnTheTitleRowOfAnArea() {
        Map<String, Position> onTitle = spots();
        onTitle.put("B3", new Position(4, 0));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), onTitle, triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsTwoResourcesInTheSameSlot() {
        Map<String, Position> repeated = spots();
        repeated.put("B3", ward.slot(0));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), repeated, triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsAResourceOnAMorguePlace() {
        Map<String, Position> repeated = spots();
        repeated.put("B3", morgue.slot(0));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), repeated, triage.slot(0),
                triageSpots(), waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsATriageStationOnTheEntrance() {
        List<Position> stations = triageSpots();
        stations.add(triage.slot(0));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                stations, waitingSpots(), bloodSpots(), morgueSpots()));
    }

    @Test
    void rejectsTwoBloodPlacesInTheSameSlot() {
        List<Position> places = bloodSpots();
        places.add(blood.slot(0));

        assertThrows(IllegalArgumentException.class, () -> layoutWith(7, areas(), spots(), triage.slot(0),
                triageSpots(), waitingSpots(), places, morgueSpots()));
    }
}
