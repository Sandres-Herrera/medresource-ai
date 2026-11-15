package com.medresource.persistence;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Base de datos falsa, en memoria, con la tabla de los reportes y lo que la lista de reportes lee de las
 * ejecuciones.
 *
 * <p>No es una prueba: reconoce exactamente las sentencias de {@link OracleReportDao} y las ejecuta sobre mapas,
 * con transacciones (confirmar y deshacer), el identificador generado, la fecha que pone Oracle (un minuto más por
 * cada reporte), la clave foránea hacia la ejecución y un solo reporte por ejecución. Que el SQL sea válido en
 * Oracle lo comprueba {@code OracleReportDaoIntegrationTest}.</p>
 */
final class FakeReportDatabase {

    /** Fecha en que se guarda el primer reporte. */
    static final LocalDateTime FIRST_SAVE = LocalDateTime.of(2026, 11, 15, 9, 0);

    /** Contenido de la tabla de reportes y el siguiente identificador. */
    private static final class Tables {

        int nextId = 1;
        final Map<Integer, Map<String, Object>> reports = new TreeMap<>();

        Tables copy() {
            Tables copy = new Tables();
            copy.nextId = nextId;
            reports.forEach((id, row) -> copy.reports.put(id, new LinkedHashMap<>(row)));
            return copy;
        }
    }

    private Tables committed = new Tables();
    private Tables working = committed;
    private final Map<Integer, Map<String, Object>> results = new TreeMap<>();
    private LocalDateTime clock = FIRST_SAVE;

    /** Si no es {@code null}, preparar una sentencia que contenga este texto lanza un error. */
    String failWhenContaining;

    int commits;
    int rollbacks;
    int connectionsOpened;
    int connectionsClosed;
    int statementsClosed;
    int resultSetsClosed;

    /** Administrador de conexiones que abre conexiones a esta base falsa. */
    ConnectionManager manager() {
        return new ConnectionManager(new DatabaseConfig("jdbc:falso", "usuario", "clave"), (url, user, password) -> {
            connectionsOpened++;
            return connection();
        });
    }

    /**
     * Declara una ejecución guardada, con lo que la lista de reportes lee de ella y de su caso.
     *
     * @param resultId identificador de la ejecución
     * @param strategy nombre de la estrategia
     * @param seed     semilla del caso
     * @param scenario nombre del escenario
     * @param patients pacientes del caso
     * @param deaths   pacientes fallecidos en la ejecución
     */
    void addResult(int resultId, String strategy, long seed, String scenario, int patients, int deaths) {
        Map<String, Object> row = new HashMap<>();
        row.put("strategy_name", strategy);
        row.put("seed", seed);
        row.put("scenario", scenario);
        row.put("patient_count", patients);
        row.put("duration_minutes", 480);
        row.put("deaths", deaths);
        results.put(resultId, row);
    }

    /** Simula que se borra la ejecución: Oracle borra en cascada su reporte. */
    void deleteResult(int resultId) {
        results.remove(resultId);
        committed.reports.values().removeIf(row -> row.get("result_id").equals(resultId));
        working = committed;
    }

    // ---------- lo que ven las pruebas (solo lo confirmado) ----------

    List<Integer> reportIds() {
        return new ArrayList<>(committed.reports.keySet());
    }

    Object reportColumn(int reportId, String column) {
        return committed.reports.get(reportId).get(column);
    }

    /** Cambia a la fuerza un dato guardado de un reporte, para simular datos dañados. */
    void corruptReport(int reportId, String column, Object value) {
        committed.reports.get(reportId).put(column, value);
    }

    /** Cambia a la fuerza un dato guardado de una ejecución, para simular datos dañados. */
    void corruptResult(int resultId, String column, Object value) {
        results.get(resultId).put(column, value);
    }

    // ---------- conexión ----------

    private Connection connection() {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setAutoCommit" -> {
                if (!(Boolean) args[0]) {
                    working = committed.copy();
                }
                yield null;
            }
            case "commit" -> {
                commits++;
                committed = working.copy();
                yield null;
            }
            case "rollback" -> {
                rollbacks++;
                working = committed.copy();
                yield null;
            }
            case "prepareStatement" -> prepare((String) args[0]);
            case "close" -> {
                connectionsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Connection) Proxy.newProxyInstance(FakeReportDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private PreparedStatement prepare(String sql) throws SQLException {
        if (failWhenContaining != null && sql.contains(failWhenContaining)) {
            throw new SQLException("fallo simulado");
        }
        Map<Integer, Object> parameters = new HashMap<>();
        int[] generatedKey = {0};
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setInt", "setString" -> {
                parameters.put((Integer) args[0], args[1]);
                yield null;
            }
            case "setCharacterStream" -> {
                parameters.put((Integer) args[0], read((Reader) args[1]));
                yield null;
            }
            case "executeUpdate" -> update(sql, parameters, generatedKey);
            case "getGeneratedKeys" -> resultSet(List.of(Map.of("1", generatedKey[0])));
            case "executeQuery" -> query(sql, parameters);
            case "close" -> {
                statementsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (PreparedStatement) Proxy.newProxyInstance(FakeReportDatabase.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, handler);
    }

    private static String read(Reader reader) {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        try (reader) {
            for (int read = reader.read(buffer); read >= 0; read = reader.read(buffer)) {
                text.append(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return text.toString();
    }

    // ---------- sentencias de cambio ----------

    private int update(String sql, Map<Integer, Object> p, int[] generatedKey) throws SQLException {
        switch (sql) {
            case OracleReportDao.INSERT_REPORT -> {
                Object resultId = p.get(1);
                if (!results.containsKey((Integer) resultId)) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_SIM_REPORT_RESULT) violada");
                }
                if (working.reports.values().stream().anyMatch(row -> row.get("result_id").equals(resultId))) {
                    throw new SQLException("ORA-00001: restricción única (UQ_SIM_REPORT_RESULT) violada");
                }
                if (!List.of("AI", "TEMPLATE").contains(p.get(2))) {
                    throw new SQLException("ORA-02290: restricción de control (CK_SIM_REPORT_SOURCE) violada");
                }
                int id = working.nextId++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("result_id", resultId);
                row.put("source", p.get(2));
                row.put("report_json", p.get(3));
                row.put("saved_at", Timestamp.valueOf(clock));
                clock = clock.plusMinutes(1);
                working.reports.put(id, row);
                generatedKey[0] = id;
                return 1;
            }
            case OracleReportDao.DELETE_REPORT -> {
                return working.reports.remove((Integer) p.get(1)) == null ? 0 : 1;
            }
            default -> throw new SQLException("Sentencia de cambio no reconocida: " + sql);
        }
    }

    // ---------- consultas ----------

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleReportDao.SELECT_REPORTS -> rows.addAll(working.reports.values());
            case OracleReportDao.SELECT_REPORT -> {
                Map<String, Object> row = working.reports.get((Integer) p.get(1));
                if (row != null) {
                    rows.add(row);
                }
            }
            case OracleReportDao.SELECT_REPORT_BY_RESULT -> working.reports.values().stream()
                    .filter(row -> row.get("result_id").equals(p.get(1)))
                    .forEach(rows::add);
            case OracleReportDao.SELECT_SUMMARIES -> working.reports.values().stream()
                    .sorted(Comparator.comparing((Map<String, Object> row) -> (Timestamp) row.get("saved_at"))
                            .thenComparing(row -> (Integer) row.get("id")).reversed())
                    .forEach(row -> {
                        Map<String, Object> joined = new HashMap<>(results.get((Integer) row.get("result_id")));
                        joined.putAll(row);
                        rows.add(joined);
                    });
            default -> throw new SQLException("Consulta no reconocida: " + sql);
        }
        return resultSet(rows);
    }

    private ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] position = {-1};
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "next" -> ++position[0] < rows.size();
            case "getInt" -> ((Number) rows.get(position[0]).get(String.valueOf(args[0]))).intValue();
            case "getLong" -> ((Number) rows.get(position[0]).get((String) args[0])).longValue();
            case "getString" -> (String) rows.get(position[0]).get((String) args[0]);
            case "getTimestamp" -> (Timestamp) rows.get(position[0]).get((String) args[0]);
            case "close" -> {
                resultSetsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (ResultSet) Proxy.newProxyInstance(FakeReportDatabase.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, handler);
    }
}
