package com.medresource.services.strategy;

import com.medresource.model.Disease;
import com.medresource.model.HealthModel;
import com.medresource.model.Patient;

/**
 * Estima las probabilidades de supervivencia de un paciente en espera.
 *
 * <p>Usa las mismas reglas de salud que el motor de simulación ({@link HealthModel}),
 * pero con el <b>diagnóstico</b> y no con la enfermedad real, porque eso es lo único
 * que una estrategia puede conocer. Tampoco sabe si el paciente es de los que no se
 * pueden salvar: solo cuenta con la mortalidad irreducible de la enfermedad.</p>
 *
 * <p>Es una estimación: no considera a los demás pacientes ni los recursos, solo
 * a este paciente, a un médico de cierta efectividad y a cuánto se demora en empezar.</p>
 */
public class SurvivalEstimator {

    private final HealthModel healthModel;

    /** Crea un estimador con las reglas de salud estándar. */
    public SurvivalEstimator() {
        this(new HealthModel());
    }

    /**
     * Crea un estimador con unas reglas de salud dadas.
     *
     * @param healthModel reglas de salud
     * @throws IllegalArgumentException si las reglas son nulas
     */
    public SurvivalEstimator(HealthModel healthModel) {
        if (healthModel == null) {
            throw new IllegalArgumentException("Las reglas de salud son obligatorias.");
        }
        this.healthModel = healthModel;
    }

    /**
     * Calcula en cuántos minutos moriría el paciente si nadie lo atiende.
     *
     * @param candidate paciente con su diagnóstico
     * @return minutos hasta que su salud llegue a cero, o {@link Integer#MAX_VALUE} si su
     * enfermedad no lo deteriora
     * @throws IllegalArgumentException si el paciente es nulo
     */
    public int minutesUntilDeath(WaitingPatient candidate) {
        requireCandidate(candidate);
        double rate = untreatedRate(candidate);
        if (rate <= 0.0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(candidate.patient().getHealth() / rate);
    }

    /**
     * Calcula cuánto duraría el tratamiento si empezara ahora.
     *
     * @param candidate     paciente con su diagnóstico
     * @param effectiveness efectividad del médico, mayor que 0 y hasta 1
     * @return minutos de tratamiento
     * @throws IllegalArgumentException si el paciente es nulo o la efectividad no es válida
     */
    public int treatmentMinutes(WaitingPatient candidate, double effectiveness) {
        requireCandidate(candidate);
        return healthModel.treatmentMinutes(candidate.diagnosis(), candidate.patient().getHealth(), effectiveness);
    }

    /**
     * Estima la probabilidad de que el paciente sobreviva si su tratamiento empieza
     * después de una demora.
     *
     * <p>Es cero si muere esperando o durante el tratamiento (esto último puede pasar
     * con un especialista no ideal, que no detiene del todo el deterioro). Si llega
     * vivo al final, es uno menos la mortalidad irreducible de la enfermedad.</p>
     *
     * @param candidate     paciente con su diagnóstico
     * @param effectiveness efectividad del médico, mayor que 0 y hasta 1
     * @param delayMinutes  minutos que esperará antes de empezar, cero o más
     * @return probabilidad de supervivencia, de 0 a 1
     * @throws IllegalArgumentException si el paciente es nulo, la efectividad no es válida
     *                                  o la demora es negativa
     */
    public double survivalProbability(WaitingPatient candidate, double effectiveness, int delayMinutes) {
        requireCandidate(candidate);
        if (delayMinutes < 0) {
            throw new IllegalArgumentException("La demora no puede ser negativa.");
        }
        Patient patient = candidate.patient();
        Disease diagnosis = candidate.diagnosis();

        double healthAtStart = patient.getHealth() - untreatedRate(candidate) * delayMinutes;
        if (healthAtStart <= 0.0) {
            return 0.0;
        }
        int minutes = healthModel.treatmentMinutes(diagnosis, healthAtStart, effectiveness);
        double treatedRate = healthModel.deteriorationPerMinute(diagnosis, patient.getAge(), effectiveness);
        if (healthAtStart - treatedRate * minutes <= 0.0) {
            return 0.0;
        }
        return 1.0 - diagnosis.getIrreducibleMortality();
    }

    private double untreatedRate(WaitingPatient candidate) {
        return healthModel.deteriorationPerMinute(candidate.diagnosis(), candidate.patient().getAge(), 0.0);
    }

    private static void requireCandidate(WaitingPatient candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("El paciente en espera es obligatorio.");
        }
    }
}
