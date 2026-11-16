package com.medresource.services.management;

import com.medresource.model.GeneratedReport;
import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
import com.medresource.persistence.ConnectionManager;
import com.medresource.persistence.DataAccessException;
import com.medresource.persistence.DatabaseConfig;
import com.medresource.persistence.OracleCaseDao;
import com.medresource.persistence.OracleDiseaseDao;
import com.medresource.persistence.OracleReportDao;
import com.medresource.persistence.OracleResultDao;
import com.medresource.persistence.ReportDao;
import com.medresource.services.simulation.CaseGenerator;

import java.util.List;
import java.util.Optional;

/**
 * Archivo de reportes: guarda en la base de datos cada reporte que se genera y permite volver a abrirlo.
 *
 * <p>Guardar un reporte guarda también lo que lo respalda: el caso simulado (si todavía no estaba), la ejecución
 * con el resultado de cada paciente y, por último, el reporte. Cada parte se guarda en su propia transacción; si
 * el reporte no se puede guardar, se borra la ejecución recién guardada para no dejarla huérfana. Antes del primer
 * guardado se cargan las enfermedades del catálogo inicial que falten, porque los pacientes las necesitan.</p>
 */
public class ReportArchive {

    private final DiseaseService diseases;
    private final CaseService cases;
    private final SimulationService runs;
    private final ReportDao reports;
    private boolean catalogReady;

    /**
     * Crea el archivo.
     *
     * @param diseases servicio de enfermedades, para cargar el catálogo inicial
     * @param cases    servicio de casos
     * @param runs     servicio de ejecuciones
     * @param reports  acceso a los reportes guardados
     * @throws IllegalArgumentException si alguno es nulo
     */
    public ReportArchive(DiseaseService diseases, CaseService cases, SimulationService runs, ReportDao reports) {
        if (diseases == null || cases == null || runs == null || reports == null) {
            throw new IllegalArgumentException("Los servicios y el acceso a los reportes son obligatorios.");
        }
        this.diseases = diseases;
        this.cases = cases;
        this.runs = runs;
        this.reports = reports;
    }

    /**
     * Crea el archivo que usa la aplicación, con Oracle, si el equipo tiene configurada la base de datos.
     *
     * @return el archivo, o vacío si no existe {@code db.properties}
     * @throws DataAccessException si {@code db.properties} existe pero no se puede leer
     */
    public static Optional<ReportArchive> fromConfiguration() {
        if (DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) == null) {
            return Optional.empty();
        }
        ConnectionManager connections = new ConnectionManager(DatabaseConfig.fromClasspath());
        OracleDiseaseDao diseaseDao = new OracleDiseaseDao(connections);
        CaseService cases = new CaseService(new OracleCaseDao(connections, diseaseDao), new CaseGenerator());
        return Optional.of(new ReportArchive(new DiseaseService(diseaseDao), cases,
                new SimulationService(cases, new OracleResultDao(connections, diseaseDao)),
                new OracleReportDao(connections)));
    }

    /**
     * Guarda un reporte con la simulación de la que salió.
     *
     * @param request datos de la simulación
     * @param report  reporte terminado
     * @return el reporte guardado, con su identificador y su fecha
     * @throws IllegalArgumentException si falta alguno
     * @throws DataAccessException      si la base de datos rechaza alguna parte; en ese caso no queda guardada la
     *                                  ejecución
     */
    public SavedReport save(ReportRequest request, GeneratedReport report) {
        if (request == null || report == null) {
            throw new IllegalArgumentException("La simulación y el reporte son obligatorios.");
        }
        prepareCatalog();
        int caseId = cases.idFor(request.caseConfig());
        int resultId = runs.save(caseId, request.hospitalConfig(), request.result(), request.baseRunId());
        int reportId;
        try {
            reportId = reports.save(SavedReport.toSave(resultId, report));
        } catch (RuntimeException e) {
            undo(resultId, e);
            throw e;
        }
        return reports.findById(reportId).orElseThrow(
                () -> new DataAccessException("El reporte " + reportId + " desapareció después de guardarse."));
    }

    /**
     * Lista los reportes guardados, del más reciente al más antiguo.
     *
     * @return un resumen por reporte; lista vacía si no hay ninguno
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    public List<SavedReportSummary> summaries() {
        return reports.summaries();
    }

    /**
     * Abre un reporte guardado.
     *
     * @param id identificador del reporte
     * @return el reporte completo, o vacío si no existe
     * @throws DataAccessException si la base de datos rechaza la operación o el reporte está dañado
     */
    public Optional<SavedReport> find(int id) {
        return reports.findById(id);
    }

    private void prepareCatalog() {
        if (!catalogReady) {
            diseases.loadDefaultCatalog();
            catalogReady = true;
        }
    }

    /** Borra la ejecución que quedó sin reporte; si tampoco se puede, lo anota en el error original. */
    private void undo(int resultId, RuntimeException cause) {
        try {
            runs.delete(resultId);
        } catch (RuntimeException e) {
            cause.addSuppressed(e);
        }
    }
}
