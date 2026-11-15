package com.medresource.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.medresource.model.GeneratedReport;

/**
 * Convierte un reporte terminado a texto JSON y de vuelta, para guardarlo en la base de datos.
 *
 * <p>Se guarda el reporte completo (números, análisis «¿y si…?» y texto) para que al abrirlo de nuevo se vea igual
 * que el día en que se generó, sin volver a simular ni a llamar a la IA. Algunos promedios pueden no existir (por
 * ejemplo, la espera de un nivel de triage sin pacientes), por eso se aceptan los valores especiales de los números
 * decimales.</p>
 */
public final class ReportJson {

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private ReportJson() {
    }

    /**
     * Convierte el reporte a texto JSON.
     *
     * @param report reporte terminado
     * @return el reporte como JSON
     * @throws IllegalArgumentException si el reporte es nulo
     */
    public static String toJson(GeneratedReport report) {
        if (report == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        return GSON.toJson(report);
    }

    /**
     * Lee un reporte guardado como JSON.
     *
     * @param json texto guardado en la base de datos
     * @return el reporte
     * @throws DataAccessException si el texto está vacío, no es JSON o no describe un reporte completo
     */
    public static GeneratedReport fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new DataAccessException("El reporte guardado está vacío.");
        }
        GeneratedReport report;
        try {
            report = GSON.fromJson(json, GeneratedReport.class);
        } catch (RuntimeException e) {
            // Gson envuelve aquí también la validación de los registros, por ejemplo una parte que falta.
            throw new DataAccessException("El reporte guardado está dañado y no se puede abrir.", e);
        }
        if (report == null) {
            throw new DataAccessException("El reporte guardado está vacío.");
        }
        return report;
    }
}
