package com.medresource.model;

/**
 * Miembro del personal asistencial que se asigna a un paciente.
 *
 * <p>Atiende a un solo paciente a la vez.</p>
 */
public abstract class StaffMember extends Person implements Assignable {

    /** Edad mínima para trabajar en el hospital. */
    public static final int MIN_STAFF_AGE = 18;

    private final Occupancy occupancy;

    /**
     * @throws IllegalArgumentException si algún dato no es válido o la edad
     *                                  es menor que {@link #MIN_STAFF_AGE}
     */
    protected StaffMember(int id, String code, int age, Sex sex) {
        super(id, code, age, sex);
        if (age < MIN_STAFF_AGE) {
            throw new IllegalArgumentException("El personal debe tener al menos " + MIN_STAFF_AGE + " años.");
        }
        this.occupancy = new Occupancy(code);
    }

    @Override
    public boolean isAvailable() {
        return occupancy.isFree();
    }

    /**
     * @throws IllegalArgumentException si el paciente es nulo, ya terminó su
     *                                  atención o el minuto es negativo
     * @throws IllegalStateException    si ya está atendiendo a otro paciente
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
}