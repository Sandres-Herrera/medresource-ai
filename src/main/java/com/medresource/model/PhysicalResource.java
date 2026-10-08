package com.medresource.model;

/**
 * Recurso físico del hospital que se asigna a un paciente: una cama o un quirófano.
 */
public abstract class PhysicalResource implements Assignable {

    private final String code;
    private final Occupancy occupancy;

    /**
     * @param code código visible, ej {@code H1-C2}
     * @throws IllegalArgumentException si el código está vacío
     */
    protected PhysicalResource(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código del recurso es obligatorio.");
        }
        this.code = code;
        this.occupancy = new Occupancy(code);
    }

    /**
     * Devuelve una descripción corta para mostrar en la interfaz.
     */
    public abstract String describe();

    @Override
    public boolean isAvailable() {
        return occupancy.isFree();
    }

    /**
     * @throws IllegalArgumentException si el paciente es nulo, ya terminó su
     *                                  atención o el minuto es negativo
     * @throws IllegalStateException    si el recurso ya está ocupado
     */
    @Override
    public void assign(Patient patient, int untilMinute) {
        occupancy.occupy(patient, untilMinute);
    }

    @Override
    public void release() {
        occupancy.free();
    }

    @Override
    public Patient getAssignedPatient() {
        return occupancy.getPatient();
    }

    @Override
    public int getBusyUntil() {
        return occupancy.getBusyUntil();
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return describe();
    }
}