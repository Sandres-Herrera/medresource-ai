package com.medresource.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Base de datos falsa, en memoria, con las tablas de las ejecuciones.
 *
 * <p>No es una prueba: reconoce exactamente las sentencias de {@link OracleResultDao} y las ejecuta sobre
 * mapas, con transacciones (confirmar y deshacer), el identificador generado, las claves foráneas (la
 * ejecución necesita su caso y los pacientes su ejecución) y el borrado en cascada que tiene el esquema
 * real. Los casos se declaran con {@link #addCase}; las enfermedades, con {@link #addDisease}. Que el SQL
 * sea válido en Oracle lo comprueba {@code OracleResultDaoIntegrationTest}.</p>
 */
final class FakeResultDatabase {

    private static final List<String> PATIENT_COLUMNS = List.of("result_id", "patient_id", "patient_code", "age",
            "sex", "triage", "disease_id", "arrival_minute", "treatment_start_minute", "outcome_minute", "status",
            "cause_of_death", "care_level", "irrecoverable", "final_health");
    private static final List<String> EVENT_COLUMNS = List.of("result_id", "event_order", "event_minute",
            "event_type", "patient_code", "resource_code", "description");
    private static final List<String> RESULT_COLUMNS = List.of("case_id", "strategy_name", "total_minutes", "beds",
            "icu_beds", "operating_rooms", "doctors", "nurses", "blood_units_per_type", "morgue_capacity");

    /** Contenido de las tablas y el siguiente identificador. */
    private static final class Tables {

        int nextId = 1;
        final Map<Integer, Map<String, Object>> results = new TreeMap<>();
        final Map<List<Integer>, Map<String, Object>> patients = new TreeMap<>(FakeResultDatabase::compareKeys);
        final Map<List<Integer>, Map<String, Object>> events = new TreeMap<>(FakeResultDatabase::compareKeys);

        Tables copy() {
            Tables copy = new Tables();
            copy.nextId = nextId;
            results.forEach((id, row) -> copy.results.put(id, new LinkedHashMap<>(row)));
            patients.forEach((key, row) -> copy.patients.put(key, new LinkedHashMap<>(row)));
            events.forEach((key, row) -> copy.events.put(key, new LinkedHashMap<>(row)));
            return copy;
        }
    }

    private static int compareKeys(List<Integer> first, List<Integer> second) {
        int byResult = first.get(0).compareTo(second.get(0));
        return byResult != 0 ? byResult : first.get(1).compareTo(second.get(1));
    }

    private Tables committed = new Tables();
    private Tables working = committed;
    private final Map<Integer, Map<String, Object>> cases = new TreeMap<>();
    private final List<Integer> knownDiseases = new ArrayList<>();

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

    /** Declara un caso guardado, para que las ejecuciones puedan referirse a él (clave foránea). */
    void addCase(int caseId, long seed, String scenario, int patientCount, int durationMinutes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", caseId);
        row.put("seed", seed);
        row.put("scenario", scenario);
        row.put("patient_count", patientCount);
        row.put("duration_minutes", durationMinutes);
        cases.put(caseId, row);
    }

    /** Declara que la enfermedad existe, para que los pacientes puedan usarla (clave foránea). */
    void addDisease(int diseaseId) {
        knownDiseases.add(diseaseId);
    }

    /** Borra el caso y, en cascada, sus ejecuciones. */
    void removeCase(int caseId) {
        cases.remove(caseId);
        committed.results.values().removeIf(row -> row.get("case_id").equals(caseId));
        committed.patients.keySet().removeIf(key -> !committed.results.containsKey(key.get(0)));
        committed.events.keySet().removeIf(key -> !committed.results.containsKey(key.get(0)));
        working = committed;
    }

    // ---------- lo que ven las pruebas (solo lo confirmado) ----------

    List<Integer> resultIds() {
        return new ArrayList<>(committed.results.keySet());
    }

    Object resultColumn(int resultId, String column) {
        return committed.results.get(resultId).get(column);
    }

    List<Integer> patientIds(int resultId) {
        return committed.patients.keySet().stream().filter(key -> key.get(0) == resultId).map(key -> key.get(1)).toList();
    }

    Object patientColumn(int resultId, int patientId, String column) {
        return committed.patients.get(List.of(resultId, patientId)).get(column);
    }

    int eventCount(int resultId) {
        return (int) committed.events.keySet().stream().filter(key -> key.get(0) == resultId).count();
    }

    Object eventColumn(int resultId, int order, String column) {
        return committed.events.get(List.of(resultId, order)).get(column);
    }

    /** Cambia a la fuerza un dato guardado de una ejecución, para simular datos dañados. */
    void corruptResult(int resultId, String column, Object value) {
        committed.results.get(resultId).put(column, value);
    }

    /** Cambia a la fuerza un dato guardado de un paciente, para simular datos dañados. */
    void corruptPatient(int resultId, int patientId, String column, Object value) {
        committed.patients.get(List.of(resultId, patientId)).put(column, value);
    }

    /** Cambia a la fuerza un dato guardado de un evento, para simular datos dañados. */
    void corruptEvent(int resultId, int order, String column, Object value) {
        committed.events.get(List.of(resultId, order)).put(column, value);
    }

    /** Cambia a la fuerza un dato guardado del caso, para simular datos dañados. */
    void corruptCase(int caseId, String column, Object value) {
        cases.get(caseId).put(column, value);
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
        return (Connection) Proxy.newProxyInstance(FakeResultDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private PreparedStatement prepare(String sql) throws SQLException {
        if (failWhenContaining != null && sql.contains(failWhenContaining)) {
            throw new SQLException("fallo simulado");
        }
        Map<Integer, Object> parameters = new HashMap<>();
        List<Map<Integer, Object>> batch = new ArrayList<>();
        int[] generatedKey = {0};
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setInt", "setLong", "setString", "setDouble" -> {
                parameters.put((Integer) args[0], args[1]);
                yield null;
            }
            case "setNull" -> {
                parameters.put((Integer) args[0], null);
                yield null;
            }
            case "executeUpdate" -> update(sql, parameters, generatedKey);
            case "addBatch" -> {
                batch.add(new HashMap<>(parameters));
                yield null;
            }
            case "executeBatch" -> {
                int[] counts = new int[batch.size()];
                for (int i = 0; i < counts.length; i++) {
                    counts[i] = update(sql, batch.get(i), generatedKey);
                }
                batch.clear();
                yield counts;
            }
            case "getGeneratedKeys" -> resultSet(List.of(Map.of("1", generatedKey[0])));
            case "executeQuery" -> query(sql, parameters);
            case "close" -> {
                statementsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (PreparedStatement) Proxy.newProxyInstance(FakeResultDatabase.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, handler);
    }

    // ---------- sentencias de cambio ----------

    private int update(String sql, Map<Integer, Object> p, int[] generatedKey) throws SQLException {
        switch (sql) {
            case OracleResultDao.INSERT_RESULT -> {
                if (!cases.containsKey((Integer) p.get(1))) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_SIM_RESULT_CASE) violada");
                }
                int id = working.nextId++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                for (int i = 0; i < RESULT_COLUMNS.size(); i++) {
                    row.put(RESULT_COLUMNS.get(i), p.get(i + 1));
                }
                working.results.put(id, row);
                generatedKey[0] = id;
                return 1;
            }
            case OracleResultDao.INSERT_PATIENT -> {
                List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
                if (!working.results.containsKey(key.get(0))) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_PATIENT_RESULT_RESULT) violada");
                }
                if (!knownDiseases.contains((Integer) p.get(7))) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_PATIENT_RESULT_DISEASE) violada");
                }
                if (working.patients.containsKey(key)) {
                    throw new SQLException("ORA-00001: restricción única (PK_PATIENT_RESULT) violada");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < PATIENT_COLUMNS.size(); i++) {
                    row.put(PATIENT_COLUMNS.get(i), p.get(i + 1));
                }
                working.patients.put(key, row);
                return 1;
            }
            case OracleResultDao.INSERT_EVENT -> {
                List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
                if (!working.results.containsKey(key.get(0))) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_SIM_EVENT_RESULT) violada");
                }
                if (working.events.containsKey(key)) {
                    throw new SQLException("ORA-00001: restricción única (PK_SIM_EVENT) violada");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < EVENT_COLUMNS.size(); i++) {
                    row.put(EVENT_COLUMNS.get(i), p.get(i + 1));
                }
                working.events.put(key, row);
                return 1;
            }
            case OracleResultDao.DELETE_RESULT -> {
                int id = (Integer) p.get(1);
                working.patients.keySet().removeIf(key -> key.get(0) == id);
                working.events.keySet().removeIf(key -> key.get(0) == id);
                return working.results.remove(id) == null ? 0 : 1;
            }
            default -> throw new SQLException("Sentencia de cambio no reconocida: " + sql);
        }
    }

    // ---------- consultas ----------

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleResultDao.SELECT_CASE_CONFIG -> {
                Map<String, Object> row = cases.get((Integer) p.get(1));
                if (row != null) {
                    rows.add(row);
                }
            }
            case OracleResultDao.SELECT_RESULTS, OracleResultDao.SELECT_RESULT -> {
                Integer only = sql.equals(OracleResultDao.SELECT_RESULT) ? (Integer) p.get(1) : null;
                working.results.forEach((id, row) -> {
                    if (only == null || only.equals(id)) {
                        Map<String, Object> joined = new LinkedHashMap<>(row);
                        Map<String, Object> theCase = cases.get((Integer) row.get("case_id"));
                        joined.put("seed", theCase.get("seed"));
                        joined.put("scenario", theCase.get("scenario"));
                        joined.put("patient_count", theCase.get("patient_count"));
                        joined.put("duration_minutes", theCase.get("duration_minutes"));
                        rows.add(joined);
                    }
                });
            }
            case OracleResultDao.SELECT_IDS_BY_CASE -> working.results.values().stream()
                    .filter(row -> row.get("case_id").equals(p.get(1))).forEach(rows::add);
            case OracleResultDao.SELECT_PATIENTS, OracleResultDao.SELECT_PATIENTS_OF -> {
                Integer only = (Integer) p.get(1);
                working.patients.forEach((key, row) -> {
                    if (only == null || only.equals(key.get(0))) {
                        rows.add(row);
                    }
                });
            }
            case OracleResultDao.SELECT_EVENTS, OracleResultDao.SELECT_EVENTS_OF -> {
                Integer only = (Integer) p.get(1);
                working.events.forEach((key, row) -> {
                    if (only == null || only.equals(key.get(0))) {
                        rows.add(row);
                    }
                });
            }
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
            case "getDouble" -> ((Number) rows.get(position[0]).get((String) args[0])).doubleValue();
            case "getString" -> (String) rows.get(position[0]).get((String) args[0]);
            case "close" -> {
                resultSetsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (ResultSet) Proxy.newProxyInstance(FakeResultDatabase.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, handler);
    }
}