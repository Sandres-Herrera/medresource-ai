package com.medresource.services.strategy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la red neuronal.
 */
class NeuralNetworkTest {

    private static final double[][] XOR_INPUTS = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
    private static final double[] XOR_TARGETS = {0, 1, 1, 0};

    private static void assertClose(double expected, double actual, double tolerance) {
        assertTrue(Math.abs(expected - actual) <= tolerance, "esperado " + expected + " pero fue " + actual);
    }

    private static NeuralNetwork trainedXor() {
        NeuralNetwork network = new NeuralNetwork(7, 2, 6, 1);
        for (int epoch = 0; epoch < 6000; epoch++) {
            for (int i = 0; i < XOR_INPUTS.length; i++) {
                network.train(XOR_INPUTS[i], XOR_TARGETS[i], 0.3);
            }
        }
        return network;
    }

    // ---------- creación ----------

    @Test
    void layerSizesAreKept() {
        NeuralNetwork network = new NeuralNetwork(1, 14, 16, 8, 1);

        assertTrue(Arrays.equals(new int[]{14, 16, 8, 1}, network.getLayerSizes()));
        assertEquals(14, network.getInputSize());
    }

    @Test
    void layerSizesCannotBeModifiedFromOutside() {
        NeuralNetwork network = new NeuralNetwork(1, 3, 2, 1);

        network.getLayerSizes()[0] = 99;

        assertEquals(3, network.getInputSize());
    }

    @Test
    void theArrayGivenToTheConstructorIsCopied() {
        int[] sizes = {3, 2, 1};
        NeuralNetwork network = new NeuralNetwork(1, sizes);

        sizes[0] = 99;

        assertEquals(3, network.getInputSize());
    }

    @Test
    void initialWeightsHaveTheScaleOfHeInitialization() {
        // Con 200 entradas, la varianza esperada de cada peso es 2 / 200 = 0,01.
        NeuralNetwork network = new NeuralNetwork(5, 200, 50, 1);
        double sumOfSquares = 0.0;
        for (int neuron = 0; neuron < 50; neuron++) {
            for (int input = 0; input < 200; input++) {
                double weight = network.weight(0, neuron, input);
                sumOfSquares += weight * weight;
            }
        }

        assertClose(0.01, sumOfSquares / (50 * 200), 0.0015);
    }

    @Test
    void invalidLayerSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new NeuralNetwork(1));
        assertThrows(IllegalArgumentException.class, () -> new NeuralNetwork(1, 1));
        assertThrows(IllegalArgumentException.class, () -> new NeuralNetwork(1, 3, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new NeuralNetwork(1, 3, -2, 1));
        assertThrows(IllegalArgumentException.class, () -> new NeuralNetwork(1, 3, 4, 2));
    }

    // ---------- predict ----------

    @Test
    void theSameSeedBuildsTheSameNetwork() {
        NeuralNetwork first = new NeuralNetwork(42, 4, 5, 1);
        NeuralNetwork second = new NeuralNetwork(42, 4, 5, 1);
        double[] inputs = {0.1, 0.5, 0.9, 0.3};

        assertEquals(first.predict(inputs), second.predict(inputs));
    }

    @Test
    void differentSeedsBuildDifferentNetworks() {
        NeuralNetwork first = new NeuralNetwork(1, 4, 5, 1);
        NeuralNetwork second = new NeuralNetwork(2, 4, 5, 1);
        double[] inputs = {0.1, 0.5, 0.9, 0.3};

        assertNotEquals(first.predict(inputs), second.predict(inputs));
    }

    @Test
    void outputIsAlwaysBetweenZeroAndOne() {
        NeuralNetwork network = new NeuralNetwork(3, 5, 8, 4, 1);
        Random random = new Random(9);

        for (int i = 0; i < 500; i++) {
            double[] inputs = new double[5];
            for (int k = 0; k < inputs.length; k++) {
                inputs[k] = random.nextDouble() * 20 - 10;
            }
            double output = network.predict(inputs);
            assertTrue(output >= 0.0 && output <= 1.0, "salida fuera de rango: " + output);
        }
    }

    @Test
    void predictingDoesNotChangeTheNetwork() {
        NeuralNetwork network = new NeuralNetwork(3, 2, 4, 1);
        double[] inputs = {0.4, 0.8};

        assertEquals(network.predict(inputs), network.predict(inputs));
    }

    @Test
    void predictionDoesNotModifyItsInputs() {
        NeuralNetwork network = new NeuralNetwork(3, 2, 4, 1);
        double[] inputs = {0.4, 0.8};

        network.predict(inputs);

        assertTrue(Arrays.equals(new double[]{0.4, 0.8}, inputs));
    }

    @Test
    void invalidInputsAreRejected() {
        NeuralNetwork network = new NeuralNetwork(3, 2, 4, 1);

        assertThrows(IllegalArgumentException.class, () -> network.predict(null));
        assertThrows(IllegalArgumentException.class, () -> network.predict(new double[]{1.0}));
        assertThrows(IllegalArgumentException.class, () -> network.predict(new double[]{1.0, 2.0, 3.0}));
        assertThrows(IllegalArgumentException.class, () -> network.predict(new double[]{1.0, Double.NaN}));
        assertThrows(IllegalArgumentException.class, () -> network.predict(new double[]{Double.POSITIVE_INFINITY, 0.0}));
    }

    // ---------- train ----------

    @Test
    void trainingReturnsTheErrorBeforeTheAdjustment() {
        NeuralNetwork network = new NeuralNetwork(5, 2, 3, 1);
        double[] inputs = {0.2, 0.7};
        double output = network.predict(inputs);

        double error = network.train(inputs, 1.0, 0.1);

        assertClose(0.5 * (output - 1.0) * (output - 1.0), error, 1e-12);
    }

    @Test
    void trainingOnTheSameExampleReducesItsError() {
        NeuralNetwork network = new NeuralNetwork(5, 2, 3, 1);
        double[] inputs = {0.2, 0.7};

        double before = network.train(inputs, 1.0, 0.1);
        for (int i = 0; i < 200; i++) {
            network.train(inputs, 1.0, 0.1);
        }
        double after = network.train(inputs, 1.0, 0.1);

        assertTrue(after < before, "el error no bajó: " + before + " → " + after);
    }

    @Test
    void learnsXor() {
        NeuralNetwork network = trainedXor();

        for (int i = 0; i < XOR_INPUTS.length; i++) {
            assertClose(XOR_TARGETS[i], network.predict(XOR_INPUTS[i]), 0.1);
        }
    }

    @Test
    void learnsAThresholdFromNumbers() {
        // Aprende que la salida es 1 cuando la suma de las dos entradas supera 1.
        NeuralNetwork network = new NeuralNetwork(11, 2, 6, 1);
        Random random = new Random(4);
        for (int i = 0; i < 20000; i++) {
            double a = random.nextDouble();
            double b = random.nextDouble();
            network.train(new double[]{a, b}, a + b > 1.0 ? 1.0 : 0.0, 0.1);
        }

        assertTrue(network.predict(new double[]{0.9, 0.9}) > 0.9);
        assertTrue(network.predict(new double[]{0.1, 0.1}) < 0.1);
    }

    @Test
    void analyticGradientMatchesTheNumericalOne() {
        long seed = 21;
        int[] sizes = {3, 4, 3, 1};
        double[] inputs = {0.6, 0.3, 0.9};
        double target = 0.8;
        double rate = 1e-6;
        double step = 1e-6;

        NeuralNetwork reference = new NeuralNetwork(seed, sizes);
        NeuralNetwork trained = new NeuralNetwork(seed, sizes);
        trained.train(inputs, target, rate);

        int checked = 0;
        for (int layer = 0; layer < sizes.length - 1; layer++) {
            for (int neuron = 0; neuron < sizes[layer + 1]; neuron++) {
                for (int input = 0; input < sizes[layer]; input++) {
                    double original = reference.weight(layer, neuron, input);
                    reference.setWeight(layer, neuron, input, original + step);
                    double up = loss(reference, inputs, target);
                    reference.setWeight(layer, neuron, input, original - step);
                    double down = loss(reference, inputs, target);
                    reference.setWeight(layer, neuron, input, original);

                    double numerical = (up - down) / (2 * step);
                    double analytic = (original - trained.weight(layer, neuron, input)) / rate;
                    assertClose(numerical, analytic, 1e-5);
                    checked++;
                }
            }
        }
        assertEquals(3 * 4 + 4 * 3 + 3 * 1, checked);
    }

    private static double loss(NeuralNetwork network, double[] inputs, double target) {
        double difference = network.predict(inputs) - target;
        return 0.5 * difference * difference;
    }

    @Test
    void invalidTrainingArgumentsAreRejected() {
        NeuralNetwork network = new NeuralNetwork(5, 2, 3, 1);
        double[] inputs = {0.2, 0.7};

        assertThrows(IllegalArgumentException.class, () -> network.train(inputs, -0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> network.train(inputs, 1.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> network.train(inputs, Double.NaN, 0.1));
        assertThrows(IllegalArgumentException.class, () -> network.train(inputs, 0.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> network.train(inputs, 0.5, -0.1));
        assertThrows(IllegalArgumentException.class, () -> network.train(inputs, 0.5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> network.train(inputs, 0.5, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> network.train(new double[]{1.0}, 0.5, 0.1));
    }

    @Test
    void trainingTwiceWithTheSameSeedGivesTheSameNetwork() {
        NeuralNetwork first = trainedXor();
        NeuralNetwork second = trainedXor();

        for (double[] inputs : XOR_INPUTS) {
            assertEquals(first.predict(inputs), second.predict(inputs));
        }
    }
}