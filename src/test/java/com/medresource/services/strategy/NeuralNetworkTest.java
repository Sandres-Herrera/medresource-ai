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

    private static void assertClose(double expected, double actual, double tolerance) {
        assertTrue(Math.abs(expected - actual) <= tolerance, "esperado " + expected + " pero fue " + actual);
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
}