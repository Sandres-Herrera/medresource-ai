package com.medresource.model;

import java.util.List;

import static com.medresource.model.DiseaseFixtures.full;
import static com.medresource.model.DiseaseFixtures.simple;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Casos de ejemplo para las pruebas de acceso a datos y de servicios.
 *
 * <p>No es una prueba: arma un caso de tres pacientes con las enfermedades de {@link DiseaseFixtures}
 * y compara que dos casos tengan exactamente los mismos datos.</p>
 */
public final class CaseFixtures {

    private CaseFixtures() {
    }

    /** Enfermedades que usan los pacientes de los casos de ejemplo (identificadores 1 y 2). */
    public static List<Disease> diseases() {
        return List.of(full(1, "Neumonía"), simple(2, "Gripa"));
    }

    /** Caso de tres pacientes con la semilla dada, escenario normal y 120 minutos de duración. */
    public static SimulationCase threePatients(long seed) {
        List<Disease> diseases = diseases();
        List<PatientProfile> profiles = List.of(
                new PatientProfile(1, 30, Sex.FEMALE, BloodType.O_POSITIVE, diseases.get(0),
                        List.of(Symptom.CHEST_PAIN, Symptom.FEVER), 0, 80.5, false),
                new PatientProfile(2, 5, Sex.MALE, BloodType.AB_NEGATIVE, diseases.get(1),
                        List.of(Symptom.FEVER), 10, 100.0, false),
                new PatientProfile(3, 70, Sex.FEMALE, BloodType.A_POSITIVE, diseases.get(0),
                        List.of(Symptom.COUGH, Symptom.FEVER, Symptom.CHEST_PAIN), 10, 33.25, true));
        return new SimulationCase(new CaseConfig(seed, Scenario.NORMAL, 3, 120), profiles);
    }

    /** Comprueba que dos casos tienen la misma configuración y los mismos pacientes, en el mismo orden. */
    public static void assertSameCase(SimulationCase expected, SimulationCase actual) {
        assertEquals(expected.config(), actual.config());
        assertEquals(expected.profiles().size(), actual.profiles().size());
        for (int i = 0; i < expected.profiles().size(); i++) {
            PatientProfile wanted = expected.profiles().get(i);
            PatientProfile found = actual.profiles().get(i);
            assertEquals(wanted.id(), found.id());
            assertEquals(wanted.age(), found.age());
            assertEquals(wanted.sex(), found.sex());
            assertEquals(wanted.bloodType(), found.bloodType());
            assertEquals(wanted.disease().getId(), found.disease().getId());
            assertEquals(wanted.symptoms(), found.symptoms());
            assertEquals(wanted.arrivalMinute(), found.arrivalMinute());
            assertEquals(wanted.initialHealth(), found.initialHealth(), 0.0);
            assertEquals(wanted.irrecoverable(), found.irrecoverable());
        }
    }
}