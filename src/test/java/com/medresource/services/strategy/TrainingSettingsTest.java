package com.medresource.services.strategy;

import com.medresource.model.CaseConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los parámetros de entrenamiento.
 */
class TrainingSettingsTest {

    private static TrainingSettings valid() {
        return new TrainingSettings(2, 1, 10, 3, 0.05, 4, 3, 7);
    }

    @Test
    void keepsTheValues() {
        TrainingSettings settings = valid();

        assertEquals(2, settings.trainingSeeds());
        assertEquals(1, settings.evaluationSeeds());
        assertEquals(10, settings.patientsPerCase());
        assertEquals(3, settings.epochs());
        assertEquals(0.05, settings.learningRate(), 1e-12);
        assertEquals(4, settings.firstHidden());
        assertEquals(3, settings.secondHidden());
        assertEquals(7L, settings.networkSeed());
    }

    @Test
    void standardSettingsAreThoseOfTheProject() {
        TrainingSettings settings = TrainingSettings.standard();

        assertEquals(new TrainingSettings(30, 10, 100, 20, 0.01, 16, 8, 42), settings);
    }

    @Test
    void needsAtLeastOneSeedOfEachKind() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(0, 1, 10, 3, 0.05, 4, 3, 7));
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(1, 0, 10, 3, 0.05, 4, 3, 7));
    }

    @Test
    void thePatientsPerCaseMustBeAValidCaseSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrainingSettings(1, 1, CaseConfig.MIN_PATIENTS - 1, 3, 0.05, 4, 3, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new TrainingSettings(1, 1, CaseConfig.MAX_PATIENTS + 1, 3, 0.05, 4, 3, 7));
        assertEquals(CaseConfig.MIN_PATIENTS,
                new TrainingSettings(1, 1, CaseConfig.MIN_PATIENTS, 3, 0.05, 4, 3, 7).patientsPerCase());
        assertEquals(CaseConfig.MAX_PATIENTS,
                new TrainingSettings(1, 1, CaseConfig.MAX_PATIENTS, 3, 0.05, 4, 3, 7).patientsPerCase());
    }

    @Test
    void needsAtLeastOneEpoch() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(1, 1, 10, 0, 0.05, 4, 3, 7));
    }

    @Test
    void theLearningRateMustBeAPositiveFiniteNumber() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(1, 1, 10, 3, 0.0, 4, 3, 7));
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(1, 1, 10, 3, -0.1, 4, 3, 7));
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(1, 1, 10, 3, Double.NaN, 4, 3, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new TrainingSettings(1, 1, 10, 3, Double.POSITIVE_INFINITY, 4, 3, 7));
    }

    @Test
    void eachHiddenLayerNeedsNeurons() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(1, 1, 10, 3, 0.05, 0, 3, 7));
        assertThrows(IllegalArgumentException.class, () -> new TrainingSettings(1, 1, 10, 3, 0.05, 4, 0, 7));
    }
}