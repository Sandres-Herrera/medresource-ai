package com.medresource.services.strategy;

import com.medresource.model.Patient;
import com.medresource.model.TriageLevel;

import java.util.Comparator;

/**
 * Estrategia clínica: atiende primero a quien está más grave.
 *
 * <p>El orden es el siguiente:</p>
 * <ol>
 *   <li>Nivel de triage efectivo: el triage asignado sube un nivel por cada
 *       {@value #AGING_MINUTES_PER_LEVEL} minutos de espera, sin pasar del nivel I. Así un paciente
 *       leve no espera para siempre mientras sigan llegando casos graves.</li>
 *   <li>Triage asignado: ante igual nivel efectivo, gana el que realmente es más grave.</li>
 *   <li>Salud actual: el que tiene menos salud va primero.</li>
 *   <li>Orden de llegada y, por último, el número del paciente.</li>
 * </ol>
 *
 * <p>La edad y el sexo no cambian la prioridad.</p>
 */
public class ClinicalPriorityStrategy extends PriorityBasedStrategy {

    /** Minutos de espera que suben un nivel la prioridad de un paciente (supuesto). */
    public static final int AGING_MINUTES_PER_LEVEL = 120;

    @Override
    public String getName() {
        return "Prioridad clínica (triage)";
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException al comparar si algún paciente todavía no tiene triage
     */
    @Override
    protected Comparator<WaitingPatient> priorityOrder(int minute) {
        return Comparator.comparingInt((WaitingPatient waiting) -> effectiveLevel(waiting.patient(), minute))
                .thenComparingInt(waiting -> triageOf(waiting.patient()).getLevel())
                .thenComparingDouble(waiting -> waiting.patient().getHealth())
                .thenComparingInt(waiting -> waiting.patient().getArrivalMinute())
                .thenComparingInt(waiting -> waiting.patient().getId());
    }

    private static int effectiveLevel(Patient patient, int minute) {
        int promotion = patient.waitingTime(minute) / AGING_MINUTES_PER_LEVEL;
        return Math.max(TriageLevel.I.getLevel(), triageOf(patient).getLevel() - promotion);
    }

    private static TriageLevel triageOf(Patient patient) {
        TriageLevel triage = patient.getTriageLevel();
        if (triage == null) {
            throw new IllegalStateException(patient.getCode() + " no tiene triage asignado.");
        }
        return triage;
    }
}