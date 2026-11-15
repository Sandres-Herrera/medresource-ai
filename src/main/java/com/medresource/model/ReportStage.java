package com.medresource.model;

/**
 * Etapas por las que pasa la generación del reporte de fin de simulación, en el orden en que se hacen.
 *
 * <p>La barra de progreso las muestra una debajo de otra: las terminadas con un visto, la actual resaltada y las
 * que faltan en gris.</p>
 */
public enum ReportStage {

    /** Calcular las métricas de la simulación que se acaba de ver. */
    METRICS("Calculando las métricas de la simulación"),
    /** Volver a simular el caso con un recurso más o con otra estrategia. */
    WHAT_IF("Probando alternativas («¿y si…?»)"),
    /** Redactar el texto con la IA o con las plantillas. */
    WRITING("Redactando el texto del reporte");

    private final String label;

    ReportStage(String label) {
        this.label = label;
    }

    /**
     * Devuelve el texto que se muestra en la barra de progreso.
     *
     * @return texto en español
     */
    public String getLabel() {
        return label;
    }

    /**
     * Indica en qué número de etapa va, contando desde 1, para mostrar «Etapa 2 de 3».
     *
     * @return número de la etapa
     */
    public int number() {
        return ordinal() + 1;
    }

    /**
     * Cuenta las etapas.
     *
     * @return cantidad de etapas
     */
    public static int count() {
        return values().length;
    }
}
