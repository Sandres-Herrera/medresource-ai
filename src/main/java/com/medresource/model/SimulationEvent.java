package com.medresource.model;

/**
 * Hecho que ocurre durante la simulación, por ejemplo la llegada de un
 * paciente o el inicio de su tratamiento.
 *
 * <p>Los eventos forman el registro de la simulación. La interfaz gráfica los
 * reproduce para animar a los pacientes, y por eso cada uno indica el recurso
 * donde ocurre.</p>
 *
 * @param minute       minuto de la simulación en que ocurre
 * @param type         tipo de evento
 * @param patientCode  código del paciente, por ejemplo {@code P001}
 * @param resourceCode código del lugar donde está el paciente cuando ocurre
 *                     (quirófano, cama o consultorio del médico); {@code null}
 *                     si está en la sala de espera o fuera del hospital
 * @param description  texto para mostrar al usuario
 */
public record SimulationEvent(int minute, EventType type, String patientCode, String resourceCode,
                              String description) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si el minuto es negativo o falta el tipo,
     *                                  el código del paciente o la descripción
     */
    public SimulationEvent {
        if (minute < 0) {
            throw new IllegalArgumentException("El minuto del evento no puede ser negativo.");
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo de evento es obligatorio.");
        }
        if (patientCode == null || patientCode.isBlank()) {
            throw new IllegalArgumentException("El código del paciente es obligatorio.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("La descripción del evento es obligatoria.");
        }
    }
}
