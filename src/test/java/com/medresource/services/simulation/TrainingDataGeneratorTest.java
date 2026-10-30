package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del generador de datos de entrenamiento.
 */
class TrainingDataGeneratorTest {

    // ---------- semillas ----------

    @Test
    void buildsConsecutiveSeeds() {
        assertEquals(List.of(5L, 6L, 7L), TrainingDataGenerator.seeds(5, 3));
        assertEquals(List.of(9L), TrainingDataGenerator.seeds(9, 1));
    }

    @Test
    void rejectsAnEmptyListOfSeeds() {
        assertThrows(IllegalArgumentException.class, () -> TrainingDataGenerator.seeds(1, 0));
        assertThrows(IllegalArgumentException.class, () -> TrainingDataGenerator.seeds(1, -2));
    }

    @Test
    void trainingAndEvaluationSeedsNeverOverlap() {
        List<Long> training = TrainingDataGenerator.seeds(TrainingDataGenerator.TRAINING_FIRST_SEED, 100_000);
        List<Long> evaluation = TrainingDataGenerator.seeds(TrainingDataGenerator.EVALUATION_FIRST_SEED, 1000);

        for (long seed : evaluation) {
            assertFalse(training.contains(seed), "semilla repetida " + seed);
        }
    }

    // ---------- construcción ----------

    @Test
    void rejectsAMissingHospitalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingDataGenerator(null));
    }
}