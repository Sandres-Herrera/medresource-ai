package com.medresource.services.strategy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private static final String FILE_HEADER = "MEDRESOURCE-NEURAL-NETWORK 1";

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

    private NeuralNetwork(int[] layerSizes, double[][][] weights, double[][] biases) {
        this.layerSizes = layerSizes;
        this.weights = weights;
        this.biases = biases;
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
     * Aprende de un ejemplo: calcula el error de su predicción y ajusta los pesos
     * y sesgos un paso en la dirección que lo reduce (retropropagación).
     *
     * <p>El error es la mitad del cuadrado de la diferencia entre la salida y el objetivo.</p>
     *
     * @param inputs       entradas del ejemplo
     * @param target       salida esperada, de 0 a 1
     * @param learningRate tamaño del paso, mayor que 0
     * @return el error del ejemplo antes del ajuste
     * @throws IllegalArgumentException si las entradas no son válidas, el objetivo
     *                                  está fuera de 0 a 1 o la tasa no es positiva
     */
    public double train(double[] inputs, double target, double learningRate) {
        if (!(target >= 0.0 && target <= 1.0)) {
            throw new IllegalArgumentException("El objetivo debe estar entre 0 y 1.");
        }
        if (!(learningRate > 0.0) || Double.isInfinite(learningRate)) {
            throw new IllegalArgumentException("La tasa de aprendizaje debe ser mayor que 0.");
        }
        double[][] activations = forward(inputs);
        int layers = weights.length;
        int last = layers - 1;

        // 1) Cuánto contribuyó cada neurona al error, de la salida hacia la entrada.
        double[][] deltas = new double[layers][];
        double output = activations[layers][0];
        double difference = output - target;
        deltas[last] = new double[]{difference * output * (1.0 - output)};
        for (int layer = last - 1; layer >= 0; layer--) {
            deltas[layer] = new double[layerSizes[layer + 1]];
            for (int neuron = 0; neuron < deltas[layer].length; neuron++) {
                if (activations[layer + 1][neuron] <= 0.0) {
                    continue; // ReLU apagada: no dejó pasar nada, así que no recibe corrección
                }
                double sum = 0.0;
                for (int next = 0; next < deltas[layer + 1].length; next++) {
                    sum += weights[layer + 1][next][neuron] * deltas[layer + 1][next];
                }
                deltas[layer][neuron] = sum;
            }
        }

        // 2) Con todas las correcciones ya calculadas, se ajustan los pesos y sesgos.
        for (int layer = 0; layer < layers; layer++) {
            for (int neuron = 0; neuron < weights[layer].length; neuron++) {
                for (int input = 0; input < weights[layer][neuron].length; input++) {
                    weights[layer][neuron][input] -= learningRate * deltas[layer][neuron] * activations[layer][input];
                }
                biases[layer][neuron] -= learningRate * deltas[layer][neuron];
            }
        }
        return 0.5 * difference * difference;
    }

    /**
     * Guarda la red (tamaños, pesos y sesgos) en un archivo de texto.
     *
     * @param file archivo de destino; se reemplaza si existe
     * @throws IllegalArgumentException si el archivo es nulo
     * @throws IOException              si no se puede escribir
     */
    public void save(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }
        List<String> lines = new ArrayList<>();
        lines.add(FILE_HEADER);
        lines.add(joinInts(layerSizes));
        for (int layer = 0; layer < weights.length; layer++) {
            for (int neuron = 0; neuron < weights[layer].length; neuron++) {
                lines.add(joinDoubles(biases[layer][neuron], weights[layer][neuron]));
            }
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * Carga una red guardada con {@link #save(Path)}.
     *
     * @param file archivo de origen
     * @return la red, con las mismas predicciones que la guardada
     * @throws IllegalArgumentException si el archivo es nulo o su contenido no es una red válida
     * @throws IOException              si no se puede leer
     */
    public static NeuralNetwork load(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        try {
            if (lines.size() < 2 || !FILE_HEADER.equals(lines.get(0))) {
                throw new IllegalArgumentException("El archivo no es una red neuronal de MedResource AI.");
            }
            int[] sizes = validatedSizes(Arrays.stream(lines.get(1).trim().split(" "))
                    .mapToInt(Integer::parseInt).toArray());
            int layers = sizes.length - 1;
            double[][][] weights = new double[layers][][];
            double[][] biases = new double[layers][];
            int row = 2;
            for (int layer = 0; layer < layers; layer++) {
                weights[layer] = new double[sizes[layer + 1]][sizes[layer]];
                biases[layer] = new double[sizes[layer + 1]];
                for (int neuron = 0; neuron < sizes[layer + 1]; neuron++) {
                    double[] values = Arrays.stream(lines.get(row++).trim().split(" "))
                            .mapToDouble(Double::parseDouble).toArray();
                    if (values.length != sizes[layer] + 1) {
                        throw new IllegalArgumentException("Una fila del archivo tiene un tamaño incorrecto.");
                    }
                    biases[layer][neuron] = values[0];
                    System.arraycopy(values, 1, weights[layer][neuron], 0, sizes[layer]);
                }
            }
            if (row != lines.size()) {
                throw new IllegalArgumentException("El archivo tiene filas de más.");
            }
            return new NeuralNetwork(sizes, weights, biases);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("El contenido del archivo no es válido.", e);
        }
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

    void setWeight(int layer, int neuron, int input, double value) {
        weights[layer][neuron][input] = value;
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

    private static String joinInts(int[] values) {
        return String.join(" ", Arrays.stream(values).mapToObj(Integer::toString).toList());
    }

    private static String joinDoubles(double first, double[] rest) {
        StringBuilder text = new StringBuilder(Double.toString(first));
        for (double value : rest) {
            text.append(' ').append(Double.toString(value));
        }
        return text.toString();
    }
}