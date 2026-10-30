package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;
import com.medresource.model.CaseConfig;
import com.medresource.model.Objective;
import com.medresource.model.PatientOutcome;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FeatureExtractor;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.OutcomeScorer;
import com.medresource.services.strategy.RandomStrategy;
import com.medresource.services.strategy.RecordingStrategy;
import com.medresource.services.strategy.TrainingExample;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera los ejemplos con los que se entrena la red neuronal, a partir de simulaciones.
 *
 * <p>Por cada semilla y cada escenario simula el mismo caso con tres estrategias
 * (FIFO, prioridad clínica y aleatoria) para que la red vea decisiones variadas.
 * Cada paciente atendido aporta un ejemplo con cómo se veía cuando lo eligieron y
 * cómo le fue; cada paciente que nunca fue atendido aporta uno con cómo se veía
 * la última vez que esperó, con resultado 0.</p>
 *
 * <p>Las semillas de entrenamiento empiezan en {@value #TRAINING_FIRST_SEED} y las
 * de evaluación en {@value #EVALUATION_FIRST_SEED}: nunca se mezclan, así que la red
 * se mide con casos que no vio al aprender.</p>
 */
public class TrainingDataGenerator {

    /** Primera semilla de los casos de entrenamiento. */
    public static final long TRAINING_FIRST_SEED = 1;

    /** Primera semilla de los casos de evaluación. */
    public static final long EVALUATION_FIRST_SEED = 1_000_000;

    private final HospitalConfig hospitalConfig;
    private final FeatureExtractor extractor = new FeatureExtractor();
    private final OutcomeScorer scorer = new OutcomeScorer();
    private final CaseGenerator caseGenerator = new CaseGenerator();
    private final HospitalFactory hospitalFactory = new HospitalFactory();

    /**
     * Crea un generador para un tamaño de hospital.
     *
     * @param hospitalConfig hospital en el que se simula
     * @throws IllegalArgumentException si la configuración es nula
     */
    public TrainingDataGenerator(HospitalConfig hospitalConfig) {
        if (hospitalConfig == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        this.hospitalConfig = hospitalConfig;
    }

    /**
     * Arma una lista de semillas consecutivas.
     *
     * @param first primera semilla
     * @param count cuántas semillas, al menos 1
     * @return semillas {@code first}, {@code first + 1}, …
     * @throws IllegalArgumentException si {@code count} es menor que 1
     */
    public static List<Long> seeds(long first, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Debe haber al menos una semilla.");
        }
        List<Long> seeds = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            seeds.add(first + index);
        }
        return seeds;
    }

    /**
     * Simula y devuelve los ejemplos de entrenamiento.
     *
     * @param seeds           semillas de los casos
     * @param objective       objetivo que puntúa cada desenlace
     * @param patientsPerCase pacientes de cada caso
     * @return ejemplos, en el orden en que se simularon
     * @throws IllegalArgumentException si las semillas, el objetivo o la cantidad de pacientes no son válidos
     */
    public List<TrainingExample> generate(List<Long> seeds, Objective objective, int patientsPerCase) {
        if (seeds == null || seeds.isEmpty() || objective == null) {
            throw new IllegalArgumentException("Las semillas y el objetivo son obligatorios.");
        }
        List<TrainingExample> examples = new ArrayList<>();
        for (long seed : seeds) {
            for (Scenario scenario : Scenario.values()) {
                SimulationCase simulationCase = caseGenerator.generate(
                        new CaseConfig(seed, scenario, patientsPerCase, CaseConfig.DEFAULT_DURATION_MINUTES));
                List<AllocationStrategy> bases = List.of(
                        new FifoStrategy(), new ClinicalPriorityStrategy(), new RandomStrategy(seed));
                for (AllocationStrategy base : bases) {
                    examples.addAll(examplesFrom(simulationCase, base, objective));
                }
            }
        }
        return examples;
    }

    private List<TrainingExample> examplesFrom(SimulationCase simulationCase, AllocationStrategy base,
                                               Objective objective) {
        RecordingStrategy recorder = new RecordingStrategy(base, extractor);
        SimulationResult result = new SimulationEngine(simulationCase,
                hospitalFactory.create(hospitalConfig), recorder).run();
        List<TrainingExample> examples = new ArrayList<>();
        for (PatientOutcome outcome : result.outcomes()) {
            Optional<double[]> features = recorder.featuresFor(outcome.patientCode());
            if (features.isPresent()) {
                examples.add(new TrainingExample(features.get(), scorer.score(outcome, objective)));
            }
        }
        return examples;
    }
}