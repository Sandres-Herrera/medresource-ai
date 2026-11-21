package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.GeneratedReport;
import com.medresource.model.NarrativeSource;
import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
import com.medresource.model.Scenario;

import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guarda y lee en Oracle los reportes de fin de simulación.
 *
 * <p>Cada reporte ocupa una fila de {@code reporte}: la ejecución de la que salió, quién redactó el texto, el
 * reporte completo como JSON (ver {@link ReportJson}) y la fecha en que se guardó, que pone Oracle. Este DAO no
 * tiene SQL: llama a los procedimientos {@code pr_*_reporte} de {@code db/plsql.sql}, y la lista de reportes
 * ({@link #summaries()}) lee la vista {@code v_reporte_resumen}, que cuenta los fallecidos.</p>
 *
 * <p>Los reportes no se modifican: {@link #update(SavedReport)} no está permitido. Al borrar la ejecución, Oracle
 * borra también su reporte.</p>
 */
public class OracleReportDao implements ReportDao {

    /** {@code pr_guardar_reporte(id_ejecucion, origen, contenido_json, id)}: el último parámetro sale. */
    static final String INSERT_REPORT = "{call pr_guardar_reporte(?, ?, ?, ?)}";
    /** {@code pr_buscar_reporte(id, cursor)}. */
    static final String SELECT_REPORT = "{call pr_buscar_reporte(?, ?)}";
    /** {@code pr_reporte_de_ejecucion(id_ejecucion, cursor)}. */
    static final String SELECT_REPORT_BY_RESULT = "{call pr_reporte_de_ejecucion(?, ?)}";
    /** {@code pr_listar_reportes(cursor)}. */
    static final String SELECT_REPORTS = "{call pr_listar_reportes(?)}";
    /** {@code pr_listar_resumen_reportes(cursor)}. */
    static final String SELECT_SUMMARIES = "{call pr_listar_resumen_reportes(?)}";
    /** {@code pr_borrar_reporte(id, borrados)}: el segundo parámetro dice cuántas filas se borraron. */
    static final String DELETE_REPORT = "{call pr_borrar_reporte(?, ?)}";

    /** Posiciones de los parámetros de salida de cada procedimiento. */
    private static final int SAVED_ID_POSITION = 4;
    private static final int DELETED_COUNT_POSITION = 2;

    private final TransactionRunner transactions;

    /**
     * Crea el DAO.
     *
     * @param connections administrador de conexiones
     * @throws IllegalArgumentException si es nulo
     */
    public OracleReportDao(ConnectionManager connections) {
        this.transactions = new TransactionRunner(connections);
    }

    // ---------- guardar y borrar ----------

    /**
     * Guarda un reporte nuevo.
     *
     * @param report reporte que se guarda, todavía sin identificador
     * @return el identificador que Oracle le asignó
     * @throws IllegalArgumentException si el reporte es nulo o ya está guardado
     * @throws DataAccessException      si la ejecución no existe, si ya tiene reporte o si Oracle rechaza la
     *                                  operación
     */
    @Override
    public Integer save(SavedReport report) {
        if (report == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        if (report.isSaved()) {
            throw new IllegalArgumentException("El reporte " + report.id() + " ya está guardado.");
        }
        String json = ReportJson.toJson(report.report());
        return transactions.run("guardar el reporte de la ejecución " + report.resultId(), connection -> {
            try (CallableStatement call = connection.prepareCall(INSERT_REPORT)) {
                call.setInt(1, report.resultId());
                call.setString(2, report.report().narrative().source().name());
                call.setCharacterStream(3, new StringReader(json), json.length());
                call.registerOutParameter(SAVED_ID_POSITION, Types.INTEGER);
                call.execute();
                return call.getInt(SAVED_ID_POSITION);
            }
        });
    }

    /**
     * Los reportes no se modifican.
     *
     * @param report no se usa
     * @throws UnsupportedOperationException siempre
     */
    @Override
    public void update(SavedReport report) {
        throw new UnsupportedOperationException("Los reportes guardados no se modifican.");
    }

    /**
     * Borra un reporte.
     *
     * @param id identificador del reporte
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si el reporte no existe o Oracle rechaza la operación
     */
    @Override
    public void deleteById(Integer id) {
        requireId(id);
        transactions.run("borrar el reporte " + id, connection -> {
            try (CallableStatement call = connection.prepareCall(DELETE_REPORT)) {
                call.setInt(1, id);
                call.registerOutParameter(DELETED_COUNT_POSITION, Types.INTEGER);
                call.execute();
                if (call.getInt(DELETED_COUNT_POSITION) == 0) {
                    throw new DataAccessException("No existe el reporte " + id + ".");
                }
            }
            return null;
        });
    }

    // ---------- leer ----------

    /**
     * Busca un reporte por su identificador.
     *
     * @param id identificador buscado
     * @return el reporte completo, o vacío si no existe
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si Oracle rechaza la operación o el reporte guardado está dañado
     */
    @Override
    public Optional<SavedReport> findById(Integer id) {
        requireId(id);
        return transactions.run("buscar el reporte " + id,
                connection -> load(connection, SELECT_REPORT, id).stream().findFirst());
    }

    /**
     * Busca el reporte de una ejecución.
     *
     * @param resultId identificador de la ejecución
     * @return el reporte completo, o vacío si esa ejecución no tiene reporte
     * @throws DataAccessException si Oracle rechaza la operación o el reporte guardado está dañado
     */
    @Override
    public Optional<SavedReport> findByResult(int resultId) {
        return transactions.run("buscar el reporte de la ejecución " + resultId,
                connection -> load(connection, SELECT_REPORT_BY_RESULT, resultId).stream().findFirst());
    }

    /**
     * Lista todos los reportes completos, ordenados por identificador. Para mostrar la lista conviene
     * {@link #summaries()}, que no lee los reportes.
     *
     * @return reportes guardados; lista vacía si no hay ninguno
     * @throws DataAccessException si Oracle rechaza la operación o algún reporte está dañado
     */
    @Override
    public List<SavedReport> findAll() {
        return transactions.run("listar los reportes", connection -> load(connection, SELECT_REPORTS, null));
    }

    /**
     * Lista los reportes guardados sin cargarlos completos, del más reciente al más antiguo.
     *
     * @return un resumen por reporte; lista vacía si no hay ninguno
     * @throws DataAccessException si Oracle rechaza la operación o algún dato guardado no es válido
     */
    @Override
    public List<SavedReportSummary> summaries() {
        return transactions.run("listar los reportes guardados", connection -> {
            List<SavedReportSummary> summaries = new ArrayList<>();
            try (CallableStatement call = connection.prepareCall(SELECT_SUMMARIES)) {
                call.registerOutParameter(1, Types.REF_CURSOR);
                call.execute();
                try (ResultSet rows = (ResultSet) call.getObject(1)) {
                    while (rows.next()) {
                        summaries.add(toSummary(rows));
                    }
                }
            }
            return summaries;
        });
    }

    /**
     * Ejecuta un procedimiento que devuelve reportes en su cursor. El cursor es el último parámetro: si el
     * procedimiento recibe un identificador, es el segundo; si no recibe nada, es el primero.
     */
    private static List<SavedReport> load(Connection connection, String sql, Integer parameter)
            throws SQLException {
        List<SavedReport> reports = new ArrayList<>();
        try (CallableStatement call = connection.prepareCall(sql)) {
            int cursor = 1;
            if (parameter != null) {
                call.setInt(1, parameter);
                cursor = 2;
            }
            call.registerOutParameter(cursor, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(cursor)) {
                while (rows.next()) {
                    reports.add(toReport(rows));
                }
            }
        }
        return reports;
    }

    private static SavedReport toReport(ResultSet row) throws SQLException {
        int id = row.getInt("id");
        GeneratedReport report;
        try {
            report = ReportJson.fromJson(row.getString("contenido_json"));
        } catch (DataAccessException e) {
            throw new DataAccessException("El reporte " + id + " no se puede abrir: " + e.getMessage(), e);
        }
        return new SavedReport(id, row.getInt("id_ejecucion"), savedAt(row, id), report);
    }

    private static SavedReportSummary toSummary(ResultSet row) throws SQLException {
        int id = row.getInt("id");
        String owner = "El reporte " + id;
        try {
            CaseConfig config = new CaseConfig(row.getLong("semilla"), parse(Scenario.class, row.getString("escenario"),
                    owner), row.getInt("cantidad_pacientes"), row.getInt("duracion_minutos"));
            return new SavedReportSummary(id, row.getInt("id_ejecucion"), savedAt(row, id), config,
                    row.getString("estrategia"), row.getInt("fallecidos"),
                    parse(NarrativeSource.class, row.getString("origen"), owner), row.getInt("id_caso"),
                    baseRunOf(row));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException(owner + " no tiene datos válidos: " + e.getMessage(), e);
        }
    }

    private static LocalDateTime savedAt(ResultSet row, int id) throws SQLException {
        Timestamp savedAt = row.getTimestamp("guardado_en");
        if (savedAt == null) {
            throw new DataAccessException("El reporte " + id + " no tiene fecha de guardado.");
        }
        return savedAt.toLocalDateTime();
    }

    /** Ejecución base de la fila actual, o {@code null} si no tiene. */
    private static Integer baseRunOf(ResultSet row) throws SQLException {
        Object base = row.getObject("id_ejecucion_base");
        return base == null ? null : ((Number) base).intValue();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, String owner) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataAccessException(owner + " tiene un valor desconocido (" + name + ") para "
                    + type.getSimpleName() + ".", e);
        }
    }

    private static void requireId(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador del reporte es obligatorio.");
        }
    }
}
