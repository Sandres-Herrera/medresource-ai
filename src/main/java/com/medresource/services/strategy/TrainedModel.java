package com.medresource.services.strategy;

import com.medresource.model.Objective;

/**
 * Resultado de entrenar una red para un objetivo.
 *
 * @param objective      objetivo para el que se entrenó
 * @param network        red ya entrenada
 * @param report         resumen del entrenamiento
 * @param evaluationLoss error medio con casos que la red no vio al aprender
 */
public record TrainedModel(Objective objective, NeuralNetwork network, TrainingReport report, double evaluationLoss) {

    /**
     * Valida que no falte ningún dato.
     *
     * @throws IllegalArgumentException si el objetivo, la red o el resumen son nulos, o el error no es un número
     */
    public TrainedModel {
        if (objective == null || network == null || report == null || Double.isNaN(evaluationLoss)) {
            throw new IllegalArgumentException("El modelo necesita objetivo, red, resumen y error de evaluación.");
        }
    }

    /**
     * Devuelve el error medio de la última época de entrenamiento.
     *
     * @return error de entrenamiento
     */
    public double trainingLoss() {
        return report.lastLoss();
    }
}