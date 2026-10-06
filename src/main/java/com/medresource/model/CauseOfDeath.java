package com.medresource.model;

/**
 * Causa registrada de un fallecimiento.
 *
 * <p>Distingue las muertes evitables, que dependen de las decisiones de
 * asignación, de las inevitables.</p>
 */
public enum CauseOfDeath {

    LACK_OF_TIMELY_CARE("Falta de atención a tiempo", true),
    NO_ICU_BED("Sin cama de UCI disponible", true),
    NO_COMPATIBLE_RESOURCE("Sin recurso compatible", true),
    MISDIAGNOSIS("Diagnóstico erróneo", true),
    NON_IDEAL_SPECIALIST("Especialista no ideal", true),
    IRREDUCIBLE_MORTALITY("Mortalidad irreducible", false);

    private final String displayName;
    private final boolean avoidable;

    CauseOfDeath(String displayName, boolean avoidable) {
        this.displayName = displayName;
        this.avoidable = avoidable;
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
     * Indica si la muerte se habría podido evitar con otra asignación.
     *
     * @return {@code true} si la causa es evitable
     */
    public boolean isAvoidable() {
        return avoidable;
    }
}