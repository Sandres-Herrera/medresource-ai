package com.medresource.services.strategy;

import java.util.Comparator;

/**
 * Estrategia de referencia: atiende a los pacientes por orden de llegada.
 *
 * <p>No mira la gravedad ni la edad; sirve como punto de comparación para las
 * estrategias que sí las consideran. Si dos pacientes llegaron en el mismo
 * minuto, va primero el de menor número.</p>
 */
public class FifoStrategy extends PriorityBasedStrategy {

    @Override
    public String getName() {
        return "FIFO (orden de llegada)";
    }

    @Override
    protected Comparator<WaitingPatient> priorityOrder(int minute) {
        return Comparator.comparingInt((WaitingPatient waiting) -> waiting.patient().getArrivalMinute())
                .thenComparingInt(waiting -> waiting.patient().getId());
    }
}