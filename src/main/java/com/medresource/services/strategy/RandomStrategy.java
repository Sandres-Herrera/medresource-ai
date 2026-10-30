package com.medresource.services.strategy;

import com.medresource.model.Hospital;

import java.util.Comparator;
import java.util.List;

/**
 * Estrategia que atiende en un orden al azar.
 *
 * <p>No es una estrategia para comparar, sino para generar datos: al probar órdenes
 * muy distintos, la red neuronal ve también decisiones que ni FIFO ni la prioridad
 * clínica tomarían. El "azar" sale de una función de mezcla de la semilla, el minuto
 * y el paciente, así que con la misma semilla las decisiones se repiten siempre.</p>
 */
public class RandomStrategy extends PriorityBasedStrategy {

    private final long seed;

    /**
     * Crea una estrategia aleatoria repetible.
     *
     * @param seed semilla del orden
     */
    public RandomStrategy(long seed) {
        this.seed = seed;
    }

    @Override
    public String getName() {
        return "Aleatoria (entrenamiento)";
    }

    @Override
    protected Comparator<WaitingPatient> priorityOrder(int minute, List<WaitingPatient> waiting, Hospital hospital) {
        return Comparator.comparingLong((WaitingPatient candidate) -> mix(minute, candidate.patient().getId()))
                .thenComparingInt(candidate -> candidate.patient().getId());
    }

    /** Mezcla la semilla, el minuto y el paciente en un número que parece aleatorio. */
    private long mix(int minute, int patientId) {
        long z = seed + minute * 0x9E3779B97F4A7C15L + patientId * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}