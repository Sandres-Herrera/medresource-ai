package com.medresource.model;

/**
 * Qué cambió entre dos ejecuciones del mismo caso, y por lo tanto qué mide su comparación.
 *
 * <p>Se decide mirando las configuraciones reales de las dos ejecuciones, no lo que recomendó el reporte: si
 * solo cambió la estrategia se comparan estrategias; si solo cambiaron los recursos del hospital, recursos; y si
 * cambiaron las dos cosas, la comparación es mixta y no se puede saber cuál de los dos cambios causó la
 * diferencia.</p>
 */
public enum ComparisonType {

    /** Mismo hospital, distinta estrategia. */
    STRATEGY("Estrategias", "Mismo hospital, distinta estrategia de atención."),
    /** Misma estrategia, distintos recursos. */
    RESOURCES("Recursos", "Misma estrategia, distintos recursos del hospital."),
    /** Cambiaron la estrategia y los recursos. */
    MIXED("Mixta", "Cambiaron la estrategia y los recursos: no se puede saber cuál de los dos causó la diferencia.");

    private final String displayName;
    private final String description;

    ComparisonType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Decide el tipo según lo que cambió.
     *
     * @param strategyChanged  {@code true} si las ejecuciones usaron estrategias distintas
     * @param resourcesChanged {@code true} si los hospitales tenían recursos distintos
     * @return el tipo de comparación
     * @throws IllegalArgumentException si no cambió nada: las dos ejecuciones darían los mismos resultados
     */
    public static ComparisonType of(boolean strategyChanged, boolean resourcesChanged) {
        if (strategyChanged && resourcesChanged) {
            return MIXED;
        }
        if (strategyChanged) {
            return STRATEGY;
        }
        if (resourcesChanged) {
            return RESOURCES;
        }
        throw new IllegalArgumentException(
                "Las dos ejecuciones tienen la misma estrategia y los mismos recursos: darían los mismos resultados.");
    }
}
