package com.medresource.ui.controller;

import com.medresource.model.SimulationEvent;

/**
 * Qué eventos se ven en el registro: los de un grupo y, si se indica, solo los de un paciente.
 *
 * @param category    grupo de eventos que se muestra
 * @param patientCode código del único paciente cuyos eventos se muestran; nulo para mostrar los de todos
 */
public record EventFilter(EventCategory category, String patientCode) {

    /** Filtro que deja pasar todos los eventos de todos los pacientes. */
    public static final EventFilter ALL = new EventFilter(EventCategory.ALL, null);

    /**
     * Valida el filtro.
     *
     * @throws IllegalArgumentException si falta el grupo o el código del paciente está en blanco
     */
    public EventFilter {
        if (category == null) {
            throw new IllegalArgumentException("El grupo de eventos es obligatorio.");
        }
        if (patientCode != null && patientCode.isBlank()) {
            throw new IllegalArgumentException("El código del paciente no puede estar en blanco.");
        }
    }

    /**
     * Indica si un evento se ve con este filtro.
     *
     * @param event evento de la simulación
     * @return {@code true} si su tipo está en el grupo y, si hay paciente, es de ese paciente
     * @throws IllegalArgumentException si el evento es nulo
     */
    public boolean accepts(SimulationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("El evento es obligatorio.");
        }
        return category.includes(event.type()) && (patientCode == null || patientCode.equals(event.patientCode()));
    }
}
