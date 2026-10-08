package com.medresource.model;

/**
 * Miembro del personal asistencial que se asigna a un paciente.
 *
 * <p>Atiende a un solo paciente a la vez.</p>
 */
public abstract class StaffMember extends Person implements Assignable {

    /** Edad mínima para trabajar en el hospital. */
    public static final int MIN_STAFF_AGE = 18;

    private Patient assignedPatient;
    private int busyUntil = -1;

    /**
     * @throws IllegalArgumentException si algún dato no es válido o la edad
     *                                  es menor que {@link #MIN_STAFF_AGE}
     */
    protected StaffMember(int id, String code, int age, Sex sex) {
        super(id, code, age, sex);
        if (age < MIN_STAFF_AGE) {
            throw new IllegalArgumentException("El personal debe tener al menos " + MIN_STAFF_AGE + " años.");
        }
    }

    @Override
    public boolean isAvailable() {
        return assignedPatient == null;
    }

    /**
     * @throws IllegalArgumentException si el paciente es nulo, ya terminó su
     *                                  atención o el minuto es negativo
     * @throws IllegalStateException    si ya está atendiendo a otro paciente
     */
    @Override
    public void assign(Patient patient, int untilMinute) {
        if (patient == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        if (patient.isFinished()) {
            throw new IllegalArgumentException(patient.getCode() + " ya terminó su atención.");
        }
        if (untilMinute < 0) {
            throw new IllegalArgumentException("El minuto de liberación no puede ser negativo.");
        }
        if (!isAvailable()) {
            throw new IllegalStateException(getCode() + " ya está atendiendo a " + assignedPatient.getCode() + ".");
        }
        this.assignedPatient = patient;
        this.busyUntil = untilMinute;
    }

    @Override
    public void release() {
        if (isAvailable()) {
            throw new IllegalStateException(getCode() + " ya está libre.");
        }
        this.assignedPatient = null;
        this.busyUntil = -1;
    }

    @Override
    public Patient getAssignedPatient() {
        return assignedPatient;
    }

    @Override
    public int getBusyUntil() {
        return busyUntil;
    }
}