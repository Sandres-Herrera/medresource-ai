package com.medresource.model;

/**
 * Qué tan adecuado es un médico para tratar una enfermedad, según su
 * especialidad y la edad del paciente.
 */
public enum CompatibilityLevel {

    PRIMARY("Especialidad principal", 1.0),
    SECONDARY("Especialidad secundaria", 0.6),
    NOT_SUITABLE("No apto", 0.0);

    private final String displayName;
    private final double effectiveness;

    CompatibilityLevel(String displayName, double effectiveness) {
        this.displayName = displayName;
        this.effectiveness = effectiveness;
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
     * Devuelve la efectividad del tratamiento con este nivel (0 a 1).
     *
     * @return factor de efectividad
     */
    public double getEffectiveness() {
        return effectiveness;
    }

    /**
     * Indica si el médico puede atender al paciente.
     *
     * @return {@code true} si el nivel no es {@link #NOT_SUITABLE}
     */
    public boolean isSuitable() {
        return this != NOT_SUITABLE;
    }
}