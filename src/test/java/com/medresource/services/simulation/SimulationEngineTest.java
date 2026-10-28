package com.medresource.services.simulation;

import com.medresource.model.BloodBank;
import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Doctor;
import com.medresource.model.EventType;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Morgue;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Patient;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientProfile;
import com.medresource.model.PatientStatus;
import com.medresource.model.Room;
import com.medresource.model.RoomType;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationEvent;
import com.medresource.model.SimulationResult;
import com.medresource.model.Specialty;
import com.medresource.model.Symptom;
import com.medresource.model.TreatmentType;
import com.medresource.model.TriageLevel;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.Assignment;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.WaitingPatient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del motor de simulación.
 */
class SimulationEngineTest {

    private static final int FLU = 1;
    private static final int DENGUE = 2;
    private static final int PNEUMONIA = 4;
    private static final int OPEN_FRACTURE = 7;
    private static final int APPENDICITIS = 8;
    private static final int HEART_ATTACK = 9;
    private static final int ADVANCED_CANCER = 11;

    // ---------- armado de casos y hospitales ----------

    private static Disease disease(int id) {
        return DiseaseCatalog.findById(id).orElseThrow();
    }

    private static PatientProfile profile(int id, int arrival, Disease disease, int age, double health,
                                          boolean irrecoverable) {
        return new PatientProfile(id, age, Sex.MALE, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                arrival, health, irrecoverable);
    }

    private static PatientProfile profile(int id, int arrival, int diseaseId, int age, double health) {
        return profile(id, arrival, disease(diseaseId), age, health, false);
    }

    private static SimulationCase caseOf(int duration, PatientProfile... profiles) {
        List<PatientProfile> list = List.of(profiles);
        return new SimulationCase(new CaseConfig(1L, Scenario.NORMAL, list.size(), duration), list);
    }

    /** Hospital de 4 camas, una cama de UCI y un quirófano, con un médico por cada especialidad indicada. */
    private static Hospital hospital(int morgueCapacity, int bloodPerType, int nurses, Specialty... specialties) {
        List<Doctor> doctors = new ArrayList<>();
        for (int i = 0; i < specialties.length; i++) {
            doctors.add(new Doctor(i + 1, specialties[i], 40, Sex.FEMALE));
        }
        List<Nurse> nurseList = new ArrayList<>();
        for (int i = 1; i <= nurses; i++) {
            nurseList.add(new Nurse(i, 30, Sex.MALE));
        }
        Map<BloodType, Integer> units = new EnumMap<>(BloodType.class);
        for (BloodType type : BloodType.values()) {
            units.put(type, bloodPerType);
        }
        return new Hospital(List.of(new Room("H1", RoomType.HOSPITALIZATION, 4)),
                new Room("UCI", RoomType.ICU, 1), List.of(new OperatingRoom(1)), doctors, nurseList,
                new BloodBank(units), new Morgue(morgueCapacity));
    }

    private static Hospital hospital(Specialty... specialties) {
        return hospital(3, 5, 4, specialties);
    }

    private static SimulationResult run(SimulationCase simulationCase, Hospital hospital) {
        return new SimulationEngine(simulationCase, hospital, new FifoStrategy()).run();
    }

    private static List<EventType> typesOf(SimulationResult result, String patientCode) {
        return result.events().stream()
                .filter(event -> event.patientCode().equals(patientCode))
                .map(SimulationEvent::type)
                .toList();
    }

    private static SimulationEvent eventOf(SimulationResult result, String patientCode, EventType type) {
        return result.events().stream()
                .filter(event -> event.patientCode().equals(patientCode) && event.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static PatientOutcome outcomeOf(SimulationResult result, int index) {
        return result.outcomes().get(index);
    }

    // ---------- tratamientos completos ----------

    @Test
    void aConsultationPatientIsTreatedAndDischarged() {
        SimulationCase simulationCase = caseOf(60, profile(1, 0, FLU, 30, 80.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.GENERAL_MEDICINE));

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.DISCHARGED, outcome.status());
        assertEquals(0, outcome.treatmentStartMinute());
        assertEquals(CompatibilityLevel.PRIMARY, outcome.careLevel());
        assertEquals(List.of(EventType.ARRIVAL, EventType.TRIAGE_ASSIGNED, EventType.DIAGNOSIS,
                        EventType.TREATMENT_STARTED, EventType.TREATMENT_FINISHED, EventType.DISCHARGE),
                typesOf(result, outcome.patientCode()));
    }

    @Test
    void theTreatmentLastsAsLongAsTheHealthModelSays() {
        Patient patient = profile(1, 0, FLU, 30, 80.0).toPatient();
        int expected = new HealthModel().treatmentMinutes(patient, 1.0);

        SimulationResult result = run(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE));

        assertEquals(expected, outcomeOf(result, 0).outcomeMinute());
    }

    @Test
    void aHospitalizedPatientOccupiesABedAndItIsFreedAtTheEnd() {
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE);
        SimulationResult result = run(caseOf(60, profile(1, 0, DENGUE, 30, 80.0)), hospital);

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.DISCHARGED, outcome.status());
        assertEquals("H1-C1", eventOf(result, outcome.patientCode(), EventType.TREATMENT_STARTED).resourceCode());
        assertEquals(hospital.getBeds().size(), hospital.freeBeds().size());
        assertEquals(hospital.getNurses().size(), hospital.freeNurses().size());
        assertEquals(hospital.getDoctors().size(), hospital.freeDoctors().size());
    }

    @Test
    void aSurgeryUsesTheOperatingRoom() {
        Hospital hospital = hospital(Specialty.SURGERY);
        SimulationResult result = run(caseOf(60, profile(1, 0, APPENDICITIS, 30, 80.0)), hospital);

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.DISCHARGED, outcome.status());
        assertEquals("Q1", eventOf(result, outcome.patientCode(), EventType.TREATMENT_STARTED).resourceCode());
        assertEquals(1, hospital.freeOperatingRooms().size());
        assertEquals(4, hospital.freeNurses().size());
    }

    @Test
    void aCriticalPatientIsAdmittedToTheIcu() {
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE);
        SimulationResult result = run(caseOf(60, profile(1, 0, HEART_ATTACK, 60, 80.0)), hospital);

        PatientOutcome outcome = outcomeOf(result, 0);
        SimulationEvent admission = eventOf(result, outcome.patientCode(), EventType.ICU_ADMISSION);
        assertEquals("UCI-C1", admission.resourceCode());
        assertTrue(typesOf(result, outcome.patientCode()).contains(EventType.TREATMENT_STARTED));
        assertEquals(1, hospital.freeIcuBeds().size());
    }

    @Test
    void aTreatmentThatUsesBloodTakesUnitsFromTheBank() {
        Hospital hospital = hospital(Specialty.TRAUMATOLOGY);
        int before = hospital.getBloodBank().totalUnits();

        SimulationResult result = run(caseOf(60, profile(1, 0, OPEN_FRACTURE, 30, 80.0)), hospital);

        assertEquals(PatientStatus.DISCHARGED, outcomeOf(result, 0).status());
        assertEquals(before - 2, hospital.getBloodBank().totalUnits());
    }

    @Test
    void aSecondarySpecialistTreatsMoreSlowly() {
        SimulationResult primary = run(caseOf(60, profile(1, 0, PNEUMONIA, 30, 80.0)),
                hospital(Specialty.INTERNAL_MEDICINE));
        SimulationResult secondary = run(caseOf(60, profile(1, 0, PNEUMONIA, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE));

        assertEquals(CompatibilityLevel.SECONDARY, outcomeOf(secondary, 0).careLevel());
        assertTrue(outcomeOf(secondary, 0).outcomeMinute() > outcomeOf(primary, 0).outcomeMinute());
    }

    @Test
    void anIncurableDiseaseEndsStabilizedWithoutCure() {
        SimulationResult result = run(caseOf(60, profile(1, 0, ADVANCED_CANCER, 60, 80.0)),
                hospital(Specialty.INTERNAL_MEDICINE));

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.STABILIZED_WITHOUT_CURE, outcome.status());
        List<EventType> types = typesOf(result, outcome.patientCode());
        assertTrue(types.contains(EventType.STABILIZED_WITHOUT_CURE));
        assertFalse(types.contains(EventType.DISCHARGE));
    }

    @Test
    void theTreatmentImprovesTheHealthOfTheDischargedPatient() {
        SimulationResult result = run(caseOf(60, profile(1, 0, FLU, 30, 40.0)),
                hospital(Specialty.GENERAL_MEDICINE));

        assertTrue(outcomeOf(result, 0).finalHealth() > 40.0);
    }

    // ---------- fallecimientos ----------

    @Test
    void anIrrecoverablePatientDiesAtTheEndOfTheTreatment() {
        SimulationCase simulationCase = caseOf(60, profile(1, 0, disease(FLU), 30, 80.0, true));
        SimulationResult result = run(simulationCase, hospital(Specialty.GENERAL_MEDICINE));

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.DECEASED, outcome.status());
        assertEquals(CauseOfDeath.IRREDUCIBLE_MORTALITY, outcome.causeOfDeath());
        List<EventType> types = typesOf(result, outcome.patientCode());
        assertTrue(types.contains(EventType.DEATH));
        assertTrue(types.contains(EventType.MORGUE_ADMISSION));
        assertFalse(types.contains(EventType.DISCHARGE));
        assertEquals(1, result.deaths());
        assertEquals(0, result.avoidableDeaths());
    }

    @Test
    void aPatientWhoWaitsTooLongDiesForLackOfTimelyCare() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 3.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.INTERNAL_MEDICINE));

        PatientOutcome waiting = outcomeOf(result, 1);
        assertEquals(PatientStatus.DECEASED, waiting.status());
        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, waiting.causeOfDeath());
        assertEquals(-1, waiting.treatmentStartMinute());
        assertEquals(PatientStatus.DISCHARGED, outcomeOf(result, 0).status());
    }

    @Test
    void aCriticalPatientWithoutIcuBedDiesForThatReason() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, HEART_ATTACK, 60, 80.0),
                profile(2, 0, HEART_ATTACK, 60, 3.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.INTERNAL_MEDICINE));

        assertEquals(CauseOfDeath.NO_ICU_BED, outcomeOf(result, 1).causeOfDeath());
    }

    @Test
    void aPatientWhoNeedsBloodAndThereIsNoneDiesForThatReason() {
        SimulationCase simulationCase = caseOf(60, profile(1, 0, OPEN_FRACTURE, 30, 5.0));
        SimulationResult result = run(simulationCase, hospital(3, 0, 4, Specialty.TRAUMATOLOGY));

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.DECEASED, outcome.status());
        assertEquals(CauseOfDeath.NO_COMPATIBLE_RESOURCE, outcome.causeOfDeath());
    }

    @Test
    void aPatientWhoNeedsBloodButWaitsForAnotherReasonDiesForLackOfTimelyCare() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, OPEN_FRACTURE, 30, 80.0),
                profile(2, 0, OPEN_FRACTURE, 30, 3.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.TRAUMATOLOGY));

        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcomeOf(result, 1).causeOfDeath());
    }

    @Test
    void aPatientWhoDoesNotNeedBloodIsNotBlamedOnItsAbsence() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 3.0));
        SimulationResult result = run(simulationCase, hospital(3, 0, 4, Specialty.INTERNAL_MEDICINE));

        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcomeOf(result, 1).causeOfDeath());
    }

    @Test
    void aCriticalPatientWhoWaitsWhileTheIcuIsFreeDiesForLackOfTimelyCare() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, HEART_ATTACK, 60, 3.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.INTERNAL_MEDICINE));

        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcomeOf(result, 1).causeOfDeath());
    }

    @Test
    void theTreatmentOfAWaitingPatientStartsWhenTheDoctorIsFree() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 10, DENGUE, 30, 80.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.INTERNAL_MEDICINE));

        assertEquals(outcomeOf(result, 0).outcomeMinute(), outcomeOf(result, 1).treatmentStartMinute());
        assertEquals(outcomeOf(result, 0).outcomeMinute() - 10, outcomeOf(result, 1).waitingMinutes());
    }

    @Test
    void aLateArrivalIsTreatedFromItsOwnArrivalMinute() {
        SimulationResult result = run(caseOf(60, profile(1, 30, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE));

        assertEquals(30, outcomeOf(result, 0).treatmentStartMinute());
        assertEquals(0, outcomeOf(result, 0).waitingMinutes());
    }

    @Test
    void aPatientTreatedBySomeoneNotIdealCanDieDuringTheTreatment() {
        SimulationCase simulationCase = caseOf(60, profile(1, 0, PNEUMONIA, 30, 3.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.GENERAL_MEDICINE));

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.DECEASED, outcome.status());
        assertEquals(CauseOfDeath.NON_IDEAL_SPECIALIST, outcome.causeOfDeath());
        assertEquals(CompatibilityLevel.SECONDARY, outcome.careLevel());
        assertTrue(outcome.treatmentStartMinute() >= 0);
    }

    @Test
    void aDeceasedPatientInTreatmentFreesTheStaffAndTheBed() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        run(caseOf(60, profile(1, 0, PNEUMONIA, 30, 3.0)), hospital);

        assertEquals(1, hospital.freeDoctors().size());
        assertEquals(4, hospital.freeNurses().size());
        assertEquals(4, hospital.freeBeds().size());
    }

    // ---------- morgue ----------

    @Test
    void theBodyLeavesTheMorgueAfterItsRetentionTime() {
        SimulationCase simulationCase = caseOf(720,
                profile(1, 0, disease(FLU), 30, 80.0, true),
                profile(2, 600, disease(FLU), 30, 80.0, false));
        SimulationResult result = run(simulationCase, hospital(Specialty.GENERAL_MEDICINE));

        String code = outcomeOf(result, 0).patientCode();
        int admitted = eventOf(result, code, EventType.MORGUE_ADMISSION).minute();
        int released = eventOf(result, code, EventType.MORGUE_RELEASE).minute();
        assertEquals(admitted + Morgue.RETENTION_MINUTES, released);
    }

    @Test
    void aBodyThatDoesNotFitInTheMorgueKeepsItsBedUntilThereIsRoom() {
        SimulationCase simulationCase = caseOf(720,
                profile(1, 0, disease(DENGUE), 30, 80.0, true),
                profile(2, 0, disease(DENGUE), 30, 80.0, true),
                profile(3, 600, disease(FLU), 30, 80.0, false));
        Hospital hospital = hospital(1, 5, 4, Specialty.INTERNAL_MEDICINE, Specialty.INTERNAL_MEDICINE,
                Specialty.GENERAL_MEDICINE);
        SimulationEngine engine = new SimulationEngine(simulationCase, hospital, new FifoStrategy());

        while (hospital.getMorgue().occupancy() == 0 && !engine.isFinished()) {
            engine.step();
        }
        assertEquals(1, hospital.getMorgue().occupancy());
        assertEquals(3, hospital.freeBeds().size());

        SimulationResult result = engine.run();
        String first = outcomeOf(result, 0).patientCode();
        String second = outcomeOf(result, 1).patientCode();
        int released = eventOf(result, first, EventType.MORGUE_RELEASE).minute();
        assertEquals(released, eventOf(result, second, EventType.MORGUE_ADMISSION).minute());
        assertEquals(4, hospital.freeBeds().size());
    }

    // ---------- remisión y abandono ----------

    @Test
    void aPatientNobodyCanTreatIsReferredAtOnce() {
        Hospital hospital = hospital(Specialty.PEDIATRICS);
        SimulationResult result = run(caseOf(60, profile(1, 5, APPENDICITIS, 30, 80.0)), hospital);

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.REFERRED, outcome.status());
        assertEquals(5, outcome.outcomeMinute());
        assertEquals(-1, outcome.treatmentStartMinute());
        assertNull(outcome.careLevel());
        assertTrue(typesOf(result, outcome.patientCode()).contains(EventType.REFERRAL));
    }

    @Test
    void aPatientWhoIsNeverAttendedLeavesAtTheLimit() {
        Disease slow = Disease.builder(90, "Enfermedad lenta")
                .mainSpecialty(Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.HOSPITALIZATION)
                .deteriorationRate(0.0)
                .durationMinutes(60, 120)
                .symptoms(Symptom.UNCONSCIOUS)
                .build();
        SimulationCase simulationCase = caseOf(60, profile(1, 0, slow, 30, 80.0, false));
        SimulationResult result = run(simulationCase, hospital(3, 5, 0, Specialty.GENERAL_MEDICINE));

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.UNATTENDED, outcome.status());
        assertEquals(60 + SimulationEngine.EXTRA_MINUTES, outcome.outcomeMinute());
        assertEquals(60 + SimulationEngine.EXTRA_MINUTES + 1, result.totalMinutes());
        assertTrue(typesOf(result, outcome.patientCode()).contains(EventType.UNATTENDED));
        assertEquals(80.0, outcome.finalHealth());
    }

    // ---------- avance, ejecución y estado ----------

    @Test
    void stepByStepGivesTheSameResultAsRunning() {
        CaseConfig config = new CaseConfig(7L, Scenario.CATASTROPHE, 120, 480);
        SimulationCase simulationCase = new CaseGenerator().generate(config);
        HospitalFactory factory = new HospitalFactory();

        SimulationResult direct = new SimulationEngine(simulationCase, factory.create(HospitalConfig.standard()),
                new FifoStrategy()).run();
        SimulationEngine stepped = new SimulationEngine(simulationCase, factory.create(HospitalConfig.standard()),
                new FifoStrategy());
        int steps = 0;
        while (!stepped.isFinished()) {
            assertEquals(steps, stepped.getMinute());
            stepped.step();
            steps++;
        }

        assertEquals(direct, stepped.getResult());
        assertEquals(direct.totalMinutes(), steps);
    }

    @Test
    void theSameCaseAndStrategyAlwaysGiveTheSameResult() {
        SimulationCase simulationCase = new CaseGenerator().generate(new CaseConfig(3L, Scenario.HIGH_DEMAND, 100, 480));
        HospitalFactory factory = new HospitalFactory();

        SimulationResult first = new SimulationEngine(simulationCase, factory.create(HospitalConfig.standard()),
                new FifoStrategy()).run();
        SimulationResult second = new SimulationEngine(simulationCase, factory.create(HospitalConfig.standard()),
                new FifoStrategy()).run();

        assertEquals(first, second);
    }

    @Test
    void differentSeedsGiveDifferentResults() {
        HospitalFactory factory = new HospitalFactory();
        SimulationResult one = new SimulationEngine(
                new CaseGenerator().generate(new CaseConfig(1L, Scenario.NORMAL, 100, 480)),
                factory.create(HospitalConfig.standard()), new FifoStrategy()).run();
        SimulationResult two = new SimulationEngine(
                new CaseGenerator().generate(new CaseConfig(2L, Scenario.NORMAL, 100, 480)),
                factory.create(HospitalConfig.standard()), new FifoStrategy()).run();

        assertNotEquals(one, two);
    }

    @Test
    void theEngineDoesNotChangeTheCase() {
        SimulationCase simulationCase = new CaseGenerator().generate(new CaseConfig(4L, Scenario.NORMAL, 50, 480));
        HospitalFactory factory = new HospitalFactory();

        SimulationResult first = new SimulationEngine(simulationCase, factory.create(HospitalConfig.standard()),
                new FifoStrategy()).run();
        SimulationResult second = new SimulationEngine(simulationCase, factory.create(HospitalConfig.standard()),
                new FifoStrategy()).run();

        assertEquals(first, second);
        assertEquals(50, simulationCase.profiles().size());
    }

    @Test
    void exposesTheStateWhileItRuns() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)), hospital,
                new FifoStrategy());

        assertEquals(0, engine.getMinute());
        assertEquals(1, engine.getPatients().size());
        assertEquals(hospital, engine.getHospital());
        assertTrue(engine.getEvents().isEmpty());
        assertFalse(engine.isFinished());

        engine.step();

        assertEquals(1, engine.getMinute());
        assertFalse(engine.getEvents().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> engine.getEvents().clear());
        assertThrows(UnsupportedOperationException.class, () -> engine.getPatients().clear());
    }

    @Test
    void aCriticalArrivalRaisesAnEmergencyAlert() {
        SimulationResult result = run(caseOf(60, profile(1, 0, HEART_ATTACK, 60, 15.0)),
                hospital(Specialty.INTERNAL_MEDICINE));

        PatientOutcome outcome = outcomeOf(result, 0);
        boolean alert = typesOf(result, outcome.patientCode()).contains(EventType.EMERGENCY_ALERT);
        assertEquals(outcome.triage() == TriageLevel.I, alert);
        assertEquals(TriageLevel.I, outcome.triage());
    }

    @Test
    void theEventsAreInChronologicalOrder() {
        SimulationResult result = new SimulationEngine(
                new CaseGenerator().generate(new CaseConfig(9L, Scenario.UNEXPECTED_EMERGENCIES, 80, 480)),
                new HospitalFactory().create(HospitalConfig.standard()), new FifoStrategy()).run();

        int previous = 0;
        for (SimulationEvent event : result.events()) {
            assertTrue(event.minute() >= previous);
            previous = event.minute();
        }
    }

    @Test
    void stepAfterTheEndFails() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new FifoStrategy());
        engine.run();

        assertTrue(engine.isFinished());
        assertThrows(IllegalStateException.class, engine::step);
    }

    @Test
    void theResultIsNotAvailableBeforeTheEnd() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new FifoStrategy());

        assertThrows(IllegalStateException.class, engine::getResult);
    }

    @Test
    void rejectsMissingArguments() {
        SimulationCase simulationCase = caseOf(60, profile(1, 0, FLU, 30, 80.0));
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);

        assertThrows(IllegalArgumentException.class, () -> new SimulationEngine(null, hospital, new FifoStrategy()));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationEngine(simulationCase, null, new FifoStrategy()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationEngine(simulationCase, hospital, null));
    }

    // ---------- estrategias que se equivocan ----------

    /** Estrategia de prueba que entrega la decisión que se le indique, sin revisar nada. */
    private static final class RogueStrategy implements AllocationStrategy {
        private final java.util.function.BiFunction<List<WaitingPatient>, Hospital, Assignment> decision;

        RogueStrategy(java.util.function.BiFunction<List<WaitingPatient>, Hospital, Assignment> decision) {
            this.decision = decision;
        }

        @Override
        public String getName() {
            return "Estrategia incorrecta";
        }

        @Override
        public Optional<Assignment> nextAssignment(int minute, List<WaitingPatient> waiting, Hospital hospital) {
            return Optional.ofNullable(decision.apply(waiting, hospital));
        }
    }

    @Test
    void failsIfTheStrategyAssignsAPatientWhoIsNotWaiting() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new RogueStrategy((waiting, hospital) -> new Assignment(
                profile(99, 0, FLU, 30, 80.0).toPatient(), hospital.getDoctors().get(0), List.of(), null, null, 0)));

        assertThrows(IllegalStateException.class, engine::step);
    }

    @Test
    void failsIfTheStrategyAssignsABusyResource() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new RogueStrategy((waiting, hospital) -> {
            Doctor doctor = hospital.getDoctors().get(0);
            doctor.assign(waiting.get(0).patient(), 500);
            return new Assignment(waiting.get(0).patient(), doctor, List.of(), null, null, 0);
        }));

        assertThrows(IllegalStateException.class, engine::step);
    }

    @Test
    void failsIfTheStrategyAssignsADoctorWhoCannotTreatThePatient() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, APPENDICITIS, 30, 80.0)),
                hospital(Specialty.SURGERY, Specialty.PEDIATRICS), new RogueStrategy((waiting, hospital) ->
                new Assignment(waiting.get(0).patient(), hospital.getDoctors().get(1), List.of(),
                        null, hospital.getOperatingRooms().get(0), 0)));

        assertThrows(IllegalStateException.class, engine::step);
    }

    @Test
    void failsIfTheStrategyAssignsBloodThatIsNotThere() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, OPEN_FRACTURE, 30, 80.0)),
                hospital(3, 0, 4, Specialty.TRAUMATOLOGY), new RogueStrategy((waiting, hospital) ->
                new Assignment(waiting.get(0).patient(), hospital.getDoctors().get(0),
                        hospital.getNurses().subList(0, 2), null, hospital.getOperatingRooms().get(0), 2)));

        assertThrows(IllegalStateException.class, engine::step);
    }

    // ---------- invariantes ----------

    @Test
    void everyScenarioEndsWithConsistentResults() {
        HospitalFactory factory = new HospitalFactory();
        for (Scenario scenario : Scenario.values()) {
            for (long seed = 1; seed <= 3; seed++) {
                CaseConfig config = new CaseConfig(seed, scenario, 150, 480);
                Hospital hospital = factory.create(HospitalConfig.standard());
                SimulationResult result = new SimulationEngine(new CaseGenerator().generate(config), hospital,
                        new FifoStrategy()).run();

                assertEquals(150, result.outcomes().size());
                assertTrue(result.outcomes().stream().allMatch(outcome -> outcome.status().isFinal()));
                assertEquals(150, result.events().stream().filter(e -> e.type() == EventType.ARRIVAL).count());
                assertEquals(result.deaths(), result.events().stream().filter(e -> e.type() == EventType.DEATH).count());
                assertEquals(result.outcomes().stream().filter(PatientOutcome::wasTreated).count(),
                        result.events().stream().filter(e -> e.type() == EventType.TREATMENT_STARTED).count());
                assertEquals(hospital.getDoctors().size(), hospital.freeDoctors().size());
                assertEquals(hospital.getNurses().size(), hospital.freeNurses().size());
                assertEquals(hospital.getOperatingRooms().size(), hospital.freeOperatingRooms().size());
                assertEquals("FIFO (orden de llegada)", result.strategyName());
            }
        }
    }

    @Test
    void aSmallHospitalUnderACatastropheLosesPatients() {
        HospitalConfig small = new HospitalConfig(4, 1, 1, 3, 4, 2, 2);
        SimulationResult result = new SimulationEngine(
                new CaseGenerator().generate(new CaseConfig(5L, Scenario.CATASTROPHE, 300, 480)),
                new HospitalFactory().create(small), new FifoStrategy()).run();

        assertTrue(result.deaths() > 0);
    }
}
