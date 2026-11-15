package com.medresource.persistence;

import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a los reportes de fin de simulación guardados.
 *
 * <p>Cada reporte pertenece a una ejecución guardada y una ejecución tiene a lo sumo un reporte. Los reportes no
 * se modifican: se guardan tal como se generaron.</p>
 */
public interface ReportDao extends GenericDao<SavedReport, Integer> {

    /**
     * Busca el reporte de una ejecución.
     *
     * @param resultId identificador de la ejecución
     * @return el reporte, o vacío si esa ejecución no tiene reporte guardado
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    Optional<SavedReport> findByResult(int resultId);

    /**
     * Lista los reportes guardados sin cargarlos completos, del más reciente al más antiguo.
     *
     * @return un resumen por reporte; lista vacía si no hay ninguno
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    List<SavedReportSummary> summaries();
}
