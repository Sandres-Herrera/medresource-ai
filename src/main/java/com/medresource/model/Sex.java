package com.medresource.model;

/**
 * Sexo biológico de una persona.
 *
 * <p>Se usa únicamente para generar pacientes coherentes con su enfermedad;
 * nunca interviene en la prioridad de atención.</p>
 */
public enum Sex {

    FEMALE("Femenino"),
    MALE("Masculino");

    private final String displayName;

    Sex(String displayName) {
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