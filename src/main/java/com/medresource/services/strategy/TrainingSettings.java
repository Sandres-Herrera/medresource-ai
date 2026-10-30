package com.medresource.services.strategy;

import com.medresource.model.CaseConfig;

/**
 * Parámetros con los que se entrena una red neuronal.
 *
 * @param trainingSeeds    cuántas semillas (casos distintos) se usan para entrenar, al menos 1
 * @param evaluationSeeds  cuántas semillas distintas se usan para medir la red, al menos 1
 * @param patientsPerCase  pacientes de cada caso simulado, de {@value CaseConfig#MIN_PATIENTS}
 *                         a {@value CaseConfig#MAX_PATIENTS}
 * @param epochs           veces que se recorren todos los ejemplos, al menos 1
 * @param learningRate     tamaño de cada ajuste; mayor que 0 y finito
 * @param firstHidden      neuronas de la primera capa oculta, al menos 1
 * @param secondHidden     neuronas de la segunda capa oculta, al menos 1
 * @param networkSeed      semilla de los pesos iniciales y del orden de los ejemplos
 */
public record TrainingSettings(int trainingSeeds, int evaluationSeeds, int patientsPerCase, int epochs,
                               double learningRate, int firstHidden, int secondHidden, long networkSeed) {

    /**
     * Valida cada parámetro.
     *
     * @throws IllegalArgumentException si algún parámetro está fuera de su rango
     */
    public TrainingSettings {
        if (trainingSeeds < 1 || evaluationSeeds < 1) {
            throw new IllegalArgumentException("Debe haber al menos una semilla de entrenamiento y una de evaluación.");
        }
        if (patientsPerCase < CaseConfig.MIN_PATIENTS || patientsPerCase > CaseConfig.MAX_PATIENTS) {
            throw new IllegalArgumentException("Los pacientes por caso deben estar entre " + CaseConfig.MIN_PATIENTS
                    + " y " + CaseConfig.MAX_PATIENTS + ".");
        }
        if (epochs < 1) {
            throw new IllegalArgumentException("Debe haber al menos una época.");
        }
        if (!(learningRate > 0.0) || Double.isInfinite(learningRate)) {
            throw new IllegalArgumentException("La tasa de aprendizaje debe ser un número mayor que 0.");
        }
        if (firstHidden < 1 || secondHidden < 1) {
            throw new IllegalArgumentException("Cada capa oculta necesita al menos una neurona.");
        }
    }

    /**
     * Devuelve los parámetros con los que se entrenan las redes del proyecto: 30 casos de
     * entrenamiento, 10 de evaluación, 100 pacientes por caso, 20 épocas, tasa 0.01 y una
     * red 14 → 16 → 8 → 1.
     *
     * @return parámetros estándar
     */
    public static TrainingSettings standard() {
        return new TrainingSettings(30, 10, 100, 20, 0.01, 16, 8, 42);
    }
}