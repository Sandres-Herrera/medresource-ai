package com.medresource.services.strategy;

import java.util.Random;

/**
 * Red neuronal multicapa (perceptrón) con una sola salida.
 *
 * <p>Las capas ocultas usan la función ReLU y la capa de salida usa la sigmoide,
 * así que la salida siempre está entre 0 y 1. Aprende por retropropagación con
 * descenso del gradiente, de a un ejemplo. Los pesos iniciales salen de un
 * generador con semilla, de modo que la misma semilla produce siempre la misma red.</p>
 *
 * <p>No depende de ninguna librería: solo usa arreglos de {@code double}.</p>
 */
public class NeuralNetwork {

    private final int[] layerSizes;

    /** Pesos: {@code weights[capa][neurona][entrada]}; la capa 0 es la primera capa oculta. */
    private final double[][][] weights;

    /** Sesgos: {@code biases[capa][neurona]}. */
    private final double[][] biases;

    /**
     * Crea una red con pesos iniciales aleatorios (inicialización de He) y sesgos en cero.
     *
     * @param seed       semilla del generador de pesos iniciales
     * @param layerSizes neuronas de cada capa, de la entrada a la salida; al menos dos
     *                   capas, todas con una neurona o más, y la última con exactamente una
     * @throws IllegalArgumentException si los tamaños no son válidos
     */
    public NeuralNetwork(long seed, int... layerSizes) {
        this.layerSizes = validatedSizes(layerSizes);
        Random random = new Random(seed);
        int layers = this.layerSizes.length - 1;
        weights = new double[layers][][];
        biases = new double[layers][];
        for (int layer = 0; layer < layers; layer++) {
            int inputs = this.layerSizes[layer];
            int neurons = this.layerSizes[layer + 1];
            double scale = Math.sqrt(2.0 / inputs);
            weights[layer] = new double[neurons][inputs];
            biases[layer] = new double[neurons];
            for (int neuron = 0; neuron < neurons; neuron++) {
                for (int input = 0; input < inputs; input++) {
                    weights[layer][neuron][input] = random.nextGaussian() * scale;
                }
            }
        }
    }

    /**
     * Calcula la salida de la red para unas entradas.
     *
     * @param inputs entradas, tantas como neuronas tiene la primera capa
     * @return puntuación de 0 a 1
     * @throws IllegalArgumentException si las entradas son nulas, tienen un tamaño
     *                                  distinto al de la capa de entrada o no son números finitos
     */
    public double predict(double[] inputs) {
        double[][] activations = forward(inputs);
        return activations[activations.length - 1][0];
    }

    /**
     * Devuelve las neuronas de cada capa.
     *
     * @return copia de los tamaños, de la entrada a la salida
     */
    public int[] getLayerSizes() {
        return layerSizes.clone();
    }

    /**
     * Devuelve cuántas entradas recibe la red.
     *
     * @return neuronas de la capa de entrada
     */
    public int getInputSize() {
        return layerSizes[0];
    }

    /** Peso de una conexión; solo para las pruebas. */
    double weight(int layer, int neuron, int input) {
        return weights[layer][neuron][input];
    }

    /**
     * Calcula la salida de todas las capas. La posición 0 son las entradas y la última, la salida.
     */
    private double[][] forward(double[] inputs) {
        if (inputs == null || inputs.length != layerSizes[0]) {
            throw new IllegalArgumentException("La red espera " + layerSizes[0] + " entradas.");
        }
        for (double value : inputs) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("Las entradas deben ser números finitos.");
            }
        }
        int layers = weights.length;
        double[][] activations = new double[layers + 1][];
        activations[0] = inputs;
        for (int layer = 0; layer < layers; layer++) {
            double[] result = new double[weights[layer].length];
            for (int neuron = 0; neuron < result.length; neuron++) {
                double sum = biases[layer][neuron];
                for (int input = 0; input < activations[layer].length; input++) {
                    sum += weights[layer][neuron][input] * activations[layer][input];
                }
                result[neuron] = layer == layers - 1 ? sigmoid(sum) : Math.max(0.0, sum);
            }
            activations[layer + 1] = result;
        }
        return activations;
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static int[] validatedSizes(int[] sizes) {
        if (sizes == null || sizes.length < 2) {
            throw new IllegalArgumentException("La red necesita al menos una capa de entrada y una de salida.");
        }
        for (int size : sizes) {
            if (size < 1) {
                throw new IllegalArgumentException("Cada capa necesita al menos una neurona.");
            }
        }
        if (sizes[sizes.length - 1] != 1) {
            throw new IllegalArgumentException("La capa de salida debe tener exactamente una neurona.");
        }
        return sizes.clone();
    }
}