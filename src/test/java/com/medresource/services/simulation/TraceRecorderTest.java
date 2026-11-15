package com.medresource.services.simulation;

import com.medresource.model.BloodBank;
import com.medresource.model.BloodType;
import com.medresource.model.CareRecord;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.MinuteSample;
import com.medresource.model.MissingResource;
import com.medresource.model.Morgue;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Patient;
import com.medresource.model.Room;
import com.medresource.model.RoomType;
import com.medresource.model.Sex;
import com.medresource.model.SimulationTrace;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.services.strategy.Assignment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del registro de la traza de una ejecución.
 */
class TraceRecorderTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();
    private static final Disease APPENDICITIS = DiseaseCatalog.findById(8).orElseThrow();

    private static Hospital hospital() {
        return new Hospital(List.of(new Room("H1", RoomType.HOSPITALIZATION, 2)), new Room("UCI", RoomType.ICU, 1),
                List.of(new OperatingRoom(1)),
                List.of(new Doctor(1, Specialty.GENERAL_MEDICINE, 40, Sex.MALE),
                        new Doctor(2, Specialty.SURGERY, 45, Sex.FEMALE)),
                List.of(new Nurse(1, 30, Sex.FEMALE), new Nurse(2, 31, Sex.MALE)),
                new BloodBank(Map.of(BloodType.O_POSITIVE, 6)), new Morgue(2));
    }

    private static Patient patient(int id, int arrival, TriageLevel triage) {
        Patient patient = new Patient(id, 40, Sex.MALE, BloodType.O_POSITIVE, FLU, FLU.getSymptoms(), arrival, 75.0,
                false);
        patient.assignTriage(triage);
        return patient;
    }

    /** Cierra los minutos desde el 0 hasta el indicado sin nadie esperando. */
    private static void emptyMinutes(TraceRecorder recorder, int untilMinute) {
        for (int minute = 0; minute <= untilMinute; minute++) {
            recorder.minuteEnded(minute, List.of(), List.of(), List.of(), 0, patient -> false);
        }
    }

    @Test
    void keepsTheInitialBloodAndTheHealthOnArrival() {
        TraceRecorder recorder = new TraceRecorder(hospital());
        recorder.arrival(patient(1, 0, TriageLevel.III));

        SimulationTrace trace = recorder.toTrace();
        assertEquals(6, (int) trace.initialBlood().get(BloodType.O_POSITIVE));
        assertEquals(75.0, trace.arrivalHealthOf("P001"), 0.0);
    }

    @Test
    void registersWhoAttendedThePatientAndWithWhat() {
        Hospital hospital = hospital();
        TraceRecorder recorder = new TraceRecorder(hospital);
        Patient patient = new Patient(2, 30, Sex.FEMALE, BloodType.O_POSITIVE, APPENDICITIS,
                APPENDICITIS.getSymptoms(), 0, 60.0, false);
        Assignment assignment = new Assignment(patient, hospital.getDoctors().get(1), hospital.getNurses(),
                hospital.getBeds().get(0), hospital.getOperatingRooms().get(0), 0);

        recorder.careStarted(assignment, 5, 65, 300, Map.of(), 58.0);

        CareRecord care = recorder.toTrace().careOf("P002").orElseThrow();
        assertEquals("D02", care.doctorCode());
        assertEquals(Specialty.SURGERY, care.doctorSpecialty());
        assertEquals(List.of("E01", "E02"), care.nurseCodes());
        assertEquals("Q1", care.operatingRoomCode());
        assertEquals("H1-C1", care.bedCode());
        assertFalse(care.icuBed());
        assertEquals(58.0, care.healthAtStart(), 0.0);
        assertEquals(60, care.staffMinutes());
    }

    @Test
    void countsTheMinutesEachPatientWaitedForEachMissingResource() {
        TraceRecorder recorder = new TraceRecorder(hospital());
        Patient patient = patient(1, 0, TriageLevel.III);
        recorder.blockedAfterAssignment(List.of(patient),
                waiting -> Set.of(MissingResource.DOCTOR, MissingResource.OPERATING_ROOM));
        recorder.minuteEnded(0, List.of(), List.of(), List.of(patient), 0, waiting -> false);
        recorder.minuteEnded(1, List.of(), List.of(), List.of(patient), 0, waiting -> false);

        SimulationTrace trace = recorder.toTrace();
        assertEquals(Map.of(MissingResource.DOCTOR, 2, MissingResource.OPERATING_ROOM, 2),
                trace.blockedMinutesOf("P001"));
        MinuteSample sample = trace.sampleAt(1).orElseThrow();
        assertEquals(1, sample.waiting());
        assertEquals(1, sample.blockedBy(MissingResource.DOCTOR));
    }

    @Test
    void aPatientInTheTriageQueueWaitsForAStation() {
        TraceRecorder recorder = new TraceRecorder(hospital());
        Patient queued = patient(1, 0, TriageLevel.IV);
        Patient assessed = patient(2, 0, TriageLevel.IV);
        recorder.minuteEnded(0, List.of(queued), List.of(assessed), List.of(), 0, waiting -> false);

        SimulationTrace trace = recorder.toTrace();
        assertEquals(Map.of(MissingResource.TRIAGE_STATION, 1), trace.blockedMinutesOf("P001"));
        assertTrue(trace.blockedMinutesOf("P002").isEmpty());
        assertEquals(2, trace.sampleAt(0).orElseThrow().inTriage());
    }

    @Test
    void countsThePatientsThatPassedTheTargetOfTheirTriageLevel() {
        TraceRecorder recorder = new TraceRecorder(hospital());
        Patient urgent = patient(1, 0, TriageLevel.I);
        Patient mild = patient(2, 0, TriageLevel.V);
        recorder.blockedAfterAssignment(List.of(urgent, mild), waiting -> Set.of(MissingResource.DOCTOR));
        recorder.minuteEnded(0, List.of(), List.of(), List.of(urgent, mild), 0, waiting -> false);
        recorder.minuteEnded(1, List.of(), List.of(), List.of(urgent, mild), 0, waiting -> false);

        SimulationTrace trace = recorder.toTrace();
        assertEquals(0, trace.sampleAt(0).orElseThrow().overTarget());
        assertEquals(1, trace.sampleAt(1).orElseThrow().overTarget());
    }

    @Test
    void countsTheBusyResourcesAndTheMinutesEachOneWasBusy() {
        Hospital hospital = hospital();
        TraceRecorder recorder = new TraceRecorder(hospital);
        Patient patient = patient(1, 0, TriageLevel.III);
        hospital.getDoctors().get(0).assign(patient, 10);
        hospital.getBeds().get(1).assign(patient, 10);
        hospital.getTriageStations().get(0).assign(patient, 10);

        emptyMinutes(recorder, 2);

        SimulationTrace trace = recorder.toTrace();
        MinuteSample sample = trace.sampleAt(2).orElseThrow();
        assertEquals(1, sample.busyDoctors());
        assertEquals(1, sample.busyBeds());
        assertEquals(0, sample.busyNurses());
        assertEquals(3, trace.busyMinutesOf("D01"));
        assertEquals(3, trace.busyMinutesOf("H1-C2"));
        assertEquals(3, trace.busyMinutesOf("T1"));
        assertEquals(0, trace.busyMinutesOf("D02"));
    }

    @Test
    void countsTheMinutesACriticalPatientWaitedWithoutFirstAid() {
        TraceRecorder recorder = new TraceRecorder(hospital());
        Patient critical = patient(1, 0, TriageLevel.I);
        Patient stable = patient(2, 0, TriageLevel.IV);
        recorder.blockedAfterAssignment(List.of(critical, stable), waiting -> Set.of(MissingResource.DOCTOR));
        for (int minute = 0; minute < 3; minute++) {
            recorder.minuteEnded(minute, List.of(), List.of(), List.of(critical, stable), 0,
                    waiting -> waiting == critical);
        }

        assertEquals(Map.of("P001", 3), recorder.toTrace().criticalWithoutAidMinutes());
    }

    @Test
    void keepsTheBloodLeftAtTheEndOfEachMinute() {
        Hospital hospital = hospital();
        TraceRecorder recorder = new TraceRecorder(hospital);
        emptyMinutes(recorder, 0);
        hospital.getBloodBank().withdraw(BloodType.O_POSITIVE, 2);
        recorder.minuteEnded(1, List.of(), List.of(), List.of(), 0, waiting -> false);

        SimulationTrace trace = recorder.toTrace();
        assertEquals(6, trace.sampleAt(0).orElseThrow().bloodOf(BloodType.O_POSITIVE));
        assertEquals(4, trace.sampleAt(1).orElseThrow().bloodOf(BloodType.O_POSITIVE));
        assertEquals(6, (int) trace.initialBlood().get(BloodType.O_POSITIVE));
    }

    @Test
    void needsAHospital() {
        assertThrows(IllegalArgumentException.class, () -> new TraceRecorder(null));
    }
}
