package com.medresource.ui.controller;

import com.medresource.model.EventType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Grupos de eventos que se pueden elegir en el filtro del registro de eventos.
 *
 * <p>{@link #ALL} deja pasar todos los eventos. Cada uno de los demás grupos reúne tipos de evento de una misma
 * parte del recorrido del paciente, y cada tipo de evento está en uno solo de ellos.</p>
 */
public enum EventCategory {

    /** Todos los eventos, sin filtrar. */
    ALL("Todos los eventos"),
    /** Llegadas, alertas, triage, diagnóstico y reevaluación. */
    ARRIVALS("Llegadas y triage", EventType.ARRIVAL, EventType.EMERGENCY_ALERT, EventType.TRIAGE_STARTED,
            EventType.TRIAGE_ASSIGNED, EventType.DIAGNOSIS, EventType.REEVALUATION),
    /** Primeros auxilios, tratamientos, UCI y recuperación. */
    CARE("Atención", EventType.FIRST_AID, EventType.TREATMENT_STARTED, EventType.TREATMENT_FINISHED,
            EventType.ICU_ADMISSION, EventType.RECOVERY_STARTED),
    /** Cómo terminan los pacientes que salen vivos o sin atención. */
    OUTCOMES("Altas, remisiones y sin atender", EventType.DISCHARGE, EventType.STABILIZED_WITHOUT_CURE,
            EventType.REFERRAL, EventType.UNATTENDED),
    /** Fallecimientos y movimientos de la morgue. */
    DEATHS("Fallecimientos y morgue", EventType.DEATH, EventType.MORGUE_ADMISSION, EventType.MORGUE_RELEASE);

    private final String displayName;
    private final Set<EventType> types;

    EventCategory(String displayName, EventType... types) {
        this.displayName = displayName;
        this.types = types.length == 0 ? EnumSet.allOf(EventType.class) : EnumSet.of(types[0], types);
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Indica si un tipo de evento pertenece a este grupo.
     *
     * @param type tipo de evento
     * @return {@code true} si el grupo lo incluye; {@link #ALL} incluye todos
     * @throws IllegalArgumentException si el tipo es nulo
     */
    public boolean includes(EventType type) {
        if (type == null) {
            throw new IllegalArgumentException("El tipo de evento es obligatorio.");
        }
        return types.contains(type);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
