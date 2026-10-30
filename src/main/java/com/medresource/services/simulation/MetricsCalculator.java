package com.medresource.services.simulation;

import com.medresource.model.HealthModel;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.Person;
import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationResult;
import com.medresource.model.TriageLevel;
import com.medresource.services.strategy.OutcomeScorer;

import java.util.List;
import java.util.function.Predicate;

/**
 * Calcula las métricas de una ejecución a partir de su resultado.
 *
 * <p>Un paciente sobrevivió si fue dado de alta o quedó estabilizado sin cura. Los años de vida salvados de
 * un sobreviviente son los que le faltaban hasta los {@value OutcomeScorer#LIFE_EXPECTANCY_YEARS} años
 * (supuesto). La espera es el tiempo entre la llegada y el inicio del tratamiento; si nunca fue atendido,
 * hasta que terminó su caso. Los pacientes urgentes son los de triage I y II.</p>
 */
public class MetricsCalculator {

    private static final int URGENT_LEVEL_LIMIT = TriageLevel.II.getLevel();

    /**
     * Calcula las métricas de una ejecución.
     *
     * @param result resultado de la simulación
     * @return métricas de la ejecución
     * @throws IllegalArgumentException si falta el resultado
     */
    public RunMetrics calculate(SimulationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("El resultado de la simulación es obligatorio.");
        }
        List<PatientOutcome> outcomes = result.outcomes();
        List<PatientOutcome> minors = filter(outcomes, outcome -> outcome.age() < Person.MINOR_AGE_LIMIT);
        List<PatientOutcome> elderly = filter(outcomes, outcome -> outcome.age() >= HealthModel.ELDERLY_AGE_LIMIT);
        List<PatientOutcome> urgent = filter(outcomes, outcome -> outcome.triage().getLevel() <= URGENT_LEVEL_LIMIT);
        List<PatientOutcome> survivors = filter(outcomes, MetricsCalculator::survived);
        double lifeYears = survivors.stream()
                .mapToInt(outcome -> Math.max(0, OutcomeScorer.LIFE_EXPECTANCY_YEARS - outcome.age())).sum();
        return new RunMetrics(result.strategyName(), outcomes.size(), (int) result.deaths(),
                (int) result.avoidableDeaths(), survivors.size(), (int) result.count(PatientStatus.REFERRED),
                (int) result.count(PatientStatus.UNATTENDED), averageWait(outcomes), averageWait(urgent), lifeYears,
                minors.size(), deaths(minors), elderly.size(), deaths(elderly));
    }

    private static List<PatientOutcome> filter(List<PatientOutcome> outcomes, Predicate<PatientOutcome> condition) {
        return outcomes.stream().filter(condition).toList();
    }

    private static boolean survived(PatientOutcome outcome) {
        return outcome.status() == PatientStatus.DISCHARGED
                || outcome.status() == PatientStatus.STABILIZED_WITHOUT_CURE;
    }

    private static int deaths(List<PatientOutcome> outcomes) {
        return (int) outcomes.stream().filter(outcome -> outcome.status() == PatientStatus.DECEASED).count();
    }

    /** Promedio de espera; {@link Double#NaN} si la lista está vacía. */
    private static double averageWait(List<PatientOutcome> outcomes) {
        return outcomes.stream().mapToInt(PatientOutcome::waitingMinutes).average().orElse(Double.NaN);
    }
}