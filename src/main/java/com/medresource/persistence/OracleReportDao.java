package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.GeneratedReport;
import com.medresource.model.NarrativeSource;
import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
import com.medresource.model.Scenario;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guarda y lee en Oracle los reportes de fin de simulación.
 *
 * <p>Cada reporte ocupa una fila de {@code reporte}: la ejecución de la que salió, quién redactó el texto, el
 * reporte completo como JSON (ver {@link ReportJson}) y la fecha en que se guardó, que pone Oracle. La lista de
 * reportes ({@link #summaries()}) no lee el JSON: toma el escenario, la semilla y la estrategia de
 * {@code caso} y {@code ejecucion}, y cuenta los fallecidos en {@code resultado_paciente}.</p>
 *
 * <p>Los reportes no se modifican: {@link #update(SavedReport)} no está permitido. Al borrar la ejecución, Oracle
 * borra también su reporte.</p>
 */
public class OracleReportDao implements ReportDao {

    static final String INSERT_REPORT = "INSERT INTO reporte (id_ejecucion, origen, contenido_json) VALUES (?, ?, ?)";
    static final String SELECT_REPORT =
            "SELECT id, id_ejecucion, guardado_en, contenido_json FROM reporte WHERE id = ?";
    static final String SELECT_REPORT_BY_RESULT =
            "SELECT id, id_ejecucion, guardado_en, contenido_json FROM reporte WHERE id_ejecucion = ?";
    static final String SELECT_REPORTS =
            "SELECT id, id_ejecucion, guardado_en, contenido_json FROM reporte ORDER BY id";
    static final String SELECT_SUMMARIES = "SELECT p.id, p.id_ejecucion, p.guardado_en, p.origen, r.estrategia, "
            + "r.id_ejecucion_base, c.id AS id_caso, c.semilla, c.escenario, c.cantidad_pacientes, "
            + "c.duracion_minutos, "
            + "(SELECT COUNT(*) FROM resultado_paciente d "
            + "WHERE d.id_ejecucion = p.id_ejecucion AND d.estado = 'DECEASED') "
            + "AS fallecidos "
            + "FROM reporte p JOIN ejecucion r ON r.id = p.id_ejecucion JOIN caso c ON c.id = r.id_caso "
            + "ORDER BY p.guardado_en DESC, p.id DESC";
    static final String DELETE_REPORT = "DELETE FROM reporte WHERE id = ?";

    /** Nombre de la columna que Oracle genera al insertar un reporte. */
    private static final String GENERATED_COLUMN = "ID";

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
            try (PreparedStatement statement = connection.prepareStatement(INSERT_REPORT,
                    new String[]{GENERATED_COLUMN})) {
                statement.setInt(1, report.resultId());
                statement.setString(2, report.report().narrative().source().name());
                statement.setCharacterStream(3, new StringReader(json), json.length());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Oracle no devolvió el identificador del reporte.");
                    }
                    return keys.getInt(1);
                }
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
            try (PreparedStatement statement = connection.prepareStatement(DELETE_REPORT)) {
                statement.setInt(1, id);
                if (statement.executeUpdate() == 0) {
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
            try (PreparedStatement statement = connection.prepareStatement(SELECT_SUMMARIES);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    summaries.add(toSummary(rows));
                }
            }
            return summaries;
        });
    }

    private static List<SavedReport> load(Connection connection, String sql, Integer parameter)
            throws SQLException {
        List<SavedReport> reports = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setInt(1, parameter);
            }
            try (ResultSet rows = statement.executeQuery()) {
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
