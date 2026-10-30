package com.medresource.services.strategy;

/**
 * Un ejemplo para entrenar la red: la situación de un paciente y qué tan bien terminó.
 *
 * <p>Es inmutable: guarda su propia copia de las características.</p>
 *
 * @param features características del paciente, todas entre 0 y 1
 * @param target   qué tan bueno fue el resultado, entre 0 y 1
 */
public record TrainingExample(double[] features, double target) {

    /**
     * Valida el ejemplo y guarda una copia de las características.
     *
     * @throws IllegalArgumentException si no hay características, si alguna no es un
     *                                  número entre 0 y 1, o si el resultado no está entre 0 y 1
     */
    public TrainingExample {
        if (features == null || features.length == 0) {
            throw new IllegalArgumentException("El ejemplo necesita al menos una característica.");
        }
        for (double feature : features) {
            requireUnit(feature, "Las características deben ser números entre 0 y 1.");
        }
        requireUnit(target, "El resultado esperado debe ser un número entre 0 y 1.");
        features = features.clone();
    }

    /**
     * Devuelve las características del ejemplo.
     *
     * @return copia del vector de características
     */
    @Override
    public double[] features() {
        return features.clone();
    }

    private static void requireUnit(double value, String message) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(message);
        }
    }
}