package com.medresource.model;

/**
 * Tipo de tratamiento que requiere una enfermedad y los recursos que implica.
 *
 * <p>En una consulta y en una cirugía el médico y los enfermeros acompañan al paciente todo el tiempo. En una
 * hospitalización (en cama o en UCI) solo lo acompañan durante el ingreso, los primeros
 * {@value #INITIAL_CARE_MINUTES} minutos; después el paciente sigue en su cama a cargo de enfermería y el
 * médico queda libre para atender a otros.</p>
 *
 * <p>Una cirugía tiene dos fases: la operación en el quirófano, que dura la parte
 * {@value #OPERATION_SHARE} del tratamiento, y la recuperación en una cama (o en UCI si la enfermedad lo
 * requiere) durante el resto.</p>
 */
public enum TreatmentType {

    CONSULTATION("Consulta", false, false, 0),
    HOSPITALIZATION("Hospitalización", true, false, 1),
    SURGERY("Cirugía", false, true, 2);

    /** Minutos que dura el ingreso de un paciente hospitalizado con su médico y su enfermero (supuesto). */
    public static final int INITIAL_CARE_MINUTES = 30;

    /** Parte del tratamiento de una cirugía que ocurre en el quirófano; el resto es recuperación (supuesto). */
    public static final double OPERATION_SHARE = 0.5;

    private final String displayName;
    private final boolean requiresBed;
    private final boolean requiresOperatingRoom;
    private final int requiredNurses;

    TreatmentType(String displayName, boolean requiresBed, boolean requiresOperatingRoom, int requiredNurses) {
        this.displayName = displayName;
        this.requiresBed = requiresBed;
        this.requiresOperatingRoom = requiresOperatingRoom;
        this.requiredNurses = requiredNurses;
    }

    /**
     * Devuelve el nombre que se muestra al usuario.
     *
     * @return nombre en español
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Indica si el tratamiento ocupa una cama.
     *
     * @return {@code true} si requiere cama
     */
    public boolean requiresBed() {
        return requiresBed;
    }

    /**
     * Indica si el tratamiento ocupa un quirófano.
     *
     * @return {@code true} si requiere quirófano
     */
    public boolean requiresOperatingRoom() {
        return requiresOperatingRoom;
    }

    /**
     * Devuelve el número de enfermeros que necesita el tratamiento.
     *
     * @return cantidad de enfermeros
     */
    public int getRequiredNurses() {
        return requiredNurses;
    }

    /**
     * Minutos que el médico y los enfermeros pasan con el paciente: en una hospitalización, solo el ingreso
     * (o todo el tratamiento si dura menos); en una consulta o una cirugía, todo el tratamiento.
     *
     * @param treatmentMinutes duración total del tratamiento
     * @return minutos con el personal, entre 1 y {@code treatmentMinutes}
     * @throws IllegalArgumentException si la duración es menor que 1
     */
    public int staffMinutes(int treatmentMinutes) {
        if (treatmentMinutes < 1) {
            throw new IllegalArgumentException("La duración del tratamiento debe ser de al menos un minuto.");
        }
        return this == HOSPITALIZATION ? Math.min(INITIAL_CARE_MINUTES, treatmentMinutes) : treatmentMinutes;
    }

    /** Indica si el tratamiento termina con una recuperación en cama después del quirófano. */
    public boolean hasRecovery() {
        return this == SURGERY;
    }

    /**
     * Minutos que dura la operación de una cirugía: la parte {@value #OPERATION_SHARE} del tratamiento,
     * redondeada hacia arriba, al menos 1 minuto.
     *
     * @param treatmentMinutes duración total del tratamiento
     * @return minutos en el quirófano, entre 1 y {@code treatmentMinutes}
     * @throws IllegalArgumentException si la duración es menor que 1
     * @throws IllegalStateException    si el tratamiento no es una cirugía
     */
    public int operationMinutes(int treatmentMinutes) {
        if (treatmentMinutes < 1) {
            throw new IllegalArgumentException("La duración del tratamiento debe ser de al menos un minuto.");
        }
        if (this != SURGERY) {
            throw new IllegalStateException(displayName + " no tiene operación.");
        }
        return (int) Math.ceil(treatmentMinutes * OPERATION_SHARE);
    }
}
