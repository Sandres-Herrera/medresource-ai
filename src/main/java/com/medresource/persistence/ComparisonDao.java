package com.medresource.persistence;

import com.medresource.model.RunComparison;
import com.medresource.model.SavedComparison;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a las comparaciones guardadas.
 *
 * <p>Una comparación se guarda solo cuando el usuario lo pide. El mismo par de ejecuciones, en cualquier orden, se
 * guarda una sola vez: guardarlo de nuevo devuelve la que ya estaba. Las comparaciones no se modifican.</p>
 */
public interface ComparisonDao {

    /**
     * Guarda una comparación, o devuelve la que ya estaba guardada para el mismo par de ejecuciones.
     *
     * @param comparison comparación que se guarda
     * @return la comparación guardada, con su identificador y su fecha
     * @throws IllegalArgumentException si la comparación es nula
     * @throws DataAccessException      si la base de datos no acepta la comparación (el mensaje dice por qué) o
     *                                  rechaza la operación
     */
    SavedComparison save(RunComparison comparison);

    /**
     * Busca una comparación por su identificador.
     *
     * @param id identificador
     * @return la comparación, o vacío si no existe
     * @throws DataAccessException si la base de datos rechaza la operación o la comparación está dañada
     */
    Optional<SavedComparison> findById(int id);

    /**
     * Busca la comparación guardada de un par de ejecuciones, en cualquier orden.
     *
     * @param runA una ejecución
     * @param runB la otra
     * @return la comparación, o vacío si ese par no se ha guardado
     * @throws DataAccessException si la base de datos rechaza la operación o la comparación está dañada
     */
    Optional<SavedComparison> findByRuns(int runA, int runB);

    /**
     * Lista las comparaciones guardadas, de la más reciente a la más antigua.
     *
     * @return comparaciones guardadas; lista vacía si no hay ninguna
     * @throws DataAccessException si la base de datos rechaza la operación o alguna comparación está dañada
     */
    List<SavedComparison> findAll();

    /**
     * Borra una comparación guardada; las ejecuciones y sus reportes no cambian.
     *
     * @param id identificador
     * @throws DataAccessException si la comparación no existe o la base de datos rechaza la operación
     */
    void deleteById(int id);
}
