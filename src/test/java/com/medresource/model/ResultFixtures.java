package com.medresource.model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ejecuciones de ejemplo para las pruebas de acceso a datos y de servicios.
 *
 * <p>No es una prueba: arma el resultado de los tres pacientes de {@link CaseFixtures#threePatients(long)}
 * (uno dado de alta, uno fallecido y uno sin atender), con sus eventos, y compara que dos ejecuciones
 * tengan exactamente los mismos datos.</p>
 */
public final class ResultFixtures {

    /** Hospital de ejemplo, distinto del estándar para que se note si no se guarda cada columna. */
    public static final HospitalConfig HOSPITAL = new HospitalConfig(11, 3, 2, 7, 9, 4, 6);

    private ResultFixtures() {
    }

    /** Configuración del caso de {@link CaseFixtures#threePatients(long)} con la semilla dada. */
    public static CaseConfig configOf(long seed) {
        return new CaseConfig(seed, Scenario.NORMAL, 3, 120);
    }

    /** Resultado de una estrategia sobre el caso de la semilla dada. */
    public static SimulationResult resultOf(String strategyName, long seed) {
        List<Disease> diseases = CaseFixtures.diseases();
        List<PatientOutcome> outcomes = List.of(
                new PatientOutcome(1, "P-001", 30, Sex.FEMALE, TriageLevel.II, diseases.get(0), 0, 12, 75,
                        PatientStatus.DISCHARGED, null, CompatibilityLevel.PRIMARY, false, 88.5),
                new PatientOutcome(2, "P-002", 5, Sex.MALE, TriageLevel.I, diseases.get(1), 10, 10, 40,
                        PatientStatus.DECEASED, CauseOfDeath.NO_ICU_BED, CompatibilityLevel.SECONDARY, false, 0.0),
                new PatientOutcome(3, "P-003", 70, Sex.FEMALE, TriageLevel.V, diseases.get(0), 10, -1, 130,
                        PatientStatus.UNATTENDED, null, null, true, 33.25));
        List<SimulationEvent> events = List.of(
                new SimulationEvent(0, EventType.ARRIVAL, "P-001", null, "Llega P-001"),
                new SimulationEvent(12, EventType.TREATMENT_STARTED, "P-001", "DOC-1", "Inicia el tratamiento"),
                new SimulationEvent(40, EventType.DEATH, "P-002", null, "Fallece P-002"),
                new SimulationEvent(75, EventType.DISCHARGE, "P-001", "BED-3", "Alta de P-001"));
        return new SimulationResult(strategyName, configOf(seed), 130, outcomes, events);
    }

    /** Ejecución de ejemplo sobre el caso guardado con el identificador dado. */
    public static RunRecord runOf(int caseId, String strategyName, long seed) {
        return new RunRecord(caseId, HOSPITAL, resultOf(strategyName, seed));
    }

    /** Comprueba que dos ejecuciones tienen los mismos recursos, resultados por paciente y eventos. */
    public static void assertSameRun(RunRecord expected, RunRecord actual) {
        assertEquals(expected.caseId(), actual.caseId());
        assertEquals(expected.hospital(), actual.hospital());
        SimulationResult wanted = expected.result();
        SimulationResult found = actual.result();
        assertEquals(wanted.strategyName(), found.strategyName());
        assertEquals(wanted.config(), found.config());
        assertEquals(wanted.totalMinutes(), found.totalMinutes());
        assertEquals(wanted.outcomes().size(), found.outcomes().size());
        for (int i = 0; i < wanted.outcomes().size(); i++) {
            PatientOutcome a = wanted.outcomes().get(i);
            PatientOutcome b = found.outcomes().get(i);
            assertEquals(a.patientId(), b.patientId());
            assertEquals(a.patientCode(), b.patientCode());
            assertEquals(a.age(), b.age());
            assertEquals(a.sex(), b.sex());
            assertEquals(a.triage(), b.triage());
            assertEquals(a.disease().getId(), b.disease().getId());
            assertEquals(a.arrivalMinute(), b.arrivalMinute());
            assertEquals(a.treatmentStartMinute(), b.treatmentStartMinute());
            assertEquals(a.outcomeMinute(), b.outcomeMinute());
            assertEquals(a.status(), b.status());
            assertEquals(a.causeOfDeath(), b.causeOfDeath());
            assertEquals(a.careLevel(), b.careLevel());
            assertEquals(a.irrecoverable(), b.irrecoverable());
            assertEquals(a.finalHealth(), b.finalHealth(), 0.0);
        }
        assertEquals(wanted.events(), found.events());
    }
}