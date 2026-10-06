package com.medresource.model;

/**
 * Objetivo que optimiza la estrategia de inteligencia artificial.
 *
 * <p>Distintos objetivos pueden producir decisiones distintas; esa diferencia
 * es parte del análisis ético del proyecto.</p>
 */
public enum Objective {

    MAXIMIZE_LIVES("Maximizar vidas salvadas"),
    MAXIMIZE_LIFE_YEARS("Maximizar años de vida salvados"),
    EQUITY("Equidad entre pacientes"),
    MINIMIZE_WAIT("Minimizar el tiempo de espera");

    private final String displayName;

    Objective(String displayName) {
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