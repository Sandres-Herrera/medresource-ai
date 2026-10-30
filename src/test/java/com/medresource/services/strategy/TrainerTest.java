package com.medresource.services.strategy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del entrenador de la red neuronal.
 */
class TrainerTest {

    private static final double DELTA = 1e-12;

    /** Ejemplos fáciles de aprender: el resultado es la primera característica. */
    private static List<TrainingExample> learnableExamples() {
        List<TrainingExample> examples = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            double value = i / 10.0;
            examples.add(new TrainingExample(new double[]{value, 1.0 - value}, value));
        }
        return examples;
    }

    private static NeuralNetwork network() {
        return new NeuralNetwork(11, 2, 6, 1);
    }

    // ---------- construcción ----------

    @Test
    void rejectsAnInvalidNumberOfEpochs() {
        assertThrows(IllegalArgumentException.class, () -> new Trainer(1, 0, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Trainer(1, -3, 0.1));
        new Trainer(1, 1, 0.1);
    }

    @Test
    void rejectsAnInvalidLearningRate() {
        assertThrows(IllegalArgumentException.class, () -> new Trainer(1, 5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Trainer(1, 5, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new Trainer(1, 5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Trainer(1, 5, Double.POSITIVE_INFINITY));
    }

    // ---------- entrenar ----------

    @Test
    void reportsOneLossPerEpochAndTheNumberOfExamples() {
        TrainingReport report = new Trainer(1, 7, 0.05).train(network(), learnableExamples());

        assertEquals(7, report.epochLosses().size());
        assertEquals(11, report.examples());
    }

    @Test
    void theErrorGoesDownWhileLearning() {
        TrainingReport report = new Trainer(1, 300, 0.05).train(network(), learnableExamples());

        assertTrue(report.lastLoss() < report.firstLoss() / 3,
                "primera " + report.firstLoss() + ", última " + report.lastLoss());
    }

    @Test
    void theTrainedNetworkPredictsTheExamples() {
        NeuralNetwork network = network();

        new Trainer(1, 600, 0.05).train(network, learnableExamples());

        assertEquals(0.9, network.predict(new double[]{0.9, 0.1}), 0.1);
        assertEquals(0.1, network.predict(new double[]{0.1, 0.9}), 0.1);
    }

    @Test
    void theFirstEpochLossOfOneExampleIsTheErrorBeforeLearning() {
        NeuralNetwork network = network();
        double[] input = {0.8, 0.2};
        double before = network.predict(input);

        TrainingReport report = new Trainer(1, 1, 0.05).train(network, List.of(new TrainingExample(input, 0.3)));

        assertEquals(0.5 * (before - 0.3) * (before - 0.3), report.firstLoss(), DELTA);
        assertEquals(1, report.examples());
    }

    @Test
    void theLossOfAnEpochIsTheAverageOverAllExamples() {
        NeuralNetwork network = network();
        double[] first = {0.8, 0.2};
        double[] second = {0.1, 0.9};
        double lossFirst = 0.5 * Math.pow(network.predict(first) - 0.3, 2);
        // Con tasa casi nula la red no cambia entre ejemplos y la época promedia los dos errores.
        double lossSecond = 0.5 * Math.pow(network.predict(second) - 0.6, 2);

        TrainingReport report = new Trainer(1, 1, 1e-12).train(network,
                List.of(new TrainingExample(first, 0.3), new TrainingExample(second, 0.6)));

        assertEquals((lossFirst + lossSecond) / 2, report.firstLoss(), 1e-9);
    }

    @Test
    void theSameSeedLeavesTheSameNetwork() {
        NeuralNetwork first = network();
        NeuralNetwork second = network();

        new Trainer(5, 20, 0.05).train(first, learnableExamples());
        new Trainer(5, 20, 0.05).train(second, learnableExamples());

        double[] input = {0.3, 0.7};
        assertEquals(first.predict(input), second.predict(input));
    }

    @Test
    void theSeedChangesTheOrderAndSoTheResult() {
        NeuralNetwork first = network();
        NeuralNetwork second = network();

        new Trainer(5, 20, 0.05).train(first, learnableExamples());
        new Trainer(6, 20, 0.05).train(second, learnableExamples());

        double[] input = {0.3, 0.7};
        assertNotEquals(first.predict(input), second.predict(input));
    }

    @Test
    void doesNotChangeTheListOfExamples() {
        List<TrainingExample> examples = List.copyOf(learnableExamples());

        new Trainer(1, 3, 0.05).train(network(), examples);

        assertEquals(learnableExamples().size(), examples.size());
        assertEquals(0.0, examples.get(0).target());
    }

    @Test
    void rejectsInvalidInputsWhenTraining() {
        Trainer trainer = new Trainer(1, 2, 0.05);
        List<TrainingExample> wrongSize = List.of(new TrainingExample(new double[]{0.1, 0.2, 0.3}, 0.5));
        List<TrainingExample> tooShort = List.of(new TrainingExample(new double[]{0.1}, 0.5));
        List<TrainingExample> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(IllegalArgumentException.class, () -> trainer.train(null, learnableExamples()));
        assertThrows(IllegalArgumentException.class, () -> trainer.train(network(), null));
        assertThrows(IllegalArgumentException.class, () -> trainer.train(network(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> trainer.train(network(), wrongSize));
        assertThrows(IllegalArgumentException.class, () -> trainer.train(network(), tooShort));
        assertThrows(IllegalArgumentException.class, () -> trainer.train(network(), withNull));
    }
}