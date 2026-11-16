package com.medresource.model;

/**
 * Cómo cambió un indicador de una ejecución a otra, visto desde la segunda: si mejoró, empeoró o quedó igual.
 */
public enum Trend {

    /** La segunda ejecución está mejor en este indicador. */
    IMPROVED("Mejoró"),
    /** La segunda ejecución está peor en este indicador. */
    WORSENED("Empeoró"),
    /** Las dos ejecuciones dan lo mismo. */
    UNCHANGED("Igual");

    private final String displayName;

    Trend(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
