package com.medresource.model;

/**
 * Reporte de fin de simulación ya terminado: los números, el análisis «¿y si…?» y el texto.
 *
 * <p>Es lo que muestra la ventana del reporte cuando la barra de progreso llega al final.</p>
 *
 * @param report    números de la simulación que se vio
 * @param whatIf    análisis de las alternativas sobre el mismo caso
 * @param narrative texto del reporte, de la IA o de las plantillas
 */
public record GeneratedReport(SimulationReport report, WhatIfReport whatIf, ReportNarrative narrative) {

    /**
     * Valida que no falte ninguna parte.
     *
     * @throws IllegalArgumentException si falta alguna
     */
    public GeneratedReport {
        if (report == null || whatIf == null || narrative == null) {
            throw new IllegalArgumentException("El reporte, el análisis y el texto son obligatorios.");
        }
    }
}
