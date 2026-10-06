package com.medresource.model;

/**
 * Estado de un paciente durante la simulación.
 */
public enum PatientStatus {

    WAITING("En espera", false),
    IN_TREATMENT("En tratamiento", false),
    IN_ICU("En UCI", false),
    DISCHARGED("Dado de alta", true),
    STABILIZED_WITHOUT_CURE("Estabilizado sin cura", true),
    REFERRED("Remitido", true),
    DECEASED("Fallecido", true),
    UNATTENDED("Sin atender", true);

    private final String displayName;
    private final boolean finalStatus;

    PatientStatus(String displayName, boolean finalStatus) {
        this.displayName = displayName;
        this.finalStatus = finalStatus;
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
     * Indica si el estado es un desenlace definitivo del paciente.
     *
     * @return {@code true} si el paciente ya no sigue en el hospital
     */
    public boolean isFinal() {
        return finalStatus;
    }
}