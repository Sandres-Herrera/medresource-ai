package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.EventType;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.SimulationEvent;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.render.SceneState.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la escena: dónde está cada paciente según los eventos de la simulación.
 */
class SceneStateTest {

    private final HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final SceneState scene = new SceneState(layout);

    private static SimulationEvent event(EventType type, String patient, String resource) {
        return new SimulationEvent(0, type, patient, resource, "evento de prueba");
    }

    private void arrive(String... patients) {
        for (String patient : patients) {
            scene.apply(event(EventType.ARRIVAL, patient, null));
        }
    }

    private Position positionOf(String patient) {
        return scene.positionOf(patient).orElseThrow();
    }

    private Position spot(String resourceCode) {
        return layout.positionOf(resourceCode).orElseThrow();
    }

    // ---------- datos ----------

    @Test
    void doesNotStartWithoutAPlanOrWithoutAnExit() {
        assertThrows(IllegalArgumentException.class, () -> new SceneState(null));
        List<Area> noExit = layout.getAreas().stream().filter(area -> area.zone() != ZoneType.DISCHARGE).toList();
        java.util.Map<String, Position> spots = new java.util.HashMap<>();
        layout.getResourceCodes().forEach(code -> spots.put(code, layout.positionOf(code).orElseThrow()));
        HospitalLayout withoutExit = new HospitalLayout(layout.getCols(), layout.getRows(), noExit, spots,
                layout.getEntrance(), layout.getTriageSpots(), layout.getWaitingSpots(), layout.getBloodSpots(),
                layout.getMorgueSpots());

        assertThrows(IllegalArgumentException.class, () -> new SceneState(withoutExit));
    }

    @Test
    void rejectsMissingEvents() {
        assertThrows(IllegalArgumentException.class, () -> scene.apply(null));
        assertThrows(IllegalArgumentException.class, () -> scene.applyAll(null));
    }

    @Test
    void startsEmpty() {
        assertTrue(scene.placements().isEmpty());
        assertEquals(0, scene.overflow());
        assertTrue(scene.phaseOf("P001").isEmpty());
        assertTrue(scene.positionOf("P001").isEmpty());
        assertTrue(scene.phaseOf(null).isEmpty());
    }

    // ---------- llegada y espera ----------

    @Test
    void aPatientThatArrivesWaitsInTheFirstSeat() {
        arrive("P001");

        assertEquals(Phase.QUEUED, scene.phaseOf("P001").orElseThrow());
        assertEquals(layout.getWaitingSpots().get(0), positionOf("P001"));
    }

    @Test
    void thePatientsWaitInTheSeatsInOrderOfArrival() {
        arrive("P001", "P002", "P003");

        assertEquals(layout.getWaitingSpots().get(0), positionOf("P001"));
        assertEquals(layout.getWaitingSpots().get(1), positionOf("P002"));
        assertEquals(layout.getWaitingSpots().get(2), positionOf("P003"));
    }

    @Test
    void whenSomeoneLeavesTheQueueTheOthersMoveUpOneSeat() {
        arrive("P001", "P002", "P003");

        scene.apply(event(EventType.TREATMENT_STARTED, "P001", "H1-C1"));

        assertEquals(layout.getWaitingSpots().get(0), positionOf("P002"));
        assertEquals(layout.getWaitingSpots().get(1), positionOf("P003"));
    }

    @Test
    void thosePatientsWhoDoNotFitShareTheLastSeatAndAreCountedAsOverflow() {
        int seats = layout.getWaitingSpots().size();
        for (int number = 1; number <= seats + 3; number++) {
            arrive("P" + number);
        }

        assertEquals(3, scene.overflow());
        assertEquals(seats + 3, scene.count(Phase.QUEUED));
        assertEquals(layout.getWaitingSpots().get(seats - 1), positionOf("P" + (seats + 3)));
        assertEquals(layout.getWaitingSpots().get(seats - 1), positionOf("P" + seats));
        assertEquals(layout.getWaitingSpots().get(seats - 2), positionOf("P" + (seats - 1)));
    }

    @Test
    void thereIsNoOverflowWhenAllFitInTheSeats() {
        int seats = layout.getWaitingSpots().size();
        for (int number = 1; number <= seats; number++) {
            arrive("P" + number);
        }

        assertEquals(0, scene.overflow());
    }

    @Test
    void aPatientCannotArriveTwice() {
        arrive("P001");

        assertThrows(IllegalStateException.class, () -> arrive("P001"));
    }

    @Test
    void theEventsThatMoveNoOneAreIgnored() {
        arrive("P001");
        List<EventType> quiet = List.of(EventType.TRIAGE_ASSIGNED, EventType.DIAGNOSIS, EventType.EMERGENCY_ALERT,
                EventType.REEVALUATION, EventType.TREATMENT_FINISHED);

        for (EventType type : quiet) {
            scene.apply(event(type, "P001", null));
            scene.apply(event(type, "P999", null));
        }

        assertEquals(Phase.QUEUED, scene.phaseOf("P001").orElseThrow());
        assertEquals(1, scene.placements().size());
    }

    // ---------- tratamiento ----------

    @Test
    void aPatientWhoStartsTreatmentGoesToTheBedOfTheEvent() {
        arrive("P001");

        scene.apply(event(EventType.TREATMENT_STARTED, "P001", "H1-C2"));

        assertEquals(Phase.TREATING, scene.phaseOf("P001").orElseThrow());
        assertEquals(spot("H1-C2"), positionOf("P001"));
        assertEquals(0, scene.count(Phase.QUEUED));
    }

    @Test
    void aPatientAdmittedToTheIcuGoesToTheIcuBed() {
        arrive("P001");

        scene.apply(event(EventType.ICU_ADMISSION, "P001", "UCI-C1"));
        scene.apply(event(EventType.TREATMENT_STARTED, "P001", "UCI-C1"));

        assertEquals(Phase.TREATING, scene.phaseOf("P001").orElseThrow());
        assertEquals(spot("UCI-C1"), positionOf("P001"));
    }

    @Test
    void aPatientOperatedOnGoesToTheOperatingRoom() {
        arrive("P001");

        scene.apply(event(EventType.TREATMENT_STARTED, "P001", "Q2"));

        assertEquals(spot("Q2"), positionOf("P001"));
    }

    @Test
    void aPatientSeenOnlyByADoctorGoesToTheDoctor() {
        arrive("P001");

        scene.apply(event(EventType.TREATMENT_STARTED, "P001", "D03"));

        assertEquals(spot("D03"), positionOf("P001"));
    }

    @Test
    void doesNotStartTreatmentInAResourceTheLayoutDoesNotHave() {
        arrive("P001");

        assertThrows(IllegalStateException.class,
                () -> scene.apply(event(EventType.TREATMENT_STARTED, "P001", "H99-C9")));
        assertThrows(IllegalStateException.class,
                () -> scene.apply(event(EventType.TREATMENT_STARTED, "P001", null)));
    }

    @Test
    void doesNotChangeAPatientWhoHasNotArrived() {
        for (EventType type : List.of(EventType.TREATMENT_STARTED, EventType.ICU_ADMISSION, EventType.DEATH,
                EventType.MORGUE_ADMISSION, EventType.MORGUE_RELEASE, EventType.DISCHARGE,
                EventType.STABILIZED_WITHOUT_CURE, EventType.REFERRAL, EventType.UNATTENDED)) {
            assertThrows(IllegalStateException.class, () -> scene.apply(event(type, "P404", "H1-C1")));
        }
    }

    // ---------- salida ----------

    @Test
    void aDischargedPatientLeavesThroughTheExitArea() {
        arrive("P001");
        scene.apply(event(EventType.TREATMENT_STARTED, "P001", "H1-C1"));

        scene.apply(event(EventType.DISCHARGE, "P001", "H1-C1"));

        assertEquals(Phase.LEAVING, scene.phaseOf("P001").orElseThrow());
        assertEquals(ZoneType.DISCHARGE, layout.areaAt(positionOf("P001")).orElseThrow().zone());
    }

    @Test
    void everyWayOfLeavingGoesToTheExitArea() {
        for (EventType type : List.of(EventType.DISCHARGE, EventType.STABILIZED_WITHOUT_CURE, EventType.REFERRAL,
                EventType.UNATTENDED)) {
            SceneState fresh = new SceneState(layout);
            fresh.apply(event(EventType.ARRIVAL, "P001", null));

            fresh.apply(event(type, "P001", null));

            assertEquals(Phase.LEAVING, fresh.phaseOf("P001").orElseThrow(), type.name());
            assertEquals(ZoneType.DISCHARGE,
                    layout.areaAt(fresh.positionOf("P001").orElseThrow()).orElseThrow().zone(), type.name());
        }
    }

    @Test
    void thosePatientsWhoLeaveTogetherUseDifferentSpotsOfTheExit() {
        arrive("P001", "P002", "P003");

        scene.apply(event(EventType.DISCHARGE, "P001", null));
        scene.apply(event(EventType.REFERRAL, "P002", null));
        scene.apply(event(EventType.UNATTENDED, "P003", null));

        Set<Position> spots = new HashSet<>(List.of(positionOf("P001"), positionOf("P002"), positionOf("P003")));
        assertEquals(3, spots.size());
    }

    @Test
    void theExitSpotsStartOverWhenTheyAreAllUsed() {
        Area exit = layout.areasOf(ZoneType.DISCHARGE).get(0);
        for (int number = 0; number <= exit.slotCount(); number++) {
            arrive("P" + number);
            scene.apply(event(EventType.DISCHARGE, "P" + number, null));
        }

        assertEquals(exit.slot(0), positionOf("P0"));
        assertEquals(exit.slot(0), positionOf("P" + exit.slotCount()));
        assertEquals(exit.slot(exit.slotCount() - 1), positionOf("P" + (exit.slotCount() - 1)));
    }

    @Test
    void thePatientsWhoLeftDisappearOnlyWhenTheDeparturesAreCleared() {
        arrive("P001", "P002");
        scene.apply(event(EventType.DISCHARGE, "P001", null));

        assertEquals(2, scene.placements().size());

        scene.clearDeparted();

        assertEquals(1, scene.placements().size());
        assertTrue(scene.phaseOf("P001").isEmpty());
        assertEquals(Phase.QUEUED, scene.phaseOf("P002").orElseThrow());
    }

    @Test
    void aPatientWhoLeavesNoLongerTakesASeat() {
        arrive("P001", "P002");

        scene.apply(event(EventType.REFERRAL, "P001", null));

        assertEquals(layout.getWaitingSpots().get(0), positionOf("P002"));
        assertEquals(1, scene.count(Phase.QUEUED));
    }

    // ---------- muerte y morgue ----------

    @Test
    void aPatientWhoDiesInABedStaysThere() {
        arrive("P001");
        scene.apply(event(EventType.TREATMENT_STARTED, "P001", "H1-C1"));

        scene.apply(event(EventType.DEATH, "P001", "H1-C1"));

        assertEquals(Phase.DECEASED, scene.phaseOf("P001").orElseThrow());
        assertEquals(spot("H1-C1"), positionOf("P001"));
    }

    @Test
    void aPatientWhoDiesWaitingStaysInTheSeatTheyHad() {
        arrive("P001", "P002", "P003");

        scene.apply(event(EventType.DEATH, "P002", null));

        assertEquals(Phase.DECEASED, scene.phaseOf("P002").orElseThrow());
        assertEquals(layout.getWaitingSpots().get(1), positionOf("P002"));
        assertEquals(layout.getWaitingSpots().get(1), positionOf("P003"));
    }

    @Test
    void aBodyThatEntersTheMorgueTakesTheFirstFreePlace() {
        arrive("P001", "P002");
        scene.apply(event(EventType.DEATH, "P001", null));
        scene.apply(event(EventType.DEATH, "P002", null));

        scene.apply(event(EventType.MORGUE_ADMISSION, "P001", null));
        scene.apply(event(EventType.MORGUE_ADMISSION, "P002", null));

        assertEquals(Phase.IN_MORGUE, scene.phaseOf("P001").orElseThrow());
        assertEquals(layout.getMorgueSpots().get(0), positionOf("P001"));
        assertEquals(layout.getMorgueSpots().get(1), positionOf("P002"));
    }

    @Test
    void aBodyThatLeavesTheMorgueDisappearsAndItsPlaceIsFreeAgain() {
        arrive("P001", "P002", "P003");
        for (String patient : List.of("P001", "P002")) {
            scene.apply(event(EventType.DEATH, patient, null));
            scene.apply(event(EventType.MORGUE_ADMISSION, patient, null));
        }

        scene.apply(event(EventType.MORGUE_RELEASE, "P001", null));
        scene.apply(event(EventType.DEATH, "P003", null));
        scene.apply(event(EventType.MORGUE_ADMISSION, "P003", null));

        assertTrue(scene.phaseOf("P001").isEmpty());
        assertEquals(layout.getMorgueSpots().get(0), positionOf("P003"));
        assertEquals(layout.getMorgueSpots().get(1), positionOf("P002"));
    }

    @Test
    void aFullMorgueNeverTakesAPlaceThatDoesNotExist() {
        int places = layout.getMorgueSpots().size();
        for (int number = 0; number <= places; number++) {
            arrive("P" + number);
            scene.apply(event(EventType.DEATH, "P" + number, null));
            scene.apply(event(EventType.MORGUE_ADMISSION, "P" + number, null));
        }

        assertEquals(layout.getMorgueSpots().get(places - 1), positionOf("P" + places));
    }

    // ---------- varios eventos ----------

    @Test
    void appliesAListOfEventsInOrder() {
        scene.applyAll(List.of(event(EventType.ARRIVAL, "P001", null),
                event(EventType.TRIAGE_ASSIGNED, "P001", null),
                event(EventType.TREATMENT_STARTED, "P001", "H1-C1"),
                event(EventType.DISCHARGE, "P001", "H1-C1")));

        assertEquals(Phase.LEAVING, scene.phaseOf("P001").orElseThrow());
    }

    @Test
    void thePlacementsAreInOrderOfArrivalAndCannotBeModified() {
        arrive("P003", "P001", "P002");
        List<SceneState.Placement> placements = scene.placements();
        List<String> codes = new ArrayList<>();
        placements.forEach(placement -> codes.add(placement.patientCode()));

        assertEquals(List.of("P003", "P001", "P002"), codes);
        assertThrows(UnsupportedOperationException.class, placements::clear);
    }
}