package com.medresource.model;

/**
 * Recurso que se puede asignar a un paciente durante un tiempo: personal,
 * camas y quirófanos.
 *
 * <p>El recurso queda ocupado hasta que el motor de simulación lo libera con
 * {@link #release()}; {@link #getBusyUntil()} indica cuándo debería ocurrir.</p>
 */
public interface Assignable {

    boolean isAvailable();

    /**
     * Ocupa el recurso con un paciente.
     *
     * @param patient     paciente que lo recibe
     * @param untilMinute minuto previsto en que se libera
     * @throws IllegalStateException si el recurso ya está ocupado
     */
    void assign(Patient patient, int untilMinute);

    /**
     * Libera el recurso.
     *
     * @throws IllegalStateException si el recurso ya estaba libre
     */
    void release();

    /**
     * @return paciente asignado, o {@code null} si está libre
     */
    Patient getAssignedPatient();

    /**
     * @return minuto previsto de liberación, o {@code -1} si está libre
     */
    int getBusyUntil();
}