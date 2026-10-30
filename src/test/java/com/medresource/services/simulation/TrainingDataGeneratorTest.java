package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Objective;
import com.medresource.model.PatientOutcome;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.services.strategy.FeatureExtractor;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.OutcomeScorer;
import com.medresource.services.strategy.RecordingStrategy;
import com.medresource.services.strategy.TrainingExample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del generador de datos de entrenamiento.
 */
class TrainingDataGeneratorTest {

    private static final int PATIENTS = 20;
    private static final int STRATEGIES = 3;

    private final TrainingDataGenerator generator = new TrainingDataGenerator(HospitalConfig.standard());

    private static double mean(List<TrainingExample> examples) {
        return examples.stream().mapToDouble(TrainingExample::target).average().orElseThrow();
    }

    // ---------- semillas ----------

    @Test
    void buildsConsecutiveSeeds() {
        assertEquals(List.of(5L, 6L, 7L), TrainingDataGenerator.seeds(5, 3));
        assertEquals(List.of(9L), TrainingDataGenerator.seeds(9, 1));
    }

    @Test
    void rejectsAnEmptyListOfSeeds() {
        assertThrows(IllegalArgumentException.class, () -> TrainingDataGenerator.seeds(1, 0));
        assertThrows(IllegalArgumentException.class, () -> TrainingDataGenerator.seeds(1, -2));
    }

    @Test
    void trainingAndEvaluationSeedsNeverOverlap() {
        List<Long> training = TrainingDataGenerator.seeds(TrainingDataGenerator.TRAINING_FIRST_SEED, 100_000);
        List<Long> evaluation = TrainingDataGenerator.seeds(TrainingDataGenerator.EVALUATION_FIRST_SEED, 1000);

        for (long seed : evaluation) {
            assertFalse(training.contains(seed), "semilla repetida " + seed);
        }
    }

    // ---------- construcción ----------

    @Test
    void rejectsAMissingHospitalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingDataGenerator(null));
    }

    // ---------- generar ----------

    @Test
    void generatesOneExamplePerPatientScenarioAndStrategy() {
        List<TrainingExample> examples = generator.generate(List.of(1L), Objective.MAXIMIZE_LIVES, PATIENTS);

        assertEquals(PATIENTS * Scenario.values().length * STRATEGIES, examples.size());
    }

    @Test
    void theFirstExamplesMatchASimulationDoneByHand() {
        Objective objective = Objective.MAXIMIZE_LIFE_YEARS;
        SimulationCase simulationCase = new CaseGenerator().generate(new CaseConfig(1, Scenario.values()[0],
                PATIENTS, CaseConfig.DEFAULT_DURATION_MINUTES));
        RecordingStrategy recorder = new RecordingStrategy(new FifoStrategy(), new FeatureExtractor());
        SimulationResult result = new SimulationEngine(simulationCase,
                new HospitalFactory().create(HospitalConfig.standard()), recorder).run();
        List<TrainingExample> expected = new ArrayList<>();
        for (PatientOutcome outcome : result.outcomes()) {
            Optional<double[]> features = recorder.featuresFor(outcome.patientCode());
            features.ifPresent(values -> expected.add(
                    new TrainingExample(values, new OutcomeScorer().score(outcome, objective))));
        }

        List<TrainingExample> generated = generator.generate(List.of(1L), objective, PATIENTS);

        assertFalse(expected.isEmpty());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i).features(), generated.get(i).features());
            assertEquals(expected.get(i).target(), generated.get(i).target());
        }
    }

    @Test
    void moreSeedsGiveMoreExamples() {
        List<TrainingExample> one = generator.generate(List.of(1L), Objective.MAXIMIZE_LIVES, PATIENTS);
        List<TrainingExample> two = generator.generate(List.of(1L, 2L), Objective.MAXIMIZE_LIVES, PATIENTS);

        assertEquals(2 * one.size(), two.size());
    }

    @Test
    void everyExampleHasTheFeaturesOfTheExtractor() {
        List<TrainingExample> examples = generator.generate(List.of(1L), Objective.MAXIMIZE_LIVES, PATIENTS);

        for (TrainingExample example : examples) {
            assertEquals(FeatureExtractor.FEATURE_COUNT, example.features().length);
        }
    }

    @Test
    void theSameSeedsGiveTheSameExamples() {
        List<TrainingExample> first = generator.generate(List.of(3L), Objective.EQUITY, PATIENTS);
        List<TrainingExample> second = generator.generate(List.of(3L), Objective.EQUITY, PATIENTS);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertArrayEquals(first.get(i).features(), second.get(i).features());
            assertEquals(first.get(i).target(), second.get(i).target());
        }
    }

    @Test
    void differentSeedsGiveDifferentExamples() {
        List<TrainingExample> first = generator.generate(List.of(1L), Objective.MAXIMIZE_LIFE_YEARS, PATIENTS);
        List<TrainingExample> second = generator.generate(List.of(2L), Objective.MAXIMIZE_LIFE_YEARS, PATIENTS);

        assertNotEquals(mean(first), mean(second));
    }

    @Test
    void theObjectiveChangesTheTargetsButNotTheFeatures() {
        List<TrainingExample> lives = generator.generate(List.of(1L), Objective.MAXIMIZE_LIVES, PATIENTS);
        List<TrainingExample> years = generator.generate(List.of(1L), Objective.MAXIMIZE_LIFE_YEARS, PATIENTS);

        assertEquals(lives.size(), years.size());
        for (int i = 0; i < lives.size(); i++) {
            assertArrayEquals(lives.get(i).features(), years.get(i).features());
        }
        assertNotEquals(mean(lives), mean(years));
    }

    @Test
    void livesScoreHigherThanLifeYearsBecauseNobodyIsWorthAFullHundred() {
        double lives = mean(generator.generate(List.of(1L), Objective.MAXIMIZE_LIVES, PATIENTS));
        double years = mean(generator.generate(List.of(1L), Objective.MAXIMIZE_LIFE_YEARS, PATIENTS));

        assertTrue(lives > years);
        assertTrue(lives > 0.5 && lives <= 1.0);
    }

    @Test
    void patientsReferredElsewhereNeverWaitSoTheyGiveNoExample() {
        TrainingDataGenerator tiny = new TrainingDataGenerator(new HospitalConfig(1, 1, 1, 1, 1, 0, 1));

        List<TrainingExample> standard = generator.generate(List.of(1L), Objective.MAXIMIZE_LIVES, PATIENTS);
        List<TrainingExample> small = tiny.generate(List.of(1L), Objective.MAXIMIZE_LIVES, PATIENTS);

        assertTrue(small.size() < standard.size());
    }

    @Test
    void aSmallHospitalLosesMoreOfItsPatientsThanTheStandardOne() {
        TrainingDataGenerator tiny = new TrainingDataGenerator(new HospitalConfig(1, 1, 1, 1, 1, 0, 1));

        double standard = mean(generator.generate(TrainingDataGenerator.seeds(1, 3), Objective.MAXIMIZE_LIVES, 100));
        double small = mean(tiny.generate(TrainingDataGenerator.seeds(1, 3), Objective.MAXIMIZE_LIVES, 100));

        assertTrue(small < standard, "pequeño " + small + ", estándar " + standard);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null, Objective.EQUITY, PATIENTS));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of(), Objective.EQUITY, PATIENTS));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of(1L), null, PATIENTS));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(List.of(1L), Objective.EQUITY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(List.of(1L), Objective.EQUITY, CaseConfig.MAX_PATIENTS + 1));
    }
}