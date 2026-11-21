package com.medresource.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
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
 * ejecución necesita su caso y su ejecución base; los médicos extra, los pacientes y los eventos, su ejecución),
 * el borrado en cascada y el enlace a la ejecución base que queda vacío al borrarla, como en el esquema real. Los
 * casos se declaran con {@link #addCase}; las enfermedades, con {@link #addDisease}. Que el SQL sea válido en
 * Oracle lo comprueba {@code OracleResultDaoIntegrationTest}.</p>
 */
final class FakeResultDatabase {

    private static final List<String> PATIENT_COLUMNS = List.of("id_ejecucion", "id_paciente", "codigo_paciente",
            "edad", "sexo", "nivel_triage", "id_enfermedad", "minuto_llegada", "minuto_inicio_atencion",
            "minuto_desenlace", "estado", "causa_muerte", "nivel_atencion", "irrecuperable", "salud_final");
    private static final List<String> EVENT_COLUMNS = List.of("id_ejecucion", "orden_evento", "minuto_evento",
            "tipo_evento", "codigo_paciente", "codigo_recurso", "descripcion");
    private static final List<String> RESULT_COLUMNS = List.of("id_caso", "estrategia", "minutos_totales", "camas",
            "camas_uci", "quirofanos", "medicos", "enfermeros", "unidades_sangre_por_tipo", "capacidad_morgue",
            "id_ejecucion_base");

    /** Contenido de las tablas y el siguiente identificador. */
    private static final class Tables {

        int nextId = 1;
        final Map<Integer, Map<String, Object>> results = new TreeMap<>();
        final Map<List<Integer>, Map<String, Object>> patients = new TreeMap<>(FakeResultDatabase::compareKeys);
        final Map<List<Integer>, Map<String, Object>> events = new TreeMap<>(FakeResultDatabase::compareKeys);
        final Map<List<Integer>, String> extraDoctors = new TreeMap<>(FakeResultDatabase::compareKeys);

        Tables copy() {
            Tables copy = new Tables();
            copy.nextId = nextId;
            results.forEach((id, row) -> copy.results.put(id, new LinkedHashMap<>(row)));
            patients.forEach((key, row) -> copy.patients.put(key, new LinkedHashMap<>(row)));
            events.forEach((key, row) -> copy.events.put(key, new LinkedHashMap<>(row)));
            copy.extraDoctors.putAll(extraDoctors);
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
        row.put("semilla", seed);
        row.put("escenario", scenario);
        row.put("cantidad_pacientes", patientCount);
        row.put("duracion_minutos", durationMinutes);
        cases.put(caseId, row);
    }

    /** Declara que la enfermedad existe, para que los pacientes puedan usarla (clave foránea). */
    void addDisease(int diseaseId) {
        knownDiseases.add(diseaseId);
    }

    /** Borra el caso y, en cascada, sus ejecuciones. */
    void removeCase(int caseId) {
        cases.remove(caseId);
        committed.results.values().removeIf(row -> row.get("id_caso").equals(caseId));
        committed.patients.keySet().removeIf(key -> !committed.results.containsKey(key.get(0)));
        committed.events.keySet().removeIf(key -> !committed.results.containsKey(key.get(0)));
        committed.extraDoctors.keySet().removeIf(key -> !committed.results.containsKey(key.get(0)));
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

    /** Especialidades guardadas de los médicos extra de una ejecución, en orden. */
    List<String> extraDoctorsOf(int resultId) {
        return committed.extraDoctors.entrySet().stream().filter(entry -> entry.getKey().get(0) == resultId)
                .map(Map.Entry::getValue).toList();
    }

    /** Cambia a la fuerza la especialidad guardada de un médico extra, para simular datos dañados. */
    void corruptExtraDoctor(int resultId, int order, String specialty) {
        committed.extraDoctors.put(List.of(resultId, order), specialty);
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
            case "prepareCall" -> prepareCall((String) args[0]);
            case "close" -> {
                connectionsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Connection) Proxy.newProxyInstance(FakeResultDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private CallableStatement prepareCall(String sql) throws SQLException {
        if (failWhenContaining != null && sql.contains(failWhenContaining)) {
            throw new SQLException("fallo simulado");
        }
        Map<Integer, Object> parameters = new HashMap<>();
        Map<Integer, Object> outputs = new HashMap<>();
        List<Map<Integer, Object>> batch = new ArrayList<>();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setInt", "setLong", "setString", "setDouble" -> {
                parameters.put((Integer) args[0], args[1]);
                yield null;
            }
            case "setNull" -> {
                parameters.put((Integer) args[0], null);
                yield null;
            }
            case "registerOutParameter" -> null;
            case "execute" -> {
                call(sql, parameters, outputs);
                yield false;
            }
            case "addBatch" -> {
                batch.add(new HashMap<>(parameters));
                yield null;
            }
            case "executeBatch" -> {
                int[] counts = new int[batch.size()];
                for (int i = 0; i < counts.length; i++) {
                    call(sql, batch.get(i), new HashMap<>());
                    counts[i] = 1;
                }
                batch.clear();
                yield counts;
            }
            case "getInt" -> ((Number) outputs.get((Integer) args[0])).intValue();
            case "getObject" -> outputs.get((Integer) args[0]);
            case "close" -> {
                statementsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (CallableStatement) Proxy.newProxyInstance(FakeResultDatabase.class.getClassLoader(),
                new Class<?>[]{CallableStatement.class}, handler);
    }

    // ---------- sentencias de cambio ----------

    /** Imita los procedimientos de ejecuciones: deja en {@code outputs} lo que Oracle devolvería. */
    private void call(String sql, Map<Integer, Object> p, Map<Integer, Object> outputs) throws SQLException {
        switch (sql) {
            case OracleResultDao.SELECT_CASE_CONFIG, OracleResultDao.SELECT_BASE_CASE, OracleResultDao.SELECT_RESULTS,
                 OracleResultDao.SELECT_IDS_BY_CASE, OracleResultDao.SELECT_PATIENTS, OracleResultDao.SELECT_EVENTS,
                 OracleResultDao.SELECT_EXTRA_DOCTORS -> outputs.put(2, query(sql, p));
            case OracleResultDao.INSERT_RESULT -> outputs.put(12, insertResult(p));
            case OracleResultDao.INSERT_PATIENT -> insertPatient(p);
            case OracleResultDao.INSERT_EVENT -> insertEvent(p);
            case OracleResultDao.INSERT_EXTRA_DOCTOR -> insertExtraDoctor(p);
            case OracleResultDao.DELETE_RESULT -> outputs.put(2, deleteResult((Integer) p.get(1)));
            default -> throw new SQLException("Procedimiento no reconocido: " + sql);
        }
    }

    /** Lo que hace {@code pr_crear_ejecucion}: valida el caso y la base, y devuelve el identificador. */
    private int insertResult(Map<Integer, Object> p) throws SQLException {
        if (!cases.containsKey((Integer) p.get(1))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_EJECUCION_CASO) violada");
        }
        if (p.get(11) != null && !working.results.containsKey((Integer) p.get(11))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_EJECUCION_BASE) violada");
        }
        int id = working.nextId++;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        for (int i = 0; i < RESULT_COLUMNS.size(); i++) {
            row.put(RESULT_COLUMNS.get(i), p.get(i + 1));
        }
        working.results.put(id, row);
        return id;
    }

    private void insertPatient(Map<Integer, Object> p) throws SQLException {
        List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
        if (!working.results.containsKey(key.get(0))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_RESULTADO_PAC_EJECUCION) violada");
        }
        if (!knownDiseases.contains((Integer) p.get(7))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_RESULTADO_PAC_ENFERMEDAD) violada");
        }
        if (working.patients.containsKey(key)) {
            throw new SQLException("ORA-00001: restricción única (PK_RESULTADO_PACIENTE) violada");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < PATIENT_COLUMNS.size(); i++) {
            row.put(PATIENT_COLUMNS.get(i), p.get(i + 1));
        }
        working.patients.put(key, row);
    }

    private void insertEvent(Map<Integer, Object> p) throws SQLException {
        List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
        if (!working.results.containsKey(key.get(0))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_EVENTO_EJECUCION) violada");
        }
        if (working.events.containsKey(key)) {
            throw new SQLException("ORA-00001: restricción única (PK_EVENTO) violada");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < EVENT_COLUMNS.size(); i++) {
            row.put(EVENT_COLUMNS.get(i), p.get(i + 1));
        }
        working.events.put(key, row);
    }

    private void insertExtraDoctor(Map<Integer, Object> p) throws SQLException {
        List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
        if (!working.results.containsKey(key.get(0))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_EJEC_MEDICO_EJECUCION) violada");
        }
        if (working.extraDoctors.containsKey(key)) {
            throw new SQLException("ORA-00001: restricción única (PK_EJECUCION_MEDICO_EXTRA) violada");
        }
        working.extraDoctors.put(key, (String) p.get(3));
    }

    /** Lo que hace {@code pr_borrar_ejecucion}: borra sus pacientes, eventos y médicos; deja sin base a las que la usaban. */
    private int deleteResult(int id) {
        working.extraDoctors.keySet().removeIf(key -> key.get(0) == id);
        working.results.values().stream()
                .filter(row -> Integer.valueOf(id).equals(row.get("id_ejecucion_base")))
                .forEach(row -> row.put("id_ejecucion_base", null));
        working.patients.keySet().removeIf(key -> key.get(0) == id);
        working.events.keySet().removeIf(key -> key.get(0) == id);
        return working.results.remove(id) == null ? 0 : 1;
    }

    // ---------- consultas ----------

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleResultDao.SELECT_BASE_CASE -> {
                Map<String, Object> row = working.results.get((Integer) p.get(1));
                if (row != null) {
                    rows.add(row);
                }
            }
            case OracleResultDao.SELECT_CASE_CONFIG -> {
                Map<String, Object> row = cases.get((Integer) p.get(1));
                if (row != null) {
                    rows.add(row);
                }
            }
            case OracleResultDao.SELECT_RESULTS -> {
                Integer only = (Integer) p.get(1);
                working.results.forEach((id, row) -> {
                    if (only == null || only.equals(id)) {
                        Map<String, Object> joined = new LinkedHashMap<>(row);
                        Map<String, Object> theCase = cases.get((Integer) row.get("id_caso"));
                        joined.put("semilla", theCase.get("semilla"));
                        joined.put("escenario", theCase.get("escenario"));
                        joined.put("cantidad_pacientes", theCase.get("cantidad_pacientes"));
                        joined.put("duracion_minutos", theCase.get("duracion_minutos"));
                        rows.add(joined);
                    }
                });
            }
            case OracleResultDao.SELECT_IDS_BY_CASE -> working.results.values().stream()
                    .filter(row -> row.get("id_caso").equals(p.get(1))).forEach(rows::add);
            case OracleResultDao.SELECT_PATIENTS -> {
                Integer only = (Integer) p.get(1);
                working.patients.forEach((key, row) -> {
                    if (only == null || only.equals(key.get(0))) {
                        rows.add(row);
                    }
                });
            }
            case OracleResultDao.SELECT_EXTRA_DOCTORS -> {
                Integer only = (Integer) p.get(1);
                working.extraDoctors.forEach((key, specialty) -> {
                    if (only == null || only.equals(key.get(0))) {
                        rows.add(Map.of("id_ejecucion", key.get(0), "especialidad", specialty));
                    }
                });
            }
            case OracleResultDao.SELECT_EVENTS -> {
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
            case "getObject" -> rows.get(position[0]).get((String) args[0]);
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