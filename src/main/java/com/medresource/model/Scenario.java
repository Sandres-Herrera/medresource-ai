package com.medresource.model;

/**
 * Escenario de simulación que define cómo llegan los pacientes.
 */
public enum Scenario {

    NORMAL('A', "Hospital normal"),
    HIGH_DEMAND('B', "Alta demanda"),
    CATASTROPHE('C', "Catástrofe"),
    UNEXPECTED_EMERGENCIES('D', "Emergencias inesperadas");

    private final char code;
    private final String displayName;

    Scenario(char code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * Devuelve la letra del escenario (A, B, C o D).
     *
     * @return código del escenario
     */
    public char getCode() {
        return code;
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