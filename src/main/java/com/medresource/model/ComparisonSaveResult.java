package com.medresource.model;

/**
 * Resultado de pedir que se guarde una comparación: la comparación guardada y si ya lo estaba de antes.
 *
 * @param saved        comparación guardada
 * @param alreadySaved {@code true} si ese par de ejecuciones ya estaba guardado y no se repitió
 */
public record ComparisonSaveResult(SavedComparison saved, boolean alreadySaved) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta la comparación guardada
     */
    public ComparisonSaveResult {
        if (saved == null) {
            throw new IllegalArgumentException("La comparación guardada es obligatoria.");
        }
    }
}
