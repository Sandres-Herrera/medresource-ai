package com.medresource.persistence;

import com.google.gson.Gson;
import com.medresource.model.RunComparison;

/**
 * Convierte una comparación de dos ejecuciones a texto JSON y de vuelta, para guardarla en la base de datos.
 *
 * <p>Se guarda completa (cambios, indicadores, conclusión y limitaciones) para que al abrirla de nuevo se vea igual
 * que el día en que se guardó. A diferencia del reporte, todos sus números existen, así que el JSON es estándar y
 * Oracle lo acepta en la restricción {@code IS JSON}.</p>
 */
public final class ComparisonJson {

    private static final Gson GSON = new Gson();

    private ComparisonJson() {
    }

    /**
     * Convierte la comparación a texto JSON.
     *
     * @param comparison comparación
     * @return la comparación como JSON
     * @throws IllegalArgumentException si la comparación es nula
     */
    public static String toJson(RunComparison comparison) {
        if (comparison == null) {
            throw new IllegalArgumentException("La comparación es obligatoria.");
        }
        return GSON.toJson(comparison);
    }

    /**
     * Lee una comparación guardada como JSON.
     *
     * @param json texto guardado en la base de datos
     * @return la comparación
     * @throws DataAccessException si el texto está vacío, no es JSON o no describe una comparación completa
     */
    public static RunComparison fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new DataAccessException("La comparación guardada está vacía.");
        }
        RunComparison comparison;
        try {
            comparison = GSON.fromJson(json, RunComparison.class);
        } catch (RuntimeException e) {
            throw new DataAccessException("La comparación guardada está dañada y no se puede abrir.", e);
        }
        if (comparison == null) {
            throw new DataAccessException("La comparación guardada está vacía.");
        }
        return comparison;
    }
}
