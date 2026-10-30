package com.medresource.services.strategy;

import com.medresource.model.BloodType;
import com.medresource.model.Disease;
import com.medresource.model.Hospital;
import com.medresource.model.Objective;
import com.medresource.model.Patient;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.medresource.services.strategy.StrategyFixtures.APPENDICITIS;
import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.OPEN_FRACTURE;
import static com.medresource.services.strategy.StrategyFixtures.disease;
import static com.medresource.services.strategy.StrategyFixtures.hospital;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la estrategia de inteligencia artificial.
 *
 * <p>Se prueba con redes escritas a mano, de una sola neurona, que puntúan según una
 * característica elegida. Así se sabe exactamente a quién debe elegir la estrategia sin
 * depender de ningún entrenamiento.</p>
 */
class OptimizationStrategyTest {

    /** Red de una neurona: puntuación = sigmoide(sesgo + peso · característica elegida). */
    private static NeuralNetwork networkScoring(String feature, double weight) throws IOException {
        StringBuilder row = new StringBuilder("0.0");
        for (String name : FeatureExtractor.FEATURE_NAMES) {
            row.append(' ').append(name.equals(feature) ? weight : 0.0);
        }
        return networkFromRows(FeatureExtractor.FEATURE_COUNT + " 1", row.toString());
    }

    private static NeuralNetwork networkFromRows(String sizes, String... rows) throws IOException {
        Path file = Files.createTempFile("red-", ".txt");
        try {
            List<String> lines = new ArrayList<>(List.of("MEDRESOURCE-NEURAL-NETWORK 1", sizes));
            Collections.addAll(lines, rows);
            Files.write(file, lines);
            return NeuralNetwork.load(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Paciente con apendicitis: cuanto menos salud tiene, antes moriría si no lo atienden. */
    private static WaitingPatient appendicitis(int id, int arrival, TriageLevel triage, double health) {
        Disease disease = disease(APPENDICITIS);
        Patient patient = new Patient(id, 30, Sex.MALE, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                arrival, health, false);
        patient.assignTriage(triage);
        return new WaitingPatient(patient, disease);
    }

    /** Paciente con gripa: no corre peligro de morir pronto, así que su urgencia es 0. */
    private static WaitingPatient flu(int id, int arrival, TriageLevel triage) {
        Disease disease = disease(GENERAL_FLU);
        Patient patient = new Patient(id, 30, Sex.MALE, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                arrival, 80.0, false);
        patient.assignTriage(triage);
        return new WaitingPatient(patient, disease);
    }

    private static int firstChoice(AllocationStrategy strategy, WaitingPatient... waiting) {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE, Specialty.SURGERY);
        return strategy.nextAssignment(10, List.of(waiting), hospital).orElseThrow().patient().getId();
    }

    // ---------- decisión ----------

    @Test
    void servesThePatientTheNetworkScoresHighest() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 10.0), Objective.MAXIMIZE_LIVES);

        assertEquals(2, firstChoice(strategy,
                appendicitis(1, 0, TriageLevel.IV, 60.0),
                appendicitis(2, 5, TriageLevel.I, 60.0),
                appendicitis(3, 1, TriageLevel.III, 60.0)));
    }

    @Test
    void theNetworkDecidesNotAFixedRule() throws IOException {
        OptimizationStrategy lessUrgentFirst = new OptimizationStrategy(networkScoring("urgency", -10.0), Objective.EQUITY);

        assertEquals(1, firstChoice(lessUrgentFirst,
                appendicitis(1, 5, TriageLevel.IV, 60.0),
                appendicitis(2, 0, TriageLevel.I, 60.0)));
    }

    @Test
    void servesFirstWhoWouldDieSoonerWhenTheNetworkSeesNoDifference() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 0.0), Objective.MAXIMIZE_LIVES);

        assertEquals(2, firstChoice(strategy,
                appendicitis(1, 0, TriageLevel.III, 90.0),
                appendicitis(2, 5, TriageLevel.III, 30.0)));
    }

    @Test
    void aPatientWhoCanWaitIsServedAfterOneWhoCannot() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 0.0), Objective.MAXIMIZE_LIVES);

        assertEquals(2, firstChoice(strategy,
                flu(1, 0, TriageLevel.V),
                appendicitis(2, 8, TriageLevel.III, 90.0)));
    }

    @Test
    void weighsTheNetworkScoreByHowSoonThePatientWouldDie() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 10.0), Objective.MAXIMIZE_LIVES);
        WaitingPatient highScoreButCanWait = appendicitis(1, 0, TriageLevel.I, 90.0);
        WaitingPatient lowerScoreButUrgent = appendicitis(2, 1, TriageLevel.V, 20.0);

        assertEquals(2, firstChoice(strategy, highScoreButCanWait, lowerScoreButUrgent));
    }

    @Test
    void breaksTiesByArrivalAndThenByNumber() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 0.0), Objective.MINIMIZE_WAIT);

        assertEquals(2, firstChoice(strategy,
                appendicitis(3, 4, TriageLevel.III, 60.0),
                appendicitis(2, 1, TriageLevel.III, 60.0),
                appendicitis(1, 4, TriageLevel.III, 60.0)));
        assertEquals(1, firstChoice(strategy,
                appendicitis(2, 1, TriageLevel.III, 60.0),
                appendicitis(1, 1, TriageLevel.III, 60.0)));
    }

    @Test
    void skipsTheTopPatientWhenTheirResourcesAreMissing() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 10.0), Objective.MAXIMIZE_LIVES);
        Hospital withoutBlood = hospital(3, 0, 1, Specialty.GENERAL_MEDICINE, Specialty.TRAUMATOLOGY);
        WaitingPatient fracture = StrategyFixtures.waiting(1, 0, OPEN_FRACTURE, 30);
        fracture.patient().assignTriage(TriageLevel.I);
        WaitingPatient flu = flu(2, 1, TriageLevel.V);

        Assignment assignment = strategy.nextAssignment(10, List.of(fracture, flu), withoutBlood).orElseThrow();

        assertEquals(2, assignment.patient().getId());
    }

    @Test
    void returnsNothingWhenThereIsNobodyToServe() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 10.0), Objective.MAXIMIZE_LIVES);

        assertTrue(strategy.nextAssignment(10, List.of(), hospital(Specialty.GENERAL_MEDICINE)).isEmpty());
    }

    @Test
    void theSameInputsGiveTheSameDecision() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 3.0), Objective.EQUITY);
        WaitingPatient first = appendicitis(1, 0, TriageLevel.II, 70.0);
        WaitingPatient second = appendicitis(2, 3, TriageLevel.III, 60.0);

        assertEquals(firstChoice(strategy, first, second), firstChoice(strategy, first, second));
    }

    @Test
    void rejectsANullWaitingListOrHospital() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 1.0), Objective.EQUITY);

        assertThrows(IllegalArgumentException.class,
                () -> strategy.nextAssignment(0, null, hospital(Specialty.GENERAL_MEDICINE)));
        assertThrows(IllegalArgumentException.class, () -> strategy.nextAssignment(0, List.of(), null));
    }

    // ---------- datos de la estrategia ----------

    @Test
    void namesTheObjectiveItWasTrainedFor() throws IOException {
        OptimizationStrategy strategy = new OptimizationStrategy(networkScoring("urgency", 1.0), Objective.EQUITY);

        assertEquals(Objective.EQUITY, strategy.getObjective());
        assertEquals("Inteligencia artificial (Equidad entre pacientes)", strategy.getName());
    }

    // ---------- argumentos ----------

    @Test
    void rejectsMissingArguments() throws IOException {
        NeuralNetwork network = networkScoring("urgency", 1.0);

        assertThrows(IllegalArgumentException.class, () -> new OptimizationStrategy(null, Objective.EQUITY));
        assertThrows(IllegalArgumentException.class, () -> new OptimizationStrategy(network, null));
        assertThrows(IllegalArgumentException.class,
                () -> new OptimizationStrategy(network, Objective.EQUITY, null));
    }

    @Test
    void rejectsANetworkWithTheWrongNumberOfInputs() throws IOException {
        NeuralNetwork tooSmall = networkFromRows("2 1", "0.0 1.0 1.0");

        assertThrows(IllegalArgumentException.class, () -> new OptimizationStrategy(tooSmall, Objective.EQUITY));
    }
}