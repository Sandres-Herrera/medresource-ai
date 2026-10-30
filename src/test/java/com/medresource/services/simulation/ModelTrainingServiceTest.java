package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;
import com.medresource.model.Objective;
import com.medresource.services.strategy.FeatureExtractor;
import com.medresource.services.strategy.ModelStore;
import com.medresource.services.strategy.NeuralNetwork;
import com.medresource.services.strategy.TrainedModel;
import com.medresource.services.strategy.Trainer;
import com.medresource.services.strategy.TrainingExample;
import com.medresource.services.strategy.TrainingSettings;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del entrenamiento de las redes por objetivo.
 *
 * <p>Usan parámetros diminutos (un caso de 10 pacientes) para que corran rápido.</p>
 */
class ModelTrainingServiceTest {

    private static final TrainingSettings TINY = new TrainingSettings(1, 1, 10, 2, 0.01, 4, 3, 7);

    private final ModelTrainingService service = new ModelTrainingService(HospitalConfig.standard(), TINY);

    private static double[] sample() {
        double[] features = new double[FeatureExtractor.FEATURE_COUNT];
        Arrays.fill(features, 0.5);
        return features;
    }

    private static void deleteAll(Path folder) throws IOException {
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void trainsANetworkWithTheRequestedShape() {
        TrainedModel model = service.train(Objective.MAXIMIZE_LIVES);

        assertEquals(Objective.MAXIMIZE_LIVES, model.objective());
        assertArrayEquals(new int[]{FeatureExtractor.FEATURE_COUNT, 4, 3, 1}, model.network().getLayerSizes());
    }

    @Test
    void reportsTheTrainingAndTheEvaluation() {
        TrainedModel model = service.train(Objective.EQUITY);

        assertEquals(TINY.epochs(), model.report().epochLosses().size());
        assertTrue(model.report().examples() > 0);
        assertTrue(model.trainingLoss() >= 0.0);
        assertTrue(model.evaluationLoss() >= 0.0);
        assertFalse(Double.isNaN(model.evaluationLoss()));
    }

    @Test
    void theSameSettingsGiveTheSameNetwork() {
        TrainedModel first = service.train(Objective.MAXIMIZE_LIFE_YEARS);
        TrainedModel second = service.train(Objective.MAXIMIZE_LIFE_YEARS);

        assertEquals(first.network().predict(sample()), second.network().predict(sample()), 1e-12);
        assertEquals(first.evaluationLoss(), second.evaluationLoss(), 1e-12);
    }

    @Test
    void differentObjectivesTeachDifferentNetworks() {
        double lives = service.train(Objective.MAXIMIZE_LIVES).network().predict(sample());
        double wait = service.train(Objective.MINIMIZE_WAIT).network().predict(sample());

        assertTrue(Math.abs(lives - wait) > 1e-9);
    }

    @Test
    void anotherNetworkSeedGivesAnotherNetwork() {
        TrainingSettings other = new TrainingSettings(1, 1, 10, 2, 0.01, 4, 3, 8);
        double first = service.train(Objective.EQUITY).network().predict(sample());
        double second = new ModelTrainingService(HospitalConfig.standard(), other)
                .train(Objective.EQUITY).network().predict(sample());

        assertTrue(Math.abs(first - second) > 1e-9);
    }

    @Test
    void trainsWithTheSeedTheEpochsAndTheRateOfTheSettings() {
        TrainingDataGenerator generator = new TrainingDataGenerator(HospitalConfig.standard());
        List<TrainingExample> examples = generator.generate(
                TrainingDataGenerator.seeds(TrainingDataGenerator.TRAINING_FIRST_SEED, TINY.trainingSeeds()),
                Objective.EQUITY, TINY.patientsPerCase());
        NeuralNetwork expected = new NeuralNetwork(TINY.networkSeed(),
                FeatureExtractor.FEATURE_COUNT, TINY.firstHidden(), TINY.secondHidden(), 1);
        new Trainer(TINY.networkSeed(), TINY.epochs(), TINY.learningRate()).train(expected, examples);

        TrainedModel model = service.train(Objective.EQUITY);

        assertEquals(expected.predict(sample()), model.network().predict(sample()), 1e-12);
    }

    @Test
    void evaluatesWithSeedsThatWereNotUsedToTrain() {
        TrainingDataGenerator generator = new TrainingDataGenerator(HospitalConfig.standard());
        List<TrainingExample> unseen = generator.generate(
                TrainingDataGenerator.seeds(TrainingDataGenerator.EVALUATION_FIRST_SEED, TINY.evaluationSeeds()),
                Objective.EQUITY, TINY.patientsPerCase());

        TrainedModel model = service.train(Objective.EQUITY);

        double expected = new Trainer(TINY.networkSeed(), TINY.epochs(), TINY.learningRate())
                .evaluate(model.network(), unseen);
        assertEquals(expected, model.evaluationLoss(), 1e-12);
    }

    @Test
    void trainsAndSavesOneNetworkPerObjective() throws IOException {
        Path folder = Files.createTempDirectory("modelos-");
        try {
            ModelStore store = new ModelStore(folder);

            List<TrainedModel> models = service.trainAll(store);

            assertEquals(List.of(Objective.values()), models.stream().map(TrainedModel::objective).toList());
            for (TrainedModel model : models) {
                assertTrue(store.contains(model.objective()));
                assertEquals(model.network().predict(sample()),
                        store.load(model.objective()).orElseThrow().predict(sample()), 1e-12);
            }
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class, () -> new ModelTrainingService(null, TINY));
        assertThrows(IllegalArgumentException.class, () -> new ModelTrainingService(HospitalConfig.standard(), null));
        assertThrows(IllegalArgumentException.class, () -> service.train(null));
        assertThrows(IllegalArgumentException.class, () -> service.trainAll(null));
    }
}