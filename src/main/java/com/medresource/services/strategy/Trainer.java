package com.medresource.services.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Entrena una red neuronal con una lista de ejemplos.
 *
 * <p>En cada época recorre todos los ejemplos en un orden mezclado y le pide a la
 * red que aprenda de cada uno. El orden sale de una semilla, así que el mismo
 * entrenamiento con los mismos datos siempre deja la misma red.</p>
 */
public class Trainer {

    private final long seed;
    private final int epochs;
    private final double learningRate;

    /**
     * Prepara un entrenamiento.
     *
     * @param seed         semilla del orden de los ejemplos
     * @param epochs       veces que se recorren todos los ejemplos, al menos 1
     * @param learningRate tamaño de cada ajuste; mayor que 0 y finito
     * @throws IllegalArgumentException si las épocas o la tasa de aprendizaje no son válidas
     */
    public Trainer(long seed, int epochs, double learningRate) {
        if (epochs < 1) {
            throw new IllegalArgumentException("Debe haber al menos una época.");
        }
        if (!(learningRate > 0.0) || Double.isInfinite(learningRate)) {
            throw new IllegalArgumentException("La tasa de aprendizaje debe ser un número mayor que 0.");
        }
        this.seed = seed;
        this.epochs = epochs;
        this.learningRate = learningRate;
    }

    /**
     * Entrena la red; la modifica.
     *
     * @param network  red que aprende
     * @param examples ejemplos de entrenamiento, al menos uno
     * @return error medio de cada época
     * @throws IllegalArgumentException si falta la red, no hay ejemplos o su tamaño no coincide con la red
     */
    public TrainingReport train(NeuralNetwork network, List<TrainingExample> examples) {
        requireInputs(network, examples);
        List<TrainingExample> order = new ArrayList<>(examples);
        Random random = new Random(seed);
        List<Double> losses = new ArrayList<>();
        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(order, random);
            double total = 0.0;
            for (TrainingExample example : order) {
                total += network.train(example.features(), example.target(), learningRate);
            }
            losses.add(total / order.size());
        }
        return new TrainingReport(order.size(), losses);
    }

    private static void requireInputs(NeuralNetwork network, List<TrainingExample> examples) {
        if (network == null) {
            throw new IllegalArgumentException("La red es obligatoria.");
        }
        if (examples == null || examples.isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos un ejemplo.");
        }
        for (TrainingExample example : examples) {
            if (example == null || example.features().length != network.getInputSize()) {
                throw new IllegalArgumentException("Los ejemplos deben tener " + network.getInputSize() + " características.");
            }
        }
    }
}