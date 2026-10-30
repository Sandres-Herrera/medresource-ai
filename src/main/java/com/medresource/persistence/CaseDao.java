package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.SimulationCase;

import java.util.Optional;

/**
 * Acceso a los casos de simulación guardados.
 *
 * <p>Un caso se identifica por su configuración (semilla, escenario, cantidad de pacientes y duración):
 * no puede haber dos casos guardados con la misma. Los casos no se modifican, porque con la misma
 * configuración el generador produce siempre los mismos pacientes.</p>
 */
public interface CaseDao extends GenericDao<SimulationCase, Integer> {

    /**
     * Busca el caso guardado con una configuración.
     *
     * @param config configuración buscada
     * @return el identificador del caso, o vacío si todavía no se ha guardado
     */
    Optional<Integer> findIdByConfig(CaseConfig config);
}