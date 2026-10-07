package com.medresource.model;

/**
 * Tipo de tratamiento que requiere una enfermedad y los recursos que implica.
 */
public enum TreatmentType {

    CONSULTATION("Consulta", false, false, 0),
    HOSPITALIZATION("Hospitalización", true, false, 1),
    SURGERY("Cirugía", false, true, 2);

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
}