package com.medresource.services.strategy;

import com.medresource.model.Objective;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del resultado de un entrenamiento.
 */
class TrainedModelTest {

    private final NeuralNetwork network = new NeuralNetwork(1, FeatureExtractor.FEATURE_COUNT, 2, 1);
    private final TrainingReport report = new TrainingReport(10, List.of(0.5, 0.3, 0.2));

    @Test
    void keepsItsData() {
        TrainedModel model = new TrainedModel(Objective.EQUITY, network, report, 0.25);

        assertEquals(Objective.EQUITY, model.objective());
        assertSame(network, model.network());
        assertSame(report, model.report());
        assertEquals(0.25, model.evaluationLoss(), 1e-12);
    }

    @Test
    void theTrainingLossIsThatOfTheLastEpoch() {
        TrainedModel model = new TrainedModel(Objective.EQUITY, network, report, 0.25);

        assertEquals(0.2, model.trainingLoss(), 1e-12);
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new TrainedModel(null, network, report, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new TrainedModel(Objective.EQUITY, null, report, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new TrainedModel(Objective.EQUITY, network, null, 0.1));
    }

    @Test
    void rejectsAnEvaluationLossThatIsNotANumber() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrainedModel(Objective.EQUITY, network, report, Double.NaN));
    }
}