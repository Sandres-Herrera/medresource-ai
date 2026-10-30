package com.medresource.persistence;

import com.medresource.model.RunRecord;

import java.util.List;

/**
 * Acceso a las ejecuciones guardadas: el resultado de cada paciente y el registro de eventos de una
 * estrategia sobre un caso.
 *
 * <p>Las ejecuciones no se modifican: una ejecución nueva es otra ejecución.</p>
 */
public interface ResultDao extends GenericDao<RunRecord, Integer> {

    /**
     * Busca las ejecuciones guardadas de un caso.
     *
     * @param caseId identificador del caso
     * @return identificadores de las ejecuciones, ordenados; lista vacía si el caso no tiene ninguna
     */
    List<Integer> findIdsByCase(int caseId);
}