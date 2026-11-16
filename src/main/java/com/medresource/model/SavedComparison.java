package com.medresource.model;

import java.time.LocalDateTime;

/**
 * Una comparación guardada en la base de datos, con su identificador y la fecha en que se guardó.
 *
 * @param id         identificador de la comparación
 * @param savedAt    cuándo se guardó
 * @param comparison la comparación completa, tal como se vio al guardarla
 */
public record SavedComparison(int id, LocalDateTime savedAt, RunComparison comparison) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si el identificador no es positivo o falta la fecha o la comparación
     */
    public SavedComparison {
        if (id < 1) {
            throw new IllegalArgumentException("La comparación guardada necesita un identificador positivo.");
        }
        if (savedAt == null || comparison == null) {
            throw new IllegalArgumentException("La fecha y la comparación son obligatorias.");
        }
    }

    /**
     * Título para la lista de comparaciones, por ejemplo «Comparación #3 · Ejecución #1 vs #2 · Estrategias».
     *
     * @return el título
     */
    public String title() {
        return "Comparación #" + id + " · " + comparison.title();
    }
}
