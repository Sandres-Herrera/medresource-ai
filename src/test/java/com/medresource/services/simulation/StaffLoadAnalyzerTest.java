package com.medresource.services.simulation;

import com.medresource.model.BloodBank;
import com.medresource.model.BloodType;
import com.medresource.model.CareRecord;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.MissingResource;
import com.medresource.model.Morgue;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.PatientStatus;
import com.medresource.model.Room;
import com.medresource.model.RoomType;
import com.medresource.model.Sex;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.Specialty;
import com.medresource.model.SpecialtyLoad;
import com.medresource.model.StaffLoad;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.medresource.services.simulation.ReportFixtures.APPENDICITIS;
import static com.medresource.services.simulation.ReportFixtures.care;
import static com.medresource.services.simulation.ReportFixtures.emptySamples;
import static com.medresource.services.simulation.ReportFixtures.outcome;
import static com.medresource.services.simulation.ReportFixtures.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la carga del personal y de cada especialidad.
 */
class StaffLoadAnalyzerTest {

    /** Un médico general (D01), un cirujano (D02) y dos enfermeros. */
    private static Hospital hospital() {
        return new Hospital(List.of(new Room("H1", RoomType.HOSPITALIZATION, 2)), new Room("UCI", RoomType.ICU, 1),
                List.of(new OperatingRoom(1)),
                List.of(new Doctor(1, Specialty.GENERAL_MEDICINE, 40, Sex.MALE),
                        new Doctor(2, Specialty.SURGERY, 40, Sex.FEMALE)),
                List.of(new Nurse(1, 30, Sex.FEMALE), new Nurse(2, 30, Sex.MALE)),
                new BloodBank(Map.of()), new Morgue(2));
    }

    /** 100 minutos: el médico general atendió a P001 y el cirujano, con los dos enfermeros, a P002. */
    private static SimulationTrace trace() {
        CareRecord surgery = new CareRecord("P002", BloodType.O_POSITIVE, 30, 90, 200, "D02", Specialty.SURGERY,
                List.of("E01", "E02"), "Q1", "H1-C1", false, Map.of(), 60.0);
        return new SimulationTrace(Map.of(), List.of(care(1, 0, 80.0), surgery), emptySamples(100),
                Map.of("P002", Map.of(MissingResource.DOCTOR, 30),
                        "P003", Map.of(MissingResource.DOCTOR, 10, MissingResource.BED, 5),
                        "P004", Map.of(MissingResource.DOCTOR, 15)),
                Map.of("D01", 50, "D02", 100, "E01", 60), Map.of(), Map.of());
    }

    /** P001 con gripa, P002 y P003 con apendicitis, P004 con infarto (su especialidad es medicina interna). */
    private static SimulationResult result4() {
        return result(60, 100,
                outcome(1, TriageLevel.IV, 0, 0, 30, PatientStatus.DISCHARGED, null, 90.0),
                outcome(2, APPENDICITIS, TriageLevel.II, 0, 30, 200, PatientStatus.DISCHARGED, null, 90.0),
                outcome(3, APPENDICITIS, TriageLevel.II, 0, -1, 99, PatientStatus.UNATTENDED, null, 40.0),
                outcome(4, DiseaseCatalog.findById(9).orElseThrow(), TriageLevel.I, 0, -1, 99,
                        PatientStatus.REFERRED, null, 50.0));
    }

    private static StaffLoad loadOf(List<StaffLoad> loads, String code) {
        return loads.stream().filter(load -> load.code().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void listsTheDoctorsFirstAndThenTheNurses() {
        List<StaffLoad> loads = new StaffLoadAnalyzer().staff(trace(), hospital());

        assertEquals(List.of("D01", "D02", "E01", "E02"), loads.stream().map(StaffLoad::code).toList());
    }

    @Test
    void measuresThePatientsAndTheOccupancyOfEachPerson() {
        List<StaffLoad> loads = new StaffLoadAnalyzer().staff(trace(), hospital());

        StaffLoad surgeon = loadOf(loads, "D02");
        assertEquals(Specialty.SURGERY, surgeon.specialty());
        assertEquals(1, surgeon.patients());
        assertEquals(1.0, surgeon.occupancy(), 1e-12);
        assertTrue(surgeon.isSaturated());

        assertEquals(0.5, loadOf(loads, "D01").occupancy(), 1e-12);
        assertEquals(1, loadOf(loads, "E02").patients());
        assertFalse(loadOf(loads, "E02").isDoctor());
        assertTrue(loadOf(loads, "E02").isIdle());
    }

    @Test
    void measuresTheLoadOfEachSpecialtyAndTheWaitForItsDoctors() {
        List<SpecialtyLoad> loads = new StaffLoadAnalyzer().specialties(result4(), trace(), hospital());

        assertEquals(List.of(Specialty.GENERAL_MEDICINE, Specialty.INTERNAL_MEDICINE, Specialty.SURGERY),
                loads.stream().map(SpecialtyLoad::specialty).toList());
        SpecialtyLoad surgery = loads.get(2);
        assertEquals(1, surgery.doctors());
        assertEquals(1, surgery.patients());
        assertEquals(1.0, surgery.averageOccupancy(), 1e-12);
        assertEquals(40, surgery.doctorWaitMinutes());
    }

    @Test
    void aSpecialtyTheHospitalLacksAppearsIfSomeoneWaitedForIt() {
        SpecialtyLoad internal = new StaffLoadAnalyzer().specialties(result4(), trace(), hospital()).get(1);

        assertEquals(0, internal.doctors());
        assertEquals(15, internal.doctorWaitMinutes());
    }

    @Test
    void needsAllTheData() {
        StaffLoadAnalyzer analyzer = new StaffLoadAnalyzer();

        assertThrows(IllegalArgumentException.class, () -> analyzer.staff(null, hospital()));
        assertThrows(IllegalArgumentException.class, () -> analyzer.specialties(null, trace(), hospital()));
        assertThrows(IllegalArgumentException.class, () -> analyzer.specialties(result4(), trace(), null));
    }
}
