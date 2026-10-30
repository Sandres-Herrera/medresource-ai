package com.medresource.services.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del ejemplo de entrenamiento.
 */
class TrainingExampleTest {

    @Test
    void keepsTheFeaturesAndTheTarget() {
        TrainingExample example = new TrainingExample(new double[]{0.1, 0.5, 1.0}, 0.75);

        assertArrayEquals(new double[]{0.1, 0.5, 1.0}, example.features());
        assertEquals(0.75, example.target());
    }

    @Test
    void acceptsTheLimitsOfTheRange() {
        new TrainingExample(new double[]{0.0, 1.0}, 0.0);
        new TrainingExample(new double[]{0.0, 1.0}, 1.0);
    }

    @Test
    void changingTheOriginalArrayDoesNotChangeTheExample() {
        double[] original = {0.2, 0.4};
        TrainingExample example = new TrainingExample(original, 0.5);

        original[0] = 0.9;

        assertArrayEquals(new double[]{0.2, 0.4}, example.features());
    }

    @Test
    void changingTheReturnedArrayDoesNotChangeTheExample() {
        TrainingExample example = new TrainingExample(new double[]{0.2, 0.4}, 0.5);

        example.features()[0] = 0.9;

        assertArrayEquals(new double[]{0.2, 0.4}, example.features());
    }

    @Test
    void rejectsMissingOrEmptyFeatures() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(null, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(new double[0], 0.5));
    }

    @Test
    void rejectsFeaturesOutsideZeroToOne() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(new double[]{0.5, -0.01}, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(new double[]{1.01, 0.5}, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(new double[]{Double.NaN}, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new TrainingExample(new double[]{Double.POSITIVE_INFINITY}, 0.5));
    }

    @Test
    void rejectsATargetOutsideZeroToOne() {
        double[] features = {0.5};

        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(features, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(features, 1.01));
        assertThrows(IllegalArgumentException.class, () -> new TrainingExample(features, Double.NaN));
    }
}