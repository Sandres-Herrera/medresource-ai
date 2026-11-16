package com.medresource.model;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Lo que se muestra de un reporte guardado en la lista de reportes, sin cargar el reporte completo.
 *
 * @param id           identificador del reporte
 * @param resultId     identificador de la ejecución de la que salió
 * @param savedAt      cuándo se guardó
 * @param config       caso simulado: escenario, semilla, pacientes y duración de las llegadas
 * @param strategyName estrategia con la que se atendió
 * @param deaths       pacientes que fallecieron
 * @param source       quién redactó el texto: la IA o las plantillas
 * @param caseId       identificador del caso de la ejecución
 * @param baseRunId    ejecución del mismo caso en la que se basa la ejecución; {@code null} si es una simulación
 *                     nueva
 */
public record SavedReportSummary(int id, int resultId, LocalDateTime savedAt, CaseConfig config,
                                 String strategyName, int deaths, NarrativeSource source, int caseId,
                                 Integer baseRunId) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si los identificadores no son positivos, si falta algún dato, si los
     *                                  fallecidos son negativos o más que los pacientes, o si la ejecución se basa
     *                                  en sí misma
     */
    public SavedReportSummary {
        if (id < 1 || resultId < 1 || caseId < 1) {
            throw new IllegalArgumentException(
                    "El reporte, la ejecución y el caso necesitan identificadores positivos.");
        }
        if (baseRunId != null && (baseRunId < 1 || baseRunId == resultId)) {
            throw new IllegalArgumentException(
                    "La ejecución base debe ser otra ejecución, con identificador positivo.");
        }
        if (savedAt == null || config == null || source == null || strategyName == null
                || strategyName.isBlank()) {
            throw new IllegalArgumentException("Faltan datos del reporte guardado.");
        }
        if (deaths < 0 || deaths > config.patientCount()) {
            throw new IllegalArgumentException("Los fallecidos deben estar entre 0 y la cantidad de pacientes.");
        }
    }

    /**
     * Devuelve la ejecución en la que se basa esta.
     *
     * @return su identificador, o vacío si es una simulación nueva
     */
    public Optional<Integer> basedOn() {
        return Optional.ofNullable(baseRunId);
    }

    /**
     * Calcula la mortalidad de la ejecución.
     *
     * @return fallecidos sobre pacientes, de 0 a 1
     */
    public double mortalityRate() {
        return (double) deaths / config.patientCount();
    }
}
