package com.medresource.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Operaciones básicas (CRUD) que ofrece el acceso a datos de cualquier entidad.
 *
 * <p>Los DAO de enfermedades, casos y resultados implementan esta misma interfaz, de modo
 * que los servicios dependen de ella y no de Oracle.</p>
 *
 * @param <T>  tipo de la entidad
 * @param <ID> tipo de su identificador
 */
public interface GenericDao<T, ID> {

    /**
     * Guarda una entidad nueva.
     *
     * @param entity entidad que se guarda
     * @return identificador con el que quedó guardada
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    ID save(T entity);

    /**
     * Busca una entidad por su identificador.
     *
     * @param id identificador buscado
     * @return la entidad, o vacío si no existe
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    Optional<T> findById(ID id);

    /**
     * Lista todas las entidades guardadas.
     *
     * @return entidades guardadas; lista vacía si no hay ninguna
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    List<T> findAll();

    /**
     * Reemplaza los datos de una entidad que ya existe.
     *
     * @param entity entidad con los datos nuevos
     * @throws DataAccessException si la entidad no existe o la base de datos rechaza la operación
     */
    void update(T entity);

    /**
     * Borra una entidad por su identificador.
     *
     * @param id identificador de la entidad
     * @throws DataAccessException si la entidad no existe o la base de datos rechaza la operación
     */
    void deleteById(ID id);
}