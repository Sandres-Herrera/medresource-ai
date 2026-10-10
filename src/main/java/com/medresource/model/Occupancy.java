package com.medresource.model;

/**
 * Ocupación de un recurso asignable: qué paciente lo tiene y hasta cuándo.
 */
final class Occupancy {

    private final String ownerCode;
    private Patient patient;
    private int busyUntil = -1;

    Occupancy(String ownerCode) {
        this.ownerCode = ownerCode;
    }

    boolean isFree() {
        return patient == null;
    }

    /**
     * @throws IllegalArgumentException si el paciente es nulo, ya terminó su
     *                                  atención o el minuto es negativo
     * @throws IllegalStateException    si el recurso ya está ocupado
     */
    void occupy(Patient newPatient, int untilMinute) {
        if (newPatient == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        if (newPatient.isFinished()) {
            throw new IllegalArgumentException(newPatient.getCode() + " ya terminó su atención.");
        }
        if (untilMinute < 0) {
            throw new IllegalArgumentException("El minuto de liberación no puede ser negativo.");
        }
        if (!isFree()) {
            throw new IllegalStateException(ownerCode + " ya está ocupado por " + patient.getCode() + ".");
        }
        this.patient = newPatient;
        this.busyUntil = untilMinute;
    }

    /**
     * @throws IllegalStateException si el recurso ya estaba libre
     */
    void free() {
        if (isFree()) {
            throw new IllegalStateException(ownerCode + " ya está libre.");
        }
        this.patient = null;
        this.busyUntil = -1;
    }

    Patient getPatient() {
        return patient;
    }

    int getBusyUntil() {
        return busyUntil;
    }
}