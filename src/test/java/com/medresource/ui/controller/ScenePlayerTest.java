package com.medresource.ui.controller;

import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.EventType;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Patient;
import com.medresource.model.PatientProfile;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationEvent;
import com.medresource.model.TreatmentType;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.ui.render.Motion;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.Renderer2D;
import com.medresource.ui.render.SceneState;
import com.medresource.ui.render.SceneState.Phase;
import com.medresource.ui.render.StaffMarker;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reproductor: simulaciones reales (sin azar, con semilla) reproducidas de forma continua.
 */
class ScenePlayerTest {

    private static final HospitalConfig TINY = new HospitalConfig(1, 1, 1, 1, 1, 0, 1);

    private final Renderer2D renderer = new Renderer2D();

    private SimulationEngine engineOf(HospitalConfig hospital, Scenario scenario, int patients) {
        return new SimulationEngine(new CaseGenerator().generate(new CaseConfig(1L, scenario, patients, 480)),
                new HospitalFactory().create(hospital), new ClinicalPriorityStrategy());
    }

    private ScenePlayer playerOf(HospitalConfig hospital, Scenario scenario, int patients) {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(hospital));
        return new ScenePlayer(engineOf(hospital, scenario, patients), layout, renderer);
    }

    private ScenePlayer standard() {
        return playerOf(HospitalConfig.standard(), Scenario.NORMAL, 30);
    }

    /** Avanza la escena diez segundos simulados; devuelve {@code false} si ya se había visto todo. */
    private static boolean tick(ScenePlayer player) {
        if (player.isFinished()) {
            return false;
        }
        player.advanceTo(player.getTime() + 10);
        return true;
    }

    /** Avanza hasta el momento en que aparece el primer paciente. */
    private static double untilFirstArrival(ScenePlayer player) {
        double first = player.nextEventTime().orElseThrow();
        player.advanceTo(first);
        while (player.markers().isEmpty()) {
            first = player.nextEventTime().orElseThrow();
            player.advanceTo(first);
        }
        return first;
    }

    @Test
    void rejectsMissingData() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = engineOf(HospitalConfig.standard(), Scenario.NORMAL, 5);

        assertThrows(IllegalArgumentException.class, () -> new ScenePlayer(null, layout, renderer));
        assertThrows(IllegalArgumentException.class, () -> new ScenePlayer(engine, null, renderer));
        assertThrows(IllegalArgumentException.class, () -> new ScenePlayer(engine, layout, null));
    }

    @Test
    void doesNotTakeASimulationThatAlreadyStarted() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = engineOf(HospitalConfig.standard(), Scenario.NORMAL, 5);
        engine.step();

        assertThrows(IllegalStateException.class, () -> new ScenePlayer(engine, layout, renderer));
    }

    @Test
    void startsEmptyAtSecondZero() {
        ScenePlayer player = standard();

        assertEquals(0.0, player.getTime(), 0.0);
        assertEquals(0, player.getMinute());
        assertTrue(player.markers().isEmpty());
        assertEquals(0, player.overflow());
        assertFalse(player.isFinished());
        assertNotNull(player.getState());
    }

    @Test
    void advancesToTheMomentItIsGiven() {
        ScenePlayer player = standard();

        player.advanceTo(75.5);

        assertEquals(75.5, player.getTime(), 0.0);
        assertEquals(1, player.getMinute());
        assertThrows(IllegalArgumentException.class, () -> player.advanceTo(70));
    }

    @Test
    void eachEventIsSeenInItsOwnMomentOfTheMinute() {
        ScenePlayer player = playerOf(HospitalConfig.standard(), Scenario.CATASTROPHE, 100);
        Map<Integer, Set<Double>> arrivalMoments = new HashMap<>();
        while (player.getMinute() < 15) {
            double next = player.nextEventTime().orElseThrow();
            player.advanceTo(next);
            for (SimulationEvent event : player.drainReleasedEvents()) {
                assertEquals(player.getMinute(), event.minute(), event.toString());
                if (event.type() == EventType.ARRIVAL) {
                    arrivalMoments.computeIfAbsent(event.minute(), minute -> new HashSet<>()).add(next);
                }
            }
        }

        assertTrue(arrivalMoments.values().stream().anyMatch(moments -> moments.size() >= 3),
                "en una catástrofe varios pacientes llegan en el mismo minuto, cada uno en su segundo");
    }

    @Test
    void nextEventTimeIsEmptyOnlyWhenEverythingWasSeen() {
        ScenePlayer player = standard();
        OptionalDouble next = player.nextEventTime();
        while (next.isPresent()) {
            assertTrue(next.getAsDouble() >= player.getTime());
            player.advanceTo(next.getAsDouble());
            next = player.nextEventTime();
        }

        assertTrue(player.isFinished());
    }

    @Test
    void aNewPatientAppearsAtTheEntranceAndReachesItsPlaceOneTripAfterItsLastChange() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.NORMAL, 30), layout, renderer);
        double lastChange = untilFirstArrival(player);
        Point2D entrance = renderer.centerOf(layout.getEntrance());
        PatientMarker first = player.markers().get(0);
        String code = first.code();

        assertEquals(entrance.getX(), first.x(), 0.0);
        assertEquals(entrance.getY(), first.y(), 0.0);
        assertTrue(player.hasArrived(code));

        OptionalDouble next = player.nextEventTime();
        while (next.isPresent() && next.getAsDouble() <= lastChange + Motion.TRAVEL_SECONDS) {
            player.advanceTo(next.getAsDouble());
            if (player.drainReleasedEvents().stream().anyMatch(event -> code.equals(event.patientCode()))) {
                lastChange = next.getAsDouble();
            }
            next = player.nextEventTime();
        }
        player.advanceTo(lastChange + Motion.TRAVEL_SECONDS);

        PatientMarker now = player.markers().stream().filter(marker -> marker.code().equals(code))
                .findFirst().orElseThrow();
        Point2D place = renderer.centerOf(player.getState().positionOf(code).orElseThrow());
        assertEquals(place.getX(), now.x(), 1e-9);
        assertEquals(place.getY(), now.y(), 1e-9);
    }

    @Test
    void aPatientIsHalfwayWhenHalfTheTripTimeHasPassed() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.NORMAL, 30), layout, renderer);
        double arrival = untilFirstArrival(player);
        PatientMarker first = player.markers().get(0);
        Point2D entrance = renderer.centerOf(layout.getEntrance());
        Point2D place = renderer.centerOf(player.getState().positionOf(first.code()).orElseThrow());
        OptionalDouble next = player.nextEventTime();
        double halfway = arrival + Motion.TRAVEL_SECONDS / 2;
        assertTrue(next.isEmpty() || next.getAsDouble() > halfway, "nadie más se mueve antes de la mitad");

        player.advanceTo(halfway);

        assertEquals((entrance.getX() + place.getX()) / 2, player.markers().get(0).x(), 1e-9);
        assertEquals((entrance.getY() + place.getY()) / 2, player.markers().get(0).y(), 1e-9);
    }

    @Test
    void theEventsAreDeliveredOnlyOnce() {
        ScenePlayer player = standard();
        player.advanceTo(3_600);

        assertFalse(player.drainReleasedEvents().isEmpty());
        assertTrue(player.drainReleasedEvents().isEmpty());
    }

    @Test
    void theMarkersCarryTheDataOfEachPatient() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = engineOf(HospitalConfig.standard(), Scenario.NORMAL, 30);
        Map<String, Patient> byCode = new HashMap<>();
        engine.getPatients().forEach(patient -> byCode.put(patient.getCode(), patient));
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);

        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                Patient patient = byCode.get(marker.code());
                assertEquals(patient.getTriageLevel(), marker.triage());
                assertEquals(patient.getSex(), marker.sex());
                assertEquals(patient.isMinor(), marker.minor());
            }
        }
    }

    @Test
    void everyMarkerStaysInsideTheWorld() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        Dimension world = renderer.worldSize(layout);
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.CATASTROPHE, 150), layout, renderer);

        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                assertTrue(marker.x() >= 0 && marker.x() <= world.width, marker.code());
                assertTrue(marker.y() >= 0 && marker.y() <= world.height, marker.code());
            }
        }
    }

    @Test
    void whenItFinishesNoOneIsWaitingOrBeingTreated() {
        for (Scenario scenario : Scenario.values()) {
            ScenePlayer player = playerOf(TINY, scenario, 80);
            while (tick(player)) {
                // reproduce hasta el final
            }
            Set<Phase> allowed = EnumSet.of(Phase.IN_MORGUE, Phase.LEAVING, Phase.DECEASED);

            for (SceneState.Placement placement : player.getState().placements()) {
                assertTrue(allowed.contains(placement.phase()), scenario + " " + placement);
            }
            assertEquals(0, player.getState().count(Phase.QUEUED));
            assertEquals(0, player.getState().count(Phase.TREATING));
        }
    }

    @Test
    void aCrowdedHospitalLeavesPeopleOutOfTheWaitingRoomAndHasDeceasedPatients() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 200);
        int mostWaiting = 0;
        boolean sawDeceased = false;
        while (tick(player)) {
            mostWaiting = Math.max(mostWaiting, player.overflow());
            sawDeceased |= player.markers().stream().anyMatch(PatientMarker::deceased);
        }

        assertTrue(mostWaiting > 0);
        assertTrue(sawDeceased);
    }

    @Test
    void theDeceasedMarkersAreTheOnesThatDiedOrAreInTheMorgue() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 120);
        while (tick(player)) {
            long expected = player.getState().count(Phase.DECEASED) + player.getState().count(Phase.IN_MORGUE);

            assertEquals(expected, player.markers().stream().filter(PatientMarker::deceased).count());
        }
    }

    @Test
    void thereIsOneMarkerForEachPatientInTheScene() {
        ScenePlayer player = playerOf(TINY, Scenario.HIGH_DEMAND, 100);
        while (tick(player)) {
            assertEquals(player.getState().placements().size(), player.markers().size());
        }
    }

    @Test
    void thePatientsWhoLeaveDisappearWhenTheyReachTheExit() {
        ScenePlayer player = playerOf(HospitalConfig.standard(), Scenario.NORMAL, 20);
        Map<String, Double> leftAt = new HashMap<>();
        while (!player.isFinished()) {
            double next = player.nextEventTime().orElse(player.getTime() + 1);
            player.advanceTo(Math.max(next, player.getTime() + 1));
            for (SceneState.Placement placement : player.getState().placements()) {
                if (placement.phase() == Phase.LEAVING) {
                    leftAt.putIfAbsent(placement.patientCode(), player.getTime());
                }
            }
            for (Map.Entry<String, Double> left : leftAt.entrySet()) {
                if (player.getTime() >= left.getValue() + Motion.TRAVEL_SECONDS) {
                    assertTrue(player.getState().phaseOf(left.getKey()).isEmpty(), left.getKey());
                }
            }
        }

        assertFalse(leftAt.isEmpty());
    }

    @Test
    void theSameCaseIsAlwaysPlayedTheSame() {
        ScenePlayer first = playerOf(TINY, Scenario.CATASTROPHE, 60);
        ScenePlayer second = playerOf(TINY, Scenario.CATASTROPHE, 60);

        while (tick(first)) {
            assertTrue(tick(second));

            assertEquals(first.markers(), second.markers());
            assertEquals(first.overflow(), second.overflow());
        }
        assertFalse(tick(second));
    }

    // ---------- personal ----------

    /** Caso de un solo paciente con la enfermedad indicada, que llega en el minuto 0. */
    private static SimulationCase onePatientWith(int diseaseId) {
        Disease disease = DiseaseCatalog.findById(diseaseId).orElseThrow();
        PatientProfile profile = new PatientProfile(1, 30, Sex.MALE, BloodType.O_POSITIVE, disease,
                disease.getSymptoms(), 0, 80.0, false);
        return new SimulationCase(new CaseConfig(1L, Scenario.NORMAL, 1, 60), List.of(profile));
    }

    /** Caso de un solo paciente con dengue, que se hospitaliza con un médico de medicina interna y un enfermero. */
    private static SimulationCase oneDenguePatient() {
        return onePatientWith(2);
    }

    private Point2D staffAt(ScenePlayer player, String code) {
        StaffMarker marker = player.staffMarkers().stream()
                .filter(member -> member.code().equals(code))
                .findFirst()
                .orElseThrow();
        return new Point2D.Double(marker.x(), marker.y());
    }

    @Test
    void theStaffStartsSeatedInTheStaffRoom() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.NORMAL, 5), layout,
                renderer);

        player.advanceTo(0);

        List<StaffMarker> staff = player.staffMarkers();
        assertEquals(40, staff.size());
        for (StaffMarker member : staff) {
            Point2D seat = renderer.seatOf(layout.staffSeatOf(member.code()).orElseThrow());
            assertEquals(seat, new Point2D.Double(member.x(), member.y()), member.code());
            assertTrue(member.seated(), member.code());
        }
    }

    @Test
    void theDoctorsComeFirstAndOnlyTheyHaveASpecialty() {
        List<StaffMarker> staff = standard().staffMarkers();

        assertEquals("D01", staff.get(0).code());
        assertTrue(staff.get(0).isDoctor());
        assertEquals("E01", staff.get(16).code());
        assertFalse(staff.get(16).isDoctor());
    }

    @Test
    void theDoctorAndTheNurseStandNextToTheirPatientAndThenGoBackToTheirSeats() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(oneDenguePatient(),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        while (player.getState().phaseOf("P001").orElse(null) != Phase.TREATING) {
            tick(player);
        }
        String doctor = engine.getHospital().getDoctors().stream()
                .filter(member -> !member.isAvailable()).findFirst().orElseThrow().getCode();
        String nurse = engine.getHospital().getNurses().stream()
                .filter(member -> !member.isAvailable()).findFirst().orElseThrow().getCode();

        player.advanceTo(player.getTime() + Motion.TRAVEL_SECONDS + 1);

        Point2D bed = renderer.centerOf(player.getState().positionOf("P001").orElseThrow());
        assertEquals(new Point2D.Double(bed.getX() - 32, bed.getY()), staffAt(player, doctor));
        assertEquals(new Point2D.Double(bed.getX() - 16, bed.getY()), staffAt(player, nurse));
        assertFalse(player.staffMarkers().stream().filter(member -> member.code().equals(doctor))
                .findFirst().orElseThrow().seated());

        player.advanceTo(player.getTime() + (TreatmentType.INITIAL_CARE_MINUTES + 1) * 60.0 + Motion.TRAVEL_SECONDS);

        assertEquals(renderer.seatOf(layout.staffSeatOf(doctor).orElseThrow()), staffAt(player, doctor));
        assertEquals(renderer.seatOf(layout.staffSeatOf(nurse).orElseThrow()), staffAt(player, nurse));
        assertEquals(Phase.TREATING, player.getState().phaseOf("P001").orElseThrow());
    }

    @Test
    void theDoctorAttendsAConsultationAtTheirDeskInTheConsultingRooms() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(onePatientWith(1),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        while (player.getState().phaseOf("P001").orElse(null) != Phase.TREATING) {
            tick(player);
        }
        String doctor = engine.getHospital().getDoctors().stream()
                .filter(member -> !member.isAvailable()).findFirst().orElseThrow().getCode();

        player.advanceTo(player.getTime() + Motion.TRAVEL_SECONDS + 1);

        Point2D desk = renderer.centerOf(layout.consultDeskOf(doctor).orElseThrow());
        assertEquals(layout.consultDeskOf(doctor).orElseThrow(), player.getState().positionOf("P001").orElseThrow());
        assertEquals(new Point2D.Double(desk.getX() - 32, desk.getY()), staffAt(player, doctor));
    }
}