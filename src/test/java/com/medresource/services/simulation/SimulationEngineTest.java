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
import com.medresource.model.HealthModel;
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
import com.medresource.model.StaffMember;
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
    private static final int SEVERE_HEAD_TRAUMA = 10;
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

    /** Minutos que dura en el triage la valoración de un paciente con esa enfermedad. */
    private static int triageMinutes(int diseaseId) {
        return new TriageService().classify(profile(1, 0, diseaseId, 30, 80.0).toPatient()).getAssessmentMinutes();
    }

    /** Avanza el motor hasta el minuto en que empieza el primer tratamiento, incluido. */
    private static void stepUntilATreatmentStarts(SimulationEngine engine) {
        while (engine.getEvents().stream().noneMatch(event -> event.type() == EventType.TREATMENT_STARTED)) {
            engine.step();
        }
    }

    // ---------- triage ----------

    @Test
    void aPatientIsAssessedAtATriageStationBeforeWaiting() {
        SimulationResult result = run(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE));

        SimulationEvent start = eventOf(result, "P001", EventType.TRIAGE_STARTED);
        SimulationEvent end = eventOf(result, "P001", EventType.TRIAGE_ASSIGNED);
        assertEquals(0, start.minute());
        assertEquals("T1", start.resourceCode());
        assertEquals(TriageLevel.IV, outcomeOf(result, 0).triage());
        assertEquals(TriageLevel.IV.getAssessmentMinutes(), end.minute());
        assertEquals(end.minute(), eventOf(result, "P001", EventType.DIAGNOSIS).minute());
        assertEquals(end.minute(), outcomeOf(result, 0).treatmentStartMinute());
    }

    @Test
    void theAssessmentLastsAccordingToTheTriageLevel() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, FLU, 30, 80.0),
                profile(2, 0, DENGUE, 30, 80.0),
                profile(3, 0, APPENDICITIS, 30, 80.0));
        SimulationResult result = run(simulationCase,
                hospital(Specialty.GENERAL_MEDICINE, Specialty.INTERNAL_MEDICINE, Specialty.SURGERY));

        for (int index = 0; index < 3; index++) {
            String code = outcomeOf(result, index).patientCode();
            assertEquals(outcomeOf(result, index).triage().getAssessmentMinutes(),
                    eventOf(result, code, EventType.TRIAGE_ASSIGNED).minute()
                            - eventOf(result, code, EventType.TRIAGE_STARTED).minute());
        }
    }

    @Test
    void aCriticalPatientSkipsTheTriageStationAndIsTreatedAtOnce() {
        SimulationResult result = run(caseOf(60, profile(1, 0, HEART_ATTACK, 60, 80.0)),
                hospital(Specialty.INTERNAL_MEDICINE));

        SimulationEvent start = eventOf(result, "P001", EventType.TRIAGE_STARTED);
        assertNull(start.resourceCode());
        assertEquals(0, start.minute());
        assertEquals(0, eventOf(result, "P001", EventType.TRIAGE_ASSIGNED).minute());
        assertEquals(0, outcomeOf(result, 0).treatmentStartMinute());
    }

    @Test
    void whenEveryStationIsBusyThePatientsQueueInOrderOfArrival() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, FLU, 30, 80.0),
                profile(2, 0, FLU, 30, 80.0),
                profile(3, 0, FLU, 30, 80.0),
                profile(4, 0, FLU, 30, 80.0),
                profile(5, 1, FLU, 30, 80.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.GENERAL_MEDICINE));

        int assessment = triageMinutes(FLU);
        assertEquals("T1", eventOf(result, "P001", EventType.TRIAGE_STARTED).resourceCode());
        assertEquals("T2", eventOf(result, "P002", EventType.TRIAGE_STARTED).resourceCode());
        assertEquals("T3", eventOf(result, "P003", EventType.TRIAGE_STARTED).resourceCode());
        assertEquals(assessment, eventOf(result, "P004", EventType.TRIAGE_STARTED).minute());
        assertEquals("T1", eventOf(result, "P004", EventType.TRIAGE_STARTED).resourceCode());
        assertEquals(assessment, eventOf(result, "P005", EventType.TRIAGE_STARTED).minute());
        assertEquals("T2", eventOf(result, "P005", EventType.TRIAGE_STARTED).resourceCode());
    }

    @Test
    void aCriticalPatientDoesNotQueueBehindTheOthers() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, FLU, 30, 80.0),
                profile(2, 0, FLU, 30, 80.0),
                profile(3, 0, FLU, 30, 80.0),
                profile(4, 0, FLU, 30, 80.0),
                profile(5, 0, HEART_ATTACK, 60, 80.0));
        SimulationResult result = run(simulationCase,
                hospital(Specialty.GENERAL_MEDICINE, Specialty.INTERNAL_MEDICINE));

        assertEquals(0, outcomeOf(result, 4).treatmentStartMinute());
    }

    @Test
    void theNursesOfTheHospitalDoNotWorkInTheTriage() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        SimulationEngine engine = new SimulationEngine(caseOf(60,
                profile(1, 0, FLU, 30, 80.0),
                profile(2, 0, FLU, 30, 80.0)), hospital, new FifoStrategy());

        engine.step();

        assertEquals(1, hospital.freeTriageStations().size());
        assertEquals(4, hospital.freeNurses().size());
        assertEquals(1, hospital.freeDoctors().size());
    }

    @Test
    void aPatientCanDieWhileQueuingForTheTriage() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, FLU, 30, 80.0),
                profile(2, 0, FLU, 30, 80.0),
                profile(3, 0, FLU, 30, 80.0),
                profile(4, 0, OPEN_FRACTURE, 30, 0.2));
        SimulationResult result = run(simulationCase,
                hospital(Specialty.GENERAL_MEDICINE, Specialty.TRAUMATOLOGY));

        PatientOutcome outcome = outcomeOf(result, 3);
        assertEquals(PatientStatus.DECEASED, outcome.status());
        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcome.causeOfDeath());
        assertNull(eventOf(result, "P004", EventType.DEATH).resourceCode());
        assertFalse(typesOf(result, "P004").contains(EventType.TRIAGE_STARTED));
    }

    @Test
    void aPatientWhoDiesAtATriageStationFreesIt() {
        Hospital hospital = hospital(Specialty.TRAUMATOLOGY);
        SimulationResult result = run(caseOf(60, profile(1, 0, OPEN_FRACTURE, 30, 0.2)), hospital);

        assertEquals(PatientStatus.DECEASED, outcomeOf(result, 0).status());
        assertEquals("T1", eventOf(result, "P001", EventType.DEATH).resourceCode());
        assertFalse(typesOf(result, "P001").contains(EventType.TRIAGE_ASSIGNED));
        assertEquals(Hospital.TRIAGE_STATIONS, hospital.freeTriageStations().size());
    }

    @Test
    void theStrategyOnlySeesPatientsWhoFinishedTheTriage() {
        List<Integer> waitingSizes = new ArrayList<>();
        AllocationStrategy spy = new AllocationStrategy() {
            @Override
            public String getName() {
                return "Espía";
            }

            @Override
            public Optional<Assignment> nextAssignment(int minute, List<WaitingPatient> waiting, Hospital hospital) {
                waitingSizes.add(waiting.size());
                return Optional.empty();
            }
        };
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), spy);

        for (int minute = 0; minute <= triageMinutes(FLU); minute++) {
            engine.step();
        }

        assertEquals(0, (int) waitingSizes.get(0));
        assertEquals(1, (int) waitingSizes.get(waitingSizes.size() - 1));
    }

    // ---------- tratamientos completos ----------

    @Test
    void aConsultationPatientIsTreatedAndDischarged() {
        SimulationCase simulationCase = caseOf(60, profile(1, 0, FLU, 30, 80.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.GENERAL_MEDICINE));

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.DISCHARGED, outcome.status());
        assertEquals(triageMinutes(FLU), outcome.treatmentStartMinute());
        assertEquals(CompatibilityLevel.PRIMARY, outcome.careLevel());
        assertEquals(List.of(EventType.ARRIVAL, EventType.TRIAGE_STARTED, EventType.TRIAGE_ASSIGNED,
                        EventType.DIAGNOSIS, EventType.TREATMENT_STARTED, EventType.TREATMENT_FINISHED,
                        EventType.DISCHARGE),
                typesOf(result, outcome.patientCode()));
    }

    @Test
    void theTreatmentLastsAsLongAsTheHealthModelSays() {
        // Durante el triage el paciente pierde salud, y con ella se calcula la duración.
        HealthModel model = new HealthModel();
        Patient patient = profile(1, 0, FLU, 30, 80.0).toPatient();
        patient.deteriorate(model.deteriorationPerMinute(patient, 0.0) * triageMinutes(FLU));
        int expected = model.treatmentMinutes(patient, 1.0);

        SimulationResult result = run(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE));

        assertEquals(expected, outcomeOf(result, 0).outcomeMinute() - outcomeOf(result, 0).treatmentStartMinute());
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
        // Un solo enfermero, ocupado con el primer paciente: nadie queda libre para darle primeros auxilios.
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 3.0));
        SimulationResult result = run(simulationCase, hospital(3, 5, 1, Specialty.INTERNAL_MEDICINE));

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
        SimulationResult result = run(simulationCase, hospital(3, 0, 1, Specialty.INTERNAL_MEDICINE));

        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcomeOf(result, 1).causeOfDeath());
    }

    @Test
    void aCriticalPatientWhoWaitsWhileTheIcuIsFreeDiesForLackOfTimelyCare() {
        // El infarto llega cuando el único médico ya atiende al paciente con dengue.
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 5, HEART_ATTACK, 60, 3.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.INTERNAL_MEDICINE));

        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcomeOf(result, 1).causeOfDeath());
    }

    @Test
    void theDoctorOfAHospitalizedPatientIsFreeAgainAfterTheAdmission() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 10, DENGUE, 30, 80.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.INTERNAL_MEDICINE));

        int doctorFree = triageMinutes(DENGUE) + TreatmentType.INITIAL_CARE_MINUTES;
        assertEquals(doctorFree, outcomeOf(result, 1).treatmentStartMinute());
        assertEquals(doctorFree - 10, outcomeOf(result, 1).waitingMinutes());
        assertTrue(outcomeOf(result, 0).outcomeMinute() > doctorFree);
    }

    @Test
    void aHospitalizedPatientKeepsTheBedAfterTheDoctorAndTheNurseLeave() {
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE);
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, DENGUE, 30, 80.0)), hospital,
                new FifoStrategy());

        stepUntilATreatmentStarts(engine);
        assertEquals(0, hospital.freeDoctors().size());
        assertEquals(3, hospital.freeNurses().size());
        for (int minute = 1; minute <= TreatmentType.INITIAL_CARE_MINUTES; minute++) {
            engine.step();
        }

        assertEquals(1, hospital.freeDoctors().size());
        assertEquals(4, hospital.freeNurses().size());
        assertEquals(3, hospital.freeBeds().size());
    }

    @Test
    void aConsultationKeepsTheDoctorUntilItEnds() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, FLU, 30, 80.0),
                profile(2, 5, FLU, 30, 80.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.GENERAL_MEDICINE));

        assertEquals(outcomeOf(result, 0).outcomeMinute(), outcomeOf(result, 1).treatmentStartMinute());
        assertEquals(outcomeOf(result, 0).outcomeMinute() - 5, outcomeOf(result, 1).waitingMinutes());
    }

    @Test
    void theSurgeonAndTheOperatingRoomAreFreeAgainWhenTheOperationEnds() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, APPENDICITIS, 30, 80.0),
                profile(2, 5, APPENDICITIS, 30, 80.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.SURGERY));

        int recoveryMinute = eventOf(result, "P001", EventType.RECOVERY_STARTED).minute();
        assertEquals(recoveryMinute, outcomeOf(result, 1).treatmentStartMinute());
        assertTrue(recoveryMinute < outcomeOf(result, 0).outcomeMinute());
        assertEquals("Q1", eventOf(result, "P002", EventType.TREATMENT_STARTED).resourceCode());
    }

    @Test
    void anOperatedPatientRecoversInTheReservedBed() {
        SimulationResult result = run(caseOf(60, profile(1, 0, APPENDICITIS, 30, 80.0)),
                hospital(Specialty.SURGERY));

        assertEquals(List.of(EventType.ARRIVAL, EventType.TRIAGE_STARTED, EventType.TRIAGE_ASSIGNED,
                        EventType.DIAGNOSIS, EventType.TREATMENT_STARTED, EventType.RECOVERY_STARTED,
                        EventType.TREATMENT_FINISHED, EventType.DISCHARGE),
                typesOf(result, "P001"));
        assertEquals("Q1", eventOf(result, "P001", EventType.TREATMENT_STARTED).resourceCode());
        assertEquals("H1-C1", eventOf(result, "P001", EventType.RECOVERY_STARTED).resourceCode());
        assertEquals("H1-C1", eventOf(result, "P001", EventType.DISCHARGE).resourceCode());

        int start = eventOf(result, "P001", EventType.TREATMENT_STARTED).minute();
        int end = eventOf(result, "P001", EventType.TREATMENT_FINISHED).minute();
        assertEquals(start + TreatmentType.SURGERY.operationMinutes(end - start),
                eventOf(result, "P001", EventType.RECOVERY_STARTED).minute());
    }

    @Test
    void duringTheRecoveryOnlyTheBedStaysOccupied() {
        Hospital hospital = hospital(Specialty.SURGERY);
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, APPENDICITIS, 30, 80.0)),
                hospital, new FifoStrategy());

        stepUntilATreatmentStarts(engine);
        assertEquals(0, hospital.freeOperatingRooms().size());
        assertEquals(3, hospital.freeBeds().size());
        assertEquals(2, hospital.freeNurses().size());
        while (engine.getEvents().stream().noneMatch(event -> event.type() == EventType.RECOVERY_STARTED)) {
            engine.step();
        }

        assertEquals(1, hospital.freeOperatingRooms().size());
        assertEquals(1, hospital.freeDoctors().size());
        assertEquals(4, hospital.freeNurses().size());
        assertEquals(3, hospital.freeBeds().size());
    }

    @Test
    void anOperatedPatientWhoNeedsIntensiveCareGoesToTheIcuWhenTheOperationEnds() {
        SimulationResult result = run(caseOf(60, profile(1, 0, SEVERE_HEAD_TRAUMA, 30, 80.0)),
                hospital(Specialty.SURGERY));

        List<EventType> types = typesOf(result, "P001");
        assertTrue(types.indexOf(EventType.TREATMENT_STARTED) < types.indexOf(EventType.ICU_ADMISSION));
        assertFalse(types.contains(EventType.RECOVERY_STARTED));
        assertEquals("Q1", eventOf(result, "P001", EventType.TREATMENT_STARTED).resourceCode());
        assertEquals("UCI-C1", eventOf(result, "P001", EventType.ICU_ADMISSION).resourceCode());
        assertTrue(eventOf(result, "P001", EventType.ICU_ADMISSION).minute()
                > eventOf(result, "P001", EventType.TREATMENT_STARTED).minute());
    }

    @Test
    void aPatientWhoDiesDuringTheOperationFreesTheReservedBed() {
        Hospital hospital = hospital(Specialty.SURGERY);
        // Lo opera un cirujano, que no es el ideal para una fractura expuesta, y llega muy grave.
        SimulationResult result = run(caseOf(60, profile(1, 0, OPEN_FRACTURE, 30, 3.0)), hospital);

        assertEquals(PatientStatus.DECEASED, outcomeOf(result, 0).status());
        assertFalse(typesOf(result, "P001").contains(EventType.RECOVERY_STARTED));
        assertEquals("Q1", eventOf(result, "P001", EventType.DEATH).resourceCode());
        assertNull(eventOf(result, "P001", EventType.MORGUE_ADMISSION).resourceCode());
        assertEquals(4, hospital.freeBeds().size());
        assertEquals(1, hospital.freeOperatingRooms().size());
    }

    @Test
    void theStartOfASurgerySaysWhenThePatientGoesToRecovery() {
        SimulationResult result = run(caseOf(60, profile(1, 0, APPENDICITIS, 30, 80.0)),
                hospital(Specialty.SURGERY));

        int recoveryMinute = eventOf(result, "P001", EventType.RECOVERY_STARTED).minute();
        assertTrue(eventOf(result, "P001", EventType.TREATMENT_STARTED).description()
                .contains("pasa a recuperación en el minuto " + recoveryMinute));
    }

    @Test
    void theStartOfAHospitalizationSaysWhenTheDoctorIsFree() {
        SimulationResult result = run(caseOf(60, profile(1, 0, DENGUE, 30, 80.0)),
                hospital(Specialty.INTERNAL_MEDICINE));

        assertTrue(eventOf(result, "P001", EventType.TREATMENT_STARTED).description()
                .contains("el médico queda libre en el minuto "
                        + (triageMinutes(DENGUE) + TreatmentType.INITIAL_CARE_MINUTES)));
    }

    @Test
    void aLateArrivalIsTreatedFromItsOwnArrivalMinute() {
        SimulationResult result = run(caseOf(60, profile(1, 30, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE));

        assertEquals(30 + triageMinutes(FLU), outcomeOf(result, 0).treatmentStartMinute());
        assertEquals(triageMinutes(FLU), outcomeOf(result, 0).waitingMinutes());
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
    void aPatientNobodyCanTreatIsReferredAsSoonAsTheTriageEnds() {
        Hospital hospital = hospital(Specialty.PEDIATRICS);
        SimulationResult result = run(caseOf(60, profile(1, 5, APPENDICITIS, 30, 80.0)), hospital);

        PatientOutcome outcome = outcomeOf(result, 0);
        assertEquals(PatientStatus.REFERRED, outcome.status());
        assertEquals(5 + triageMinutes(APPENDICITIS), outcome.outcomeMinute());
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
    void tellsWhenTheTreatmentOfAPatientStartedAndWhenItEnds() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new FifoStrategy());
        Patient patient = engine.getPatients().get(0);

        assertTrue(engine.getTreatmentSpan(patient).isEmpty());
        stepUntilATreatmentStarts(engine);
        SimulationEngine.TreatmentSpan span = engine.getTreatmentSpan(patient).orElseThrow();
        while (!engine.isFinished()) {
            engine.step();
        }

        SimulationResult result = engine.getResult();
        assertEquals(outcomeOf(result, 0).treatmentStartMinute(), span.startMinute());
        assertEquals(outcomeOf(result, 0).outcomeMinute(), span.endMinute());
        assertEquals(span.endMinute(), span.staffEndMinute());
        assertTrue(engine.getTreatmentSpan(patient).isEmpty());
    }

    @Test
    void theSpanOfASurgerySaysWhenTheOperationEndsAndWhenTheRecoveryEnds() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, APPENDICITIS, 30, 80.0)),
                hospital(Specialty.SURGERY), new FifoStrategy());
        Patient patient = engine.getPatients().get(0);

        stepUntilATreatmentStarts(engine);
        SimulationEngine.TreatmentSpan span = engine.getTreatmentSpan(patient).orElseThrow();
        SimulationResult result = engine.run();

        assertEquals(eventOf(result, "P001", EventType.RECOVERY_STARTED).minute(), span.staffEndMinute());
        assertEquals(outcomeOf(result, 0).outcomeMinute(), span.endMinute());
        assertTrue(span.startMinute() < span.staffEndMinute() && span.staffEndMinute() < span.endMinute());
    }

    @Test
    void theSpanOfATreatmentNeedsAPatient() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new FifoStrategy());

        assertThrows(IllegalArgumentException.class, () -> engine.getTreatmentSpan(null));
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
            // Espera a que alguien termine el triage para equivocarse con él.
            return waiting.isEmpty() ? Optional.empty() : Optional.ofNullable(decision.apply(waiting, hospital));
        }
    }

    @Test
    void failsIfTheStrategyAssignsAPatientWhoIsNotWaiting() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new RogueStrategy((waiting, hospital) -> new Assignment(
                profile(99, 0, FLU, 30, 80.0).toPatient(), hospital.getDoctors().get(0), List.of(), null, null, 0)));

        assertThrows(IllegalStateException.class, engine::run);
    }

    @Test
    void failsIfTheStrategyAssignsABusyResource() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, FLU, 30, 80.0)),
                hospital(Specialty.GENERAL_MEDICINE), new RogueStrategy((waiting, hospital) -> {
            Doctor doctor = hospital.getDoctors().get(0);
            doctor.assign(waiting.get(0).patient(), 500);
            return new Assignment(waiting.get(0).patient(), doctor, List.of(), null, null, 0);
        }));

        assertThrows(IllegalStateException.class, engine::run);
    }

    @Test
    void failsIfTheStrategyAssignsADoctorWhoCannotTreatThePatient() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, APPENDICITIS, 30, 80.0)),
                hospital(Specialty.SURGERY, Specialty.PEDIATRICS), new RogueStrategy((waiting, hospital) ->
                new Assignment(waiting.get(0).patient(), hospital.getDoctors().get(1), List.of(),
                        null, hospital.getOperatingRooms().get(0), 0)));

        assertThrows(IllegalStateException.class, engine::run);
    }

    @Test
    void failsIfTheStrategyAssignsBloodThatIsNotThere() {
        SimulationEngine engine = new SimulationEngine(caseOf(60, profile(1, 0, OPEN_FRACTURE, 30, 80.0)),
                hospital(3, 0, 4, Specialty.TRAUMATOLOGY), new RogueStrategy((waiting, hospital) ->
                new Assignment(waiting.get(0).patient(), hospital.getDoctors().get(0),
                        hospital.getNurses().subList(0, 2), null, hospital.getOperatingRooms().get(0), 2)));

        assertThrows(IllegalStateException.class, engine::run);
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

    // ---------- primeros auxilios ----------

    /** Avanza el motor hasta el minuto en que alguien recibe primeros auxilios, incluido. */
    private static void stepUntilFirstAid(SimulationEngine engine) {
        while (engine.getEvents().stream().noneMatch(event -> event.type() == EventType.FIRST_AID)) {
            engine.step();
        }
    }

    @Test
    void aFreeNurseGivesFirstAidToACriticalPatientWhileTheDoctorIsBusy() {
        SimulationCase simulationCase = caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 3.0));
        SimulationResult result = run(simulationCase, hospital(Specialty.INTERNAL_MEDICINE));

        SimulationEvent firstAid = eventOf(result, "P002", EventType.FIRST_AID);
        assertNull(firstAid.resourceCode());
        assertTrue(firstAid.description().contains("E0"), firstAid.description());
        assertEquals(PatientStatus.DISCHARGED, outcomeOf(result, 1).status());
    }

    @Test
    void firstAidHalvesTheHealthAWaitingPatientLoses() {
        SimulationEngine engine = new SimulationEngine(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 3.0)), hospital(Specialty.INTERNAL_MEDICINE), new FifoStrategy());
        stepUntilFirstAid(engine);
        Patient patient = engine.getPatients().get(1);
        double before = patient.getHealth();
        double withoutCare = new HealthModel().deteriorationPerMinute(patient, 0.0);

        engine.step();

        assertEquals(0.5, HealthModel.FIRST_AID_EFFECTIVENESS, 0.0);
        assertEquals(withoutCare * 0.5, before - patient.getHealth(), 1e-9);
    }

    @Test
    void aPatientWhoIsNotCriticalGetsNoFirstAid() {
        SimulationResult result = run(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 80.0)), hospital(Specialty.INTERNAL_MEDICINE));

        assertFalse(typesOf(result, "P002").contains(EventType.FIRST_AID));
    }

    @Test
    void aDoctorGivesFirstAidWhenNoNurseIsFree() {
        SimulationResult result = run(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 3.0)), hospital(3, 5, 1, Specialty.INTERNAL_MEDICINE,
                Specialty.TRAUMATOLOGY));

        assertTrue(eventOf(result, "P002", EventType.FIRST_AID).description().contains("D02"));
    }

    @Test
    void theStaffGivingFirstAidIsFreeForTheStrategyEveryMinute() {
        SimulationEngine engine = new SimulationEngine(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 3.0)), hospital(Specialty.INTERNAL_MEDICINE), new FifoStrategy());
        stepUntilFirstAid(engine);
        Patient patient = engine.getPatients().get(1);
        StaffMember carer = engine.getHospital().getNurses().stream()
                .filter(nurse -> nurse.getAssignedPatient() == patient).findFirst().orElseThrow();

        SimulationResult result = engine.run();

        assertTrue(carer.isAvailable());
        assertTrue(outcomeOf(result, 1).wasTreated());
        assertEquals(1, typesOf(result, "P002").stream().filter(type -> type == EventType.FIRST_AID).count());
    }

    @Test
    void theMostCriticalPatientGetsTheOnlyFreeCarerFirst() {
        // Dos enfermeros: uno se queda con el primer paciente y el otro solo alcanza para uno de los críticos.
        SimulationResult result = run(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 0, DENGUE, 30, 4.0),
                profile(3, 0, DENGUE, 30, 2.0)), hospital(3, 5, 2, Specialty.INTERNAL_MEDICINE));

        assertTrue(typesOf(result, "P003").contains(EventType.FIRST_AID));
        assertFalse(typesOf(result, "P002").contains(EventType.FIRST_AID));
    }

    // ---------- cama para el triage I mientras espera ----------

    @Test
    void aTriageIPatientWaitsInAFreeIcuBedWhileTheDoctorIsBusy() {
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE);
        SimulationResult result = run(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 5, HEART_ATTACK, 60, 30.0)), hospital);

        String icuBed = hospital.getIcuBeds().get(0).getCode();
        assertEquals(icuBed, eventOf(result, "P002", EventType.FIRST_AID).resourceCode());
        assertEquals(icuBed, eventOf(result, "P002", EventType.ICU_ADMISSION).resourceCode());
    }

    @Test
    void whenTheIcuIsTakenTheNextTriageIPatientWaitsInAWardBed() {
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE);
        SimulationResult result = run(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 5, HEART_ATTACK, 60, 30.0),
                profile(3, 5, HEART_ATTACK, 60, 30.0)), hospital);

        String wardBed = eventOf(result, "P003", EventType.FIRST_AID).resourceCode();
        String icuBed = hospital.getIcuBeds().get(0).getCode();
        assertEquals(icuBed, eventOf(result, "P002", EventType.FIRST_AID).resourceCode());
        assertTrue(hospital.getBeds().stream().anyMatch(bed -> bed.getCode().equals(wardBed)), wardBed);
    }

    @Test
    void theTriageIBedIsTakenEvenWithoutStaffToGiveFirstAid() {
        // El único enfermero atiende al primer paciente: el infartado espera en la cama sin primeros auxilios.
        Hospital hospital = hospital(3, 5, 1, Specialty.INTERNAL_MEDICINE);
        SimulationEngine engine = new SimulationEngine(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 5, HEART_ATTACK, 60, 30.0)), hospital, new FifoStrategy());
        stepUntilFirstAid(engine);
        Patient patient = engine.getPatients().get(1);

        assertEquals(patient, hospital.getIcuBeds().get(0).getAssignedPatient());
        assertTrue(hospital.getNurses().stream().noneMatch(nurse -> nurse.getAssignedPatient() == patient));
        assertTrue(hospital.getDoctors().stream().noneMatch(doctor -> doctor.getAssignedPatient() == patient));
    }

    @Test
    void aTriageIPatientWhoDiesWaitingLeavesTheBodyInItsBedUntilTheMorgue() {
        // El médico y el único enfermero atienden al primer paciente; el infarto muy grave no alcanza a esperar.
        Hospital hospital = hospital(3, 5, 1, Specialty.INTERNAL_MEDICINE);
        SimulationResult result = run(caseOf(60,
                profile(1, 0, DENGUE, 30, 80.0),
                profile(2, 5, HEART_ATTACK, 60, 2.0)), hospital);
        String icuBed = hospital.getIcuBeds().get(0).getCode();

        assertEquals(PatientStatus.DECEASED, outcomeOf(result, 1).status());
        assertEquals(CauseOfDeath.LACK_OF_TIMELY_CARE, outcomeOf(result, 1).causeOfDeath());
        assertEquals(icuBed, eventOf(result, "P002", EventType.DEATH).resourceCode());
        assertEquals(icuBed, eventOf(result, "P002", EventType.MORGUE_ADMISSION).resourceCode());
        assertTrue(hospital.getIcuBeds().get(0).isAvailable());
    }
}
