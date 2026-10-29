package com.medresource.services.strategy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}