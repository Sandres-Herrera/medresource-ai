package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;
import com.medresource.model.Objective;
import com.medresource.services.strategy.FeatureExtractor;
import com.medresource.services.strategy.NeuralNetwork;
import com.medresource.services.strategy.TrainedModel;
import com.medresource.services.strategy.Trainer;
import com.medresource.services.strategy.TrainingExample;
import com.medresource.services.strategy.TrainingReport;
import com.medresource.services.strategy.TrainingSettings;

import java.util.List;

/**
 * Entrena la red neuronal de cada objetivo simulando casos.
 *
 * <p>Para un objetivo genera ejemplos con las semillas de entrenamiento, entrena una red
 * nueva y la mide con ejemplos de semillas de evaluación distintas. Con los mismos
 * parámetros siempre produce la misma red.</p>
 */
public class ModelTrainingService {

    private final TrainingDataGenerator generator;
    private final TrainingSettings settings;

    /**
     * Crea el servicio.
     *
     * @param hospitalConfig hospital en el que se simulan los casos
     * @param settings       parámetros del entrenamiento
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    public ModelTrainingService(HospitalConfig hospitalConfig, TrainingSettings settings) {
        if (hospitalConfig == null || settings == null) {
            throw new IllegalArgumentException("El hospital y los parámetros son obligatorios.");
        }
        this.generator = new TrainingDataGenerator(hospitalConfig);
        this.settings = settings;
    }

    /**
     * Entrena una red nueva para un objetivo.
     *
     * @param objective objetivo que puntúa los desenlaces
     * @return la red entrenada con su resumen y su error de evaluación
     * @throws IllegalArgumentException si el objetivo es nulo
     */
    public TrainedModel train(Objective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("El objetivo es obligatorio.");
        }
        List<TrainingExample> trainingExamples = generator.generate(
                TrainingDataGenerator.seeds(TrainingDataGenerator.TRAINING_FIRST_SEED, settings.trainingSeeds()),
                objective, settings.patientsPerCase());
        List<TrainingExample> evaluationExamples = generator.generate(
                TrainingDataGenerator.seeds(TrainingDataGenerator.EVALUATION_FIRST_SEED, settings.evaluationSeeds()),
                objective, settings.patientsPerCase());

        NeuralNetwork network = new NeuralNetwork(settings.networkSeed(),
                FeatureExtractor.FEATURE_COUNT, settings.firstHidden(), settings.secondHidden(), 1);
        Trainer trainer = new Trainer(settings.networkSeed(), settings.epochs(), settings.learningRate());
        TrainingReport report = trainer.train(network, trainingExamples);
        return new TrainedModel(objective, network, report, trainer.evaluate(network, evaluationExamples));
    }
}