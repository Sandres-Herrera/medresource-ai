package com.medresource.model;

/**
 * Especialidad de un médico; determina qué enfermedades puede tratar.
 */
public enum Specialty {

    GENERAL_MEDICINE("Medicina general"),
    PEDIATRICS("Pediatría"),
    INTERNAL_MEDICINE("Medicina interna"),
    SURGERY("Cirugía"),
    TRAUMATOLOGY("Traumatología"),
    EMERGENCY_MEDICINE("Medicina de urgencias");

    private final String displayName;

    Specialty(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Devuelve el nombre que se muestra al usuario.
     *
     * @return nombre en español
     */
    public String getDisplayName() {
        return displayName;
    }
}