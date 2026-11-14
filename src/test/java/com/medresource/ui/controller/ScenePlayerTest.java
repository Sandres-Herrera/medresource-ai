package com.medresource.ui.controller;

import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.EventType;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Door;
import com.medresource.model.Patient;
import com.medresource.model.PatientProfile;
import com.medresource.model.Position;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationEvent;
import com.medresource.model.StaffMember;
import com.medresource.model.TreatmentType;
import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.ui.render.IdleMotion;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PorterMarker;
import com.medresource.ui.render.Renderer2D;
import com.medresource.ui.render.SceneState;
import com.medresource.ui.render.SceneState.Phase;
import com.medresource.ui.render.StaffMarker;
import com.medresource.ui.render.Walker;
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
    /**
     * Segundos simulados que bastan para que cualquiera llegue a su destino en el hospital estándar: el
     * recorrido más largo, de la sala del personal a la hospitalización, mide unos 50 m y dura unos 20 s.
     */
    private static final double LONGEST_WALK = 60.0;

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
    void aNewPatientAppearsAtTheEntranceAndReachesItsPlaceAfterItsLastChange() {
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
        while (next.isPresent() && next.getAsDouble() <= lastChange + LONGEST_WALK) {
            player.advanceTo(next.getAsDouble());
            if (player.drainReleasedEvents().stream().anyMatch(event -> code.equals(event.patientCode()))) {
                lastChange = next.getAsDouble();
            }
            next = player.nextEventTime();
        }
        player.advanceTo(lastChange + LONGEST_WALK);

        PatientMarker now = player.markers().stream().filter(marker -> marker.code().equals(code))
                .findFirst().orElseThrow();
        Point2D place = renderer.centerOf(player.getState().positionOf(code).orElseThrow());
        assertEquals(place.getX(), now.x(), 1e-9);
        assertEquals(place.getY(), now.y(), 1e-9);
    }

    @Test
    void aPatientWalksFromTheEntranceAlongItsRouteAtWalkingSpeed() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.NORMAL, 30), layout, renderer);
        double arrival = untilFirstArrival(player);
        PatientMarker first = player.markers().get(0);
        Position entrance = layout.getEntrance();
        Position place = player.getState().positionOf(first.code()).orElseThrow();
        Walker expected = new Walker(entrance, renderer.centerOf(entrance));
        expected.walk(new WalkPlanner(layout, renderer).legs(entrance, renderer.centerOf(entrance), place,
                renderer.centerOf(place)), arrival);
        double halfway = arrival + expected.tripSeconds() / 2;
        OptionalDouble next = player.nextEventTime();
        assertTrue(next.isEmpty() || next.getAsDouble() > halfway, "nadie más se mueve antes de la mitad");

        player.advanceTo(halfway);
        expected.update(halfway);

        assertTrue(expected.isMoving());
        assertEquals(expected.getCurrent().getX(), player.markers().get(0).x(), 1e-9);
        assertEquals(expected.getCurrent().getY(), player.markers().get(0).y(), 1e-9);
    }

    @Test
    void nobodyWalksThroughAWall() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        ScenePlayer player = new ScenePlayer(engineOf(HospitalConfig.standard(), Scenario.HIGH_DEMAND, 40), layout,
                renderer);
        Map<String, Position> lastCell = new HashMap<>();
        for (double at = 0; at <= 45 * 60; at += 0.2) {
            player.advanceTo(at);
            Map<String, Point2D> people = new HashMap<>();
            player.markers().forEach(marker -> people.put(marker.code(), new Point2D.Double(marker.x(), marker.y())));
            player.staffMarkers().forEach(member -> people.put(member.code(), new Point2D.Double(member.x(),
                    member.y())));
            for (Map.Entry<String, Point2D> person : people.entrySet()) {
                Position cell = new Position((int) Math.floor(person.getValue().getX() / Renderer2D.CELL_W),
                        (int) Math.floor(person.getValue().getY() / Renderer2D.CELL_H));
                Position before = lastCell.put(person.getKey(), cell);
                assertTrue(before == null || before.equals(cell) || layout.canStep(before, cell),
                        person.getKey() + " pasó de " + before + " a " + cell + " en el segundo " + at);
            }
        }
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
    void aCrowdedHospitalLeavesCriticalPatientsWaitingAtTheEntranceAndHasDeceasedPatients() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 200);
        int mostAtTheEntrance = 0;
        boolean sawDeceased = false;
        while (tick(player)) {
            mostAtTheEntrance = Math.max(mostAtTheEntrance, player.getState().count(Phase.DIRECT_PASS));
            sawDeceased |= player.markers().stream().anyMatch(PatientMarker::deceased);
        }

        assertTrue(mostAtTheEntrance > 0);
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
                if (player.getTime() >= left.getValue() + LONGEST_WALK) {
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
    void theDoctorAndTheNurseStandNextToTheirPatientAndThenTheDoctorGoesBackToHisSeat() {
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

        player.advanceTo(player.getTime() + LONGEST_WALK + 1);

        Point2D bed = renderer.centerOf(player.getState().positionOf("P001").orElseThrow());
        assertEquals(new Point2D.Double(bed.getX() - 32, bed.getY()), staffAt(player, doctor));
        assertEquals(new Point2D.Double(bed.getX() - 16, bed.getY()), staffAt(player, nurse));
        assertFalse(player.staffMarkers().stream().filter(member -> member.code().equals(doctor))
                .findFirst().orElseThrow().seated());

        player.advanceTo(player.getTime() + (TreatmentType.INITIAL_CARE_MINUTES + 1) * 60.0 + LONGEST_WALK);

        assertEquals(renderer.seatOf(layout.staffSeatOf(doctor).orElseThrow()), staffAt(player, doctor));
        assertTrue(engine.getHospital().getNurses().stream().allMatch(StaffMember::isAvailable),
                "el enfermero quedó libre para el motor; desde ahí solo hace rondas");
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

        player.advanceTo(player.getTime() + LONGEST_WALK + 1);

        Point2D desk = renderer.centerOf(layout.consultDeskOf(doctor).orElseThrow());
        assertEquals(layout.consultDeskOf(doctor).orElseThrow(), player.getState().positionOf("P001").orElseThrow());
        assertEquals(new Point2D.Double(desk.getX() - 32, desk.getY()), staffAt(player, doctor));
    }

    // ---------- camilleros ----------

    @Test
    void nobodyIsFetchedBeforeAPatientWaits() {
        ScenePlayer player = standard();

        player.advanceTo(30);

        assertTrue(player.porterMarkers().isEmpty());
    }

    @Test
    void aPorterFetchesTheWaitingPatientWhileTheStaffWaitsForHimInHisCarePlace() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = engineOf(HospitalConfig.standard(), Scenario.HIGH_DEMAND, 60);
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        PorterMarker porter = null;
        while (porter == null) {
            assertTrue(player.getTime() < 3 * 3600, "ningún paciente salió de la sala de espera");
            player.advanceTo(player.getTime() + 1);
            porter = player.porterMarkers().stream().findFirst().orElse(null);
        }
        PorterMarker first = porter;
        String code = first.patientCode();
        Position chair = cellOf(markerOf(player, code));
        Set<Point2D> doors = new HashSet<>();
        for (Door door : layout.getDoors()) {
            doors.add(renderer.centerOf(door.outside()));
        }

        assertEquals(ZoneType.WAITING_ROOM, layout.areaAt(chair).orElseThrow().zone(),
                "el paciente sigue sentado cuando sale el camillero");
        assertTrue(EnumSet.of(Phase.TREATING, Phase.FIRST_AID)
                .contains(player.getState().phaseOf(code).orElseThrow()));
        assertTrue(doors.stream().anyMatch(door -> door.distance(first.x(), first.y()) < 2 * Walker.WALKING_SPEED
                * Renderer2D.CELL_H), "el camillero sale de una puerta");
        Position place = player.getState().positionOf(code).orElseThrow();
        Point2D center = renderer.centerOf(place);
        Point2D chairCenter = renderer.centerOf(chair);
        boolean someoneWaited = false;
        while (cellOf(markerOf(player, code)).equals(chair)) {
            for (StaffMarker member : player.staffMarkers()) {
                assertFalse(member.y() == chairCenter.getY() && Math.abs(member.x() - chairCenter.getX()) <= 32,
                        member.code() + " no va a buscar al paciente a la sala de espera");
                someoneWaited |= member.y() == center.getY() && Math.abs(member.x() - center.getX()) <= 32;
            }
            player.advanceTo(player.getTime() + 1);
        }
        assertTrue(someoneWaited, "el personal lo esperaba en su lugar de atención");

        player.advanceTo(player.getTime() + 2 * LONGEST_WALK);

        assertTrue(player.porterMarkers().stream().noneMatch(each -> each.patientCode().equals(code)),
                "el camillero lo dejó y se fue");
    }

    private static PatientMarker markerOf(ScenePlayer player, String code) {
        return player.markers().stream().filter(marker -> marker.code().equals(code)).findFirst().orElseThrow();
    }

    private static Position cellOf(PatientMarker marker) {
        return new Position((int) Math.floor(marker.x() / Renderer2D.CELL_W),
                (int) Math.floor(marker.y() / Renderer2D.CELL_H));
    }

    // ---------- pacientes críticos y primeros auxilios ----------

    @Test
    void aCriticalPatientNeverQueuesInTheTriageNorSitsInTheWaitingRoom() {
        ScenePlayer player = playerOf(HospitalConfig.standard(), Scenario.CATASTROPHE, 120);
        Set<Phase> notForCritical = EnumSet.of(Phase.TRIAGE_QUEUE, Phase.IN_TRIAGE, Phase.QUEUED);
        int critical = 0;
        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                if (marker.triage() == TriageLevel.I) {
                    critical++;
                    Phase phase = player.getState().phaseOf(marker.code()).orElseThrow();
                    assertFalse(notForCritical.contains(phase), marker.code() + " " + phase);
                }
            }
        }
        assertTrue(critical > 0);
    }

    /** Dos infartos, el segundo cinco minutos después, cuando el único médico ya atiende al primero. */
    private static SimulationCase twoHeartAttacks() {
        Disease heartAttack = DiseaseCatalog.findById(9).orElseThrow();
        return new SimulationCase(new CaseConfig(1L, Scenario.NORMAL, 2, 60), List.of(
                new PatientProfile(1, 70, Sex.MALE, BloodType.O_POSITIVE, heartAttack, heartAttack.getSymptoms(), 0,
                        60.0, false),
                new PatientProfile(2, 70, Sex.FEMALE, BloodType.O_POSITIVE, heartAttack, heartAttack.getSymptoms(), 5,
                        60.0, false)));
    }

    @Test
    void aFreeNurseGoesToTheBedWhereTheCriticalPatientWaitsForHisDoctor() {
        HospitalConfig config = new HospitalConfig(2, 2, 1, 1, 3, 1, 2);
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(config));
        SimulationEngine engine = new SimulationEngine(twoHeartAttacks(),
                new HospitalFactory().create(config), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        while (player.getState().phaseOf("P002").orElse(null) != Phase.FIRST_AID) {
            assertTrue(tick(player), "P002 nunca recibió primeros auxilios en una cama");
        }
        Patient critical = engine.getPatients().get(1);
        String nurse = engine.getHospital().getNurses().stream()
                .filter(member -> member.getAssignedPatient() == critical).findFirst().orElseThrow().getCode();

        player.advanceTo(player.getTime() + LONGEST_WALK);

        Position bed = player.getState().positionOf("P002").orElseThrow();
        assertEquals(ZoneType.ICU, layout.areaAt(bed).orElseThrow().zone());
        Point2D center = renderer.centerOf(bed);
        assertEquals(new Point2D.Double(center.getX() - 32, center.getY()), staffAt(player, nurse));
    }

    // ---------- itinerario completo ----------

    @Test
    void everyPatientPassesThroughEveryPlaceTheSceneGivesHim() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(
                new CaseGenerator().generate(new CaseConfig(1L, Scenario.HIGH_DEMAND, 60, 60)),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        Set<Phase> lines = EnumSet.of(Phase.TRIAGE_QUEUE, Phase.QUEUED);
        Map<String, Map<Position, Double>> given = new HashMap<>();
        Map<String, Set<Position>> visited = new HashMap<>();
        double end = 75 * 60;
        for (double at = 0; at <= end; at += 0.2) {
            player.advanceTo(at);
            for (SceneState.Placement placement : player.getState().placements()) {
                if (!lines.contains(placement.phase())) {
                    given.computeIfAbsent(placement.patientCode(), code -> new HashMap<>())
                            .putIfAbsent(placement.position(), at);
                }
            }
            for (PatientMarker marker : player.markers()) {
                Position cell = new Position((int) Math.floor(marker.x() / Renderer2D.CELL_W),
                        (int) Math.floor(marker.y() / Renderer2D.CELL_H));
                visited.computeIfAbsent(marker.code(), code -> new HashSet<>()).add(cell);
            }
        }
        int checked = 0;
        for (Map.Entry<String, Map<Position, Double>> patient : given.entrySet()) {
            for (Map.Entry<Position, Double> place : patient.getValue().entrySet()) {
                if (place.getValue() < end - 5 * 60) {
                    checked++;
                    assertTrue(visited.get(patient.getKey()).contains(place.getKey()),
                            patient.getKey() + " no pasó por " + place.getKey());
                }
            }
        }
        assertTrue(checked > 50, "solo se revisaron " + checked + " lugares");
    }

    // ---------- salud y avance del tratamiento ----------

    @Test
    void aPatientInTreatmentShowsHowMuchOfItIsDoneAndTheOthersShowNoProgress() {
        ScenePlayer player = playerOf(HospitalConfig.standard(), Scenario.HIGH_DEMAND, 60);
        Map<String, Double> lastProgress = new HashMap<>();
        int treating = 0;
        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                Phase phase = player.getState().phaseOf(marker.code()).orElseThrow();
                if (phase != Phase.TREATING) {
                    assertFalse(marker.inTreatment(), marker.code() + " " + phase);
                    continue;
                }
                treating++;
                assertTrue(marker.inTreatment(), marker.code());
                Double before = lastProgress.put(marker.code(), marker.progress());
                assertTrue(before == null || marker.progress() >= before, marker.code() + " retrocedió");
            }
        }

        assertTrue(treating > 0);
        assertTrue(lastProgress.values().stream().anyMatch(progress -> progress > 0.9),
                "algún tratamiento llegó casi al final");
    }

    @Test
    void theHealthOfAWaitingPatientDropsLittleByLittle() {
        ScenePlayer player = playerOf(TINY, Scenario.HIGH_DEMAND, 60);
        Map<String, Double> lastHealth = new HashMap<>();
        int seen = 0;
        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                assertTrue(marker.health() >= 0 && marker.health() <= Patient.MAX_HEALTH, marker.code());
                Phase phase = player.getState().phaseOf(marker.code()).orElseThrow();
                Double before = phase == Phase.QUEUED ? lastHealth.put(marker.code(), marker.health())
                        : lastHealth.remove(marker.code());
                if (phase == Phase.QUEUED && before != null) {
                    seen++;
                    assertTrue(marker.health() <= before, marker.code() + " mejoró sin atención");
                    assertTrue(before - marker.health() < 2.0, marker.code() + " perdió salud de golpe");
                }
            }
        }

        assertTrue(seen > 100, "solo se vieron " + seen + " momentos de espera");
    }

    @Test
    void aDeceasedPatientShowsNoHealthNorProgress() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 120);
        int deceased = 0;
        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                if (marker.deceased()) {
                    deceased++;
                    assertEquals(0.0, marker.health(), 0.0);
                    assertFalse(marker.inTreatment());
                }
            }
        }

        assertTrue(deceased > 0);
    }

    // ---------- camilla ----------

    @Test
    void theDeceasedLieOnAStretcherThatCarriesThemToTheMorgue() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 120);
        boolean carried = false;
        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                if (marker.deceased()) {
                    assertTrue(marker.onStretcher(), marker.code());
                    carried |= marker.isCarried()
                            && player.getState().phaseOf(marker.code()).orElseThrow() == Phase.IN_MORGUE;
                }
            }
        }

        assertTrue(carried, "ningún fallecido se vio camino a la morgue");
    }

    @Test
    void aPatientWithAFractureGoesOnAStretcherUntilHeLeaves() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(onePatientWith(5),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        boolean carried = false;
        boolean leftOnFoot = false;
        for (double at = 0; !player.isFinished(); at += 2) {
            player.advanceTo(at);
            for (PatientMarker marker : player.markers()) {
                Phase phase = player.getState().phaseOf(marker.code()).orElseThrow();
                if (phase == Phase.LEAVING) {
                    leftOnFoot |= !marker.onStretcher();
                } else {
                    assertTrue(marker.onStretcher(), phase.name());
                    carried |= marker.isCarried();
                }
            }
        }

        assertTrue(carried);
        assertTrue(leftOnFoot);
    }

    @Test
    void aPatientWhoCanWalkNeverUsesAStretcher() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(onePatientWith(1),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        int seen = 0;
        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                seen++;
                assertFalse(marker.onStretcher());
            }
        }

        assertTrue(seen > 0);
    }

    // ---------- rondas de enfermería ----------

    @Test
    void theFreeNursesVisitAHospitalizedPatientOnTheirRoundsAndGoBackToRest() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(oneDenguePatient(),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        while (player.getState().phaseOf("P001").orElse(null) != Phase.TREATING) {
            tick(player);
        }
        double afterAdmission = player.getTime() + (TreatmentType.INITIAL_CARE_MINUTES + 1) * 60.0;
        player.advanceTo(afterAdmission);
        Point2D bed = renderer.centerOf(player.getState().positionOf("P001").orElseThrow());
        Point2D bedside = new Point2D.Double(bed.getX() - 32, bed.getY());
        Set<String> visitors = new HashSet<>();
        boolean allResting = false;
        for (double at = afterAdmission; at < afterAdmission + 4 * NursingRounds.VISIT_MINUTES * 60.0; at += 5) {
            player.advanceTo(at);
            boolean someoneVisits = false;
            for (StaffMarker member : player.staffMarkers()) {
                if (!member.isDoctor() && new Point2D.Double(member.x(), member.y()).equals(bedside)) {
                    visitors.add(member.code());
                    someoneVisits = true;
                }
            }
            allResting |= !someoneVisits && player.staffMarkers().stream()
                    .filter(member -> !member.isDoctor()).allMatch(StaffMarker::seated);
        }

        assertEquals(2, visitors.size(), "dos enfermeros se turnan la ronda: " + visitors);
        assertFalse(allResting, "con un paciente en cama siempre hay alguien de ronda o caminando");
    }

    @Test
    void withoutHospitalizedPatientsTheNursesKeepResting() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(onePatientWith(1),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);

        while (tick(player)) {
            for (StaffMarker member : player.staffMarkers()) {
                if (!member.isDoctor()) {
                    assertTrue(member.seated(), member.code() + " en el segundo " + player.getTime());
                }
            }
        }
    }

    // ---------- movimiento de reposo ----------

    @Test
    void theRestingPeopleBreatheAndTheDeceasedAndTheWalkingDoNot() {
        ScenePlayer player = playerOf(TINY, Scenario.CATASTROPHE, 120);
        boolean patientBreathes = false;
        boolean staffBreathes = false;
        while (tick(player)) {
            for (PatientMarker marker : player.markers()) {
                if (marker.deceased() || marker.moving()) {
                    assertEquals(0.0, marker.bob(), 0.0, marker.code());
                } else {
                    assertEquals(IdleMotion.bob(marker.code(), player.getTime(), false), marker.bob(), 1e-9);
                    patientBreathes |= marker.bob() != 0.0;
                }
            }
            for (StaffMarker member : player.staffMarkers()) {
                assertTrue(Math.abs(member.bob()) <= IdleMotion.AMPLITUDE + 1e-9, member.code());
                staffBreathes |= member.seated() && member.bob() != 0.0;
            }
        }

        assertTrue(patientBreathes);
        assertTrue(staffBreathes);
    }

    // ---------- ficha del paciente ----------

    @Test
    void thereIsNoCardOfAPatientWhoIsNotInTheScene() {
        ScenePlayer player = standard();

        assertTrue(player.card("P001").isEmpty());
        assertTrue(player.card("X999").isEmpty());
        assertTrue(player.card(null).isEmpty());
    }

    @Test
    void theCardSaysWhoThePatientIsWhereHeIsAndWhoAttendsHim() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        SimulationEngine engine = new SimulationEngine(oneDenguePatient(),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        while (player.getState().phaseOf("P001").orElse(null) != Phase.TREATING) {
            tick(player);
        }
        player.advanceTo(player.getTime() + LONGEST_WALK + 1);

        PatientCard card = player.card("P001").orElseThrow();

        assertEquals("P001", card.code());
        assertEquals("30 años · Masculino", card.ageAndSex());
        assertEquals(DiseaseCatalog.findById(2).orElseThrow().getName(), card.disease());
        assertEquals(Phase.TREATING.getDisplayName(), card.activity());
        String bed = layout.resourceAt(player.getState().positionOf("P001").orElseThrow()).orElseThrow();
        assertEquals("Hospitalización · " + bed, card.place());
        assertFalse(card.walking());
        assertTrue(card.inTreatment());
        assertEquals(2, card.caregivers().size());
        assertTrue(card.caregivers().get(0).endsWith("· Medicina interna"), card.caregivers().get(0));
        assertTrue(card.caregivers().get(1).endsWith("· Enfermería"), card.caregivers().get(1));
    }

    @Test
    void whileHeWalksTheCardSaysWhereHeIsGoing() {
        ScenePlayer player = standard();
        untilFirstArrival(player);
        String code = player.markers().get(0).code();

        PatientCard card = player.card(code).orElseThrow();

        assertTrue(card.walking());
        assertEquals("Triage", card.place());
        assertEquals(0, card.minutesInHospital());
        assertTrue(card.caregivers().isEmpty());
    }
}
