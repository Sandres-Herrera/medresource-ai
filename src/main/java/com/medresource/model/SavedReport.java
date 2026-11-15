package com.medresource.model;

import java.time.LocalDateTime;

/**
 * Reporte de fin de simulación guardado en la base de datos, ligado a la ejecución de la que salió.
 *
 * <p>Antes de guardarlo no tiene identificador ni fecha ({@link #toSave(int, GeneratedReport)}); al leerlo de la
 * base de datos trae los dos.</p>
 *
 * @param id       identificador del reporte; 0 si todavía no se ha guardado
 * @param resultId identificador de la ejecución (tabla {@code sim_result})
 * @param savedAt  cuándo se guardó; nulo si todavía no se ha guardado
 * @param report   reporte completo: números, «¿y si…?» y texto
 */
public record SavedReport(int id, int resultId, LocalDateTime savedAt, GeneratedReport report) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si el identificador es negativo, si la ejecución no es positiva, si falta
     *                                  el reporte, o si un reporte guardado no tiene fecha (o uno sin guardar sí)
     */
    public SavedReport {
        if (id < 0 || resultId < 1) {
            throw new IllegalArgumentException("El reporte necesita una ejecución válida.");
        }
        if (report == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        if ((id == 0) != (savedAt == null)) {
            throw new IllegalArgumentException("Solo un reporte guardado tiene identificador y fecha.");
        }
    }

    /**
     * Prepara un reporte para guardarlo.
     *
     * @param resultId identificador de la ejecución de la que salió
     * @param report   reporte completo
     * @return reporte sin identificador ni fecha
     * @throws IllegalArgumentException si la ejecución no es positiva o falta el reporte
     */
    public static SavedReport toSave(int resultId, GeneratedReport report) {
        return new SavedReport(0, resultId, null, report);
    }

    /**
     * Indica si ya está guardado en la base de datos.
     *
     * @return {@code true} si tiene identificador
     */
    public boolean isSaved() {
        return id > 0;
    }
}
