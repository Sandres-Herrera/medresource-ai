package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del caso de simulación y de los datos de sus pacientes.
 */
class SimulationCaseTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();
    private static final Disease BRONCHIOLITIS = DiseaseCatalog.findById(3).orElseThrow();

    private PatientProfile profile(int id, int arrivalMinute) {
        return new PatientProfile(id, 30, Sex.FEMALE, BloodType.A_POSITIVE, FLU,
                FLU.getSymptoms(), arrivalMinute, 90.0, false);
    }

    private SimulationCase caseOf(int count) {
        List<PatientProfile> profiles = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            profiles.add(profile(i, i * 2));
        }
        return new SimulationCase(new CaseConfig(1L, Scenario.NORMAL, count, 480), profiles);
    }

    @Test
    void createsPatientsWithTheProfileData() {
        Patient patient = profile(3, 15).toPatient();

        assertEquals("P003", patient.getCode());
        assertEquals(30, patient.getAge());
        assertEquals(15, patient.getArrivalMinute());
        assertEquals(PatientStatus.WAITING, patient.getStatus());
    }

    @Test
    void everyRunGetsItsOwnPatients() {
        SimulationCase simulationCase = caseOf(5);
        List<Patient> first = simulationCase.createPatients();
        List<Patient> second = simulationCase.createPatients();

        first.get(0).startTreatment();
        first.get(0).deteriorate(40);

        assertEquals(PatientStatus.WAITING, second.get(0).getStatus());
        assertEquals(90.0, second.get(0).getHealth());
    }

    @Test
    void patientsKeepTheArrivalOrder() {
        List<Patient> patients = caseOf(5).createPatients();

        assertEquals(5, patients.size());
        assertEquals("P001", patients.get(0).getCode());
        assertEquals("P005", patients.get(4).getCode());
    }

    @Test
    void rejectsAProfileThatAPatientCouldNotHave() {
        assertThrows(IllegalArgumentException.class, () -> new PatientProfile(1, 50, Sex.MALE,
                BloodType.O_POSITIVE, BRONCHIOLITIS, BRONCHIOLITIS.getSymptoms(), 0, 90.0, false));
    }

    @Test
    void rejectsADifferentNumberOfPatientsThanTheConfiguration() {
        CaseConfig config = new CaseConfig(1L, Scenario.NORMAL, 3, 480);

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationCase(config, List.of(profile(1, 0), profile(2, 5))));
    }

    @Test
    void rejectsPatientsOutOfArrivalOrder() {
        CaseConfig config = new CaseConfig(1L, Scenario.NORMAL, 2, 480);

        assertThrows(IllegalArgumentException.class,
                () -> new SimulationCase(config, List.of(profile(1, 20), profile(2, 5))));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationCase(null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationCase(new CaseConfig(1L, Scenario.NORMAL, 1, 480), null));
    }

    @Test
    void casesWithDifferentPatientsAreNotEqual() {
        assertNotEquals(caseOf(3), caseOf(4));
    }
}