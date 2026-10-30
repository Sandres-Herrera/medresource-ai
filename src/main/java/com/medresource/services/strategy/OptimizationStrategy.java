package com.medresource.services.strategy;

import com.medresource.model.Hospital;
import com.medresource.model.Objective;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estrategia de inteligencia artificial: atiende primero al paciente al que más le sirve ser atendido ahora.
 *
 * <p>Por cada paciente en espera calcula sus características con {@link FeatureExtractor}
 * (siempre a partir del diagnóstico, nunca de la enfermedad real) y puntúa su
 * <strong>beneficio esperado</strong> como el producto de dos factores:</p>
 * <ul>
 *   <li>lo que predice la red neuronal: cómo le va a un paciente con esas características cuando
 *       se le atiende, de 0 a 1 según el {@link Objective} para el que se entrenó;</li>
 *   <li>la urgencia: qué tan pronto moriría si no se le atiende
 *       ({@code 1 - minutesUntilDeath}); si puede esperar mucho, su urgencia es 0.</li>
 * </ul>
 *
 * <p>Solo con la red, la estrategia atendería primero a los que mejor pronóstico tienen, que son
 * justo los que menos necesitan que los atiendan ya; por eso se multiplica por la urgencia.
 * Atiende al de mayor puntuación para el que haya recursos; ante un empate, al que llegó primero
 * y, por último, al de menor número. La elección de recursos es la misma de las demás estrategias.</p>
 */
public class OptimizationStrategy extends PriorityBasedStrategy {

    private static final int MINUTES_UNTIL_DEATH = FeatureExtractor.FEATURE_NAMES.indexOf("minutesUntilDeath");

    private final NeuralNetwork network;
    private final Objective objective;
    private final FeatureExtractor extractor;

    /**
     * Crea la estrategia con el extractor de características por defecto.
     *
     * @param network   red entrenada, con {@link FeatureExtractor#FEATURE_COUNT} entradas
     * @param objective objetivo para el que se entrenó la red
     * @throws IllegalArgumentException si algún argumento es nulo o la red no tiene el tamaño esperado
     */
    public OptimizationStrategy(NeuralNetwork network, Objective objective) {
        this(network, objective, new FeatureExtractor());
    }

    /**
     * Crea la estrategia con un extractor de características propio.
     *
     * @param network   red entrenada, con {@link FeatureExtractor#FEATURE_COUNT} entradas
     * @param objective objetivo para el que se entrenó la red
     * @param extractor extractor que describe a cada paciente
     * @throws IllegalArgumentException si algún argumento es nulo o la red no tiene el tamaño esperado
     */
    public OptimizationStrategy(NeuralNetwork network, Objective objective, FeatureExtractor extractor) {
        if (network == null || objective == null || extractor == null) {
            throw new IllegalArgumentException("La red, el objetivo y el extractor son obligatorios.");
        }
        if (network.getInputSize() != FeatureExtractor.FEATURE_COUNT) {
            throw new IllegalArgumentException("La red debe tener " + FeatureExtractor.FEATURE_COUNT + " entradas.");
        }
        this.network = network;
        this.objective = objective;
        this.extractor = extractor;
    }

    @Override
    public String getName() {
        return "Inteligencia artificial (" + objective.getDisplayName() + ")";
    }

    /**
     * Devuelve el objetivo para el que se entrenó la red.
     *
     * @return objetivo de la estrategia
     */
    public Objective getObjective() {
        return objective;
    }

    @Override
    protected Comparator<WaitingPatient> priorityOrder(int minute, List<WaitingPatient> waiting, Hospital hospital) {
        Map<Integer, Double> scores = new HashMap<>();
        for (WaitingPatient candidate : waiting) {
            double[] features = extractor.extract(candidate, waiting, hospital, minute);
            double urgency = 1.0 - features[MINUTES_UNTIL_DEATH];
            scores.put(candidate.patient().getId(), network.predict(features) * urgency);
        }
        return Comparator.comparingDouble((WaitingPatient candidate) -> scores.get(candidate.patient().getId()))
                .reversed()
                .thenComparingInt(candidate -> candidate.patient().getArrivalMinute())
                .thenComparingInt(candidate -> candidate.patient().getId());
    }
}