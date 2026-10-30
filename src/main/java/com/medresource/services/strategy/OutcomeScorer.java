package com.medresource.services.strategy;

import com.medresource.model.Objective;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;

/**
 * Convierte el desenlace de un paciente en un número de 0 a 1 según el objetivo
 * que se quiere optimizar.
 *
 * <p>Quien no sobrevive, o nunca recibió tratamiento, vale 0 en todos los objetivos.
 * Para quien sobrevive:</p>
 * <ul>
 *   <li>vidas: vale 1;</li>
 *   <li>años de vida: vale más cuanto más joven (años que le quedan hasta los
 *       {@value #LIFE_EXPECTANCY_YEARS}, entre esos mismos años);</li>
 *   <li>equidad: vale más cuanto más esperó, de 0.5 a 1 (supuesto);</li>
 *   <li>espera: vale más cuanto más corto fue su tratamiento, porque libera
 *       antes los recursos para los demás.</li>
 * </ul>
 */
public class OutcomeScorer {

    /** Edad hasta la que se cuentan los años de vida (supuesto). */
    public static final int LIFE_EXPECTANCY_YEARS = 100;

    /**
     * Puntúa el desenlace de un paciente.
     *
     * @param outcome   desenlace del paciente
     * @param objective objetivo que se optimiza
     * @return puntaje de 0 a 1
     * @throws IllegalArgumentException si falta el desenlace o el objetivo
     */
    public double score(PatientOutcome outcome, Objective objective) {
        if (outcome == null || objective == null) {
            throw new IllegalArgumentException("El desenlace y el objetivo son obligatorios.");
        }
        if (!survived(outcome)) {
            return 0.0;
        }
        return switch (objective) {
            case MAXIMIZE_LIVES -> 1.0;
            case MAXIMIZE_LIFE_YEARS ->
                    Math.max(0, LIFE_EXPECTANCY_YEARS - outcome.age()) / (double) LIFE_EXPECTANCY_YEARS;
            case EQUITY -> 0.5 + 0.5 * timeFraction(outcome.waitingMinutes());
            case MINIMIZE_WAIT -> 1.0 - timeFraction(outcome.outcomeMinute() - outcome.treatmentStartMinute());
        };
    }

    private static boolean survived(PatientOutcome outcome) {
        return outcome.status() == PatientStatus.DISCHARGED
                || outcome.status() == PatientStatus.STABILIZED_WITHOUT_CURE;
    }

    private static double timeFraction(int minutes) {
        return Math.min(minutes, FeatureExtractor.TIME_SCALE_MINUTES) / (double) FeatureExtractor.TIME_SCALE_MINUTES;
    }
}