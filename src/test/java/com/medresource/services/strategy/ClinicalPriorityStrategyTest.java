package com.medresource.services.strategy;

import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Patient;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationResult;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.medresource.services.strategy.StrategyFixtures.DENGUE;
import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.HEART_ATTACK;
import static com.medresource.services.strategy.StrategyFixtures.disease;
import static com.medresource.services.strategy.StrategyFixtures.hospital;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la estrategia de prioridad clínica.
 */
class ClinicalPriorityStrategyTest {

    private final ClinicalPriorityStrategy strategy = new ClinicalPriorityStrategy();

    /** Paciente en espera con gripa, triage y salud elegidos; el diagnóstico coincide con la enfermedad. */
    private static WaitingPatient flu(int id, int arrival, TriageLevel triage, double health, int age, Sex sex) {
        Disease disease = disease(GENERAL_FLU);
        Patient patient = new Patient(id, age, sex, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                arrival, health, false);
        patient.assignTriage(triage);
        return new WaitingPatient(patient, disease);
    }

    private static WaitingPatient flu(int id, int arrival, TriageLevel triage, double health) {
        return flu(id, arrival, triage, health, 30, Sex.MALE);
    }

    private int firstChoice(int minute, WaitingPatient... waiting) {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        return strategy.nextAssignment(minute, List.of(waiting), hospital).orElseThrow().patient().getId();
    }

    @Test
    void servesTheMostUrgentTriageFirstEvenIfItArrivedLater() {
        assertEquals(2, firstChoice(10,
                flu(1, 0, TriageLevel.IV, 80.0),
                flu(2, 5, TriageLevel.I, 80.0)));
    }

    @Test
    void ordersAllTheTriageLevels() {
        assertEquals(3, firstChoice(10,
                flu(1, 0, TriageLevel.V, 80.0),
                flu(2, 0, TriageLevel.III, 80.0),
                flu(3, 0, TriageLevel.II, 80.0),
                flu(4, 0, TriageLevel.IV, 80.0)));
    }

    @Test
    void amongTheSameTriageTheSickestGoesFirst() {
        assertEquals(2, firstChoice(10,
                flu(1, 0, TriageLevel.III, 70.0),
                flu(2, 5, TriageLevel.III, 40.0)));
    }

    @Test
    void withTheSameTriageAndHealthTheEarliestArrivalGoesFirst() {
        assertEquals(2, firstChoice(30,
                flu(1, 8, TriageLevel.III, 60.0),
                flu(2, 3, TriageLevel.III, 60.0)));
    }

    @Test
    void withEverythingEqualTheLowestNumberGoesFirst() {
        assertEquals(1, firstChoice(30,
                flu(2, 5, TriageLevel.III, 60.0),
                flu(1, 5, TriageLevel.III, 60.0)));
    }

    @Test
    void ageAndSexDoNotChangeThePriority() {
        // Misma gravedad: decide la llegada, aunque el primero sea un adulto mayor y la segunda una niña.
        assertEquals(1, firstChoice(30,
                flu(1, 0, TriageLevel.III, 60.0, 80, Sex.MALE),
                flu(2, 5, TriageLevel.III, 60.0, 3, Sex.FEMALE)));
    }

    @Test
    void aLongWaitRaisesThePriorityOneLevelAtATime() {
        // Triage V con 2 niveles de espera (240 min) equivale a III; el III recién llegado conserva su ventaja.
        assertEquals(2, firstChoice(240,
                flu(1, 0, TriageLevel.V, 80.0),
                flu(2, 200, TriageLevel.III, 80.0)));
        // Con 3 niveles de espera (360 min) el V equivale a II y pasa por delante de un III.
        assertEquals(1, firstChoice(360,
                flu(1, 0, TriageLevel.V, 80.0),
                flu(2, 300, TriageLevel.III, 80.0)));
    }

    @Test
    void lessThanOneAgingPeriodDoesNotPromote() {
        // 119 minutos de espera no suben el nivel: el IV sigue detrás del III.
        assertEquals(2, firstChoice(119,
                flu(1, 0, TriageLevel.IV, 80.0),
                flu(2, 100, TriageLevel.III, 80.0)));
    }

    @Test
    void exactlyOneAgingPeriodPromotesOneLevel() {
        // Dos triage IV: sin ascenso decidiría la salud (el de 30 iría primero); con 120 minutos de espera
        // el primero sube a nivel efectivo III y pasa por delante.
        assertEquals(1, firstChoice(120,
                flu(1, 0, TriageLevel.IV, 80.0),
                flu(2, 100, TriageLevel.IV, 30.0)));
    }

    @Test
    void waitingNeverRaisesAPatientAboveTheMostUrgentOne() {
        // Un V con muchísima espera no pasa por delante de un triage I recién llegado.
        assertEquals(2, firstChoice(2000,
                flu(1, 0, TriageLevel.V, 80.0),
                flu(2, 1990, TriageLevel.I, 80.0)));
    }

    @Test
    void whenTheEffectiveLevelTiesTheRealTriageDecides() {
        // El V con 4 niveles de espera equivale a I, pero el I verdadero va primero.
        assertEquals(2, firstChoice(480,
                flu(1, 0, TriageLevel.V, 20.0),
                flu(2, 470, TriageLevel.I, 90.0)));
    }

    @Test
    void servesALowerPriorityPatientWhenTheFirstCannotBeServed() {
        // El infarto es el más urgente pero no hay internista; se atiende la gripa.
        Disease heartAttack = disease(HEART_ATTACK);
        Patient critical = new Patient(1, 60, Sex.MALE, BloodType.O_POSITIVE, heartAttack,
                heartAttack.getSymptoms(), 0, 40.0, false);
        critical.assignTriage(TriageLevel.I);
        WaitingPatient infarct = new WaitingPatient(critical, heartAttack);

        assertEquals(2, firstChoice(10, infarct, flu(2, 5, TriageLevel.V, 90.0)));
    }

    @Test
    void usesTheDiagnosisToChooseResourcesNotToOrder() {
        // Dengue (hospitalización) con triage I va antes que la gripa con triage V.
        Disease dengue = disease(DENGUE);
        Patient patient = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, dengue, dengue.getSymptoms(),
                0, 80.0, false);
        patient.assignTriage(TriageLevel.I);
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE, Specialty.GENERAL_MEDICINE);

        Assignment assignment = strategy.nextAssignment(10,
                        List.of(flu(2, 0, TriageLevel.V, 80.0), new WaitingPatient(patient, dengue)), hospital)
                .orElseThrow();

        assertEquals(1, assignment.patient().getId());
        assertTrue(assignment.bed() != null);
    }

    @Test
    void failsIfAPatientHasNoTriage() {
        Disease disease = disease(GENERAL_FLU);
        Patient unclassified = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                0, 80.0, false);
        List<WaitingPatient> waiting = List.of(new WaitingPatient(unclassified, disease),
                flu(2, 0, TriageLevel.III, 80.0));
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);

        assertThrows(IllegalStateException.class, () -> strategy.nextAssignment(10, waiting, hospital));
    }

    @Test
    void worksInsideTheEngineForEveryScenario() {
        for (Scenario scenario : Scenario.values()) {
            SimulationResult result = new SimulationEngine(
                    new CaseGenerator().generate(new CaseConfig(11L, scenario, 100, 480)),
                    new HospitalFactory().create(HospitalConfig.standard()), strategy).run();

            assertEquals(100, result.outcomes().size());
            assertTrue(result.outcomes().stream().allMatch(outcome -> outcome.status().isFinal()));
            assertEquals(strategy.getName(), result.strategyName());
        }
    }

    @Test
    void hasAReadableName() {
        assertFalse(strategy.getName().isBlank());
        assertTrue(strategy.getName().startsWith("Prioridad clínica"));
    }
}