package com.medresource.model;

/**
 * Quién redactó el texto del reporte de fin de simulación.
 */
public enum NarrativeSource {

    AI("Redactado por IA"),
    TEMPLATE("Generado automáticamente");

    private final String label;

    NarrativeSource(String label) {
        this.label = label;
    }

    /**
     * Devuelve la etiqueta que se muestra en el reporte.
     *
     * @return etiqueta en español
     */
    public String getLabel() {
        return label;
    }
}
