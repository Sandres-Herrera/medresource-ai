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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Base de datos falsa, en memoria, con las tres tablas de los casos.
 *
 * <p>No es una prueba: reconoce exactamente las sentencias de {@link OracleCaseDao} y las ejecuta sobre
 * mapas, con transacciones (confirmar y deshacer), el identificador generado, la configuración única, las
 * claves foráneas (los pacientes necesitan su caso y su enfermedad) y el borrado en cascada que tiene el
 * esquema real. Que el SQL sea válido en Oracle lo comprueba {@code OracleCaseDaoIntegrationTest}.</p>
 */
final class FakeCaseDatabase {

    private static final List<String> PATIENT_COLUMNS = List.of("case_id", "patient_id", "age", "sex",
            "blood_type", "disease_id", "arrival_minute", "initial_health", "irrecoverable");

    /** Contenido de las tres tablas y el siguiente identificador. */
    private static final class Tables {

        int nextId = 1;
        final Map<Integer, Map<String, Object>> cases = new TreeMap<>();
        final Map<List<Integer>, Map<String, Object>> patients = new TreeMap<>(FakeCaseDatabase::compareKeys);
        final Map<List<Integer>, TreeMap<Integer, String>> symptoms = new TreeMap<>(FakeCaseDatabase::compareKeys);

        Tables copy() {
            Tables copy = new Tables();
            copy.nextId = nextId;
            cases.forEach((id, row) -> copy.cases.put(id, new LinkedHashMap<>(row)));
            patients.forEach((key, row) -> copy.patients.put(key, new LinkedHashMap<>(row)));
            symptoms.forEach((key, map) -> copy.symptoms.put(key, new TreeMap<>(map)));
            return copy;
        }
    }

    private static int compareKeys(List<Integer> first, List<Integer> second) {
        int byCase = first.get(0).compareTo(second.get(0));
        return byCase != 0 ? byCase : first.get(1).compareTo(second.get(1));
    }

    private Tables committed = new Tables();
    private Tables working = committed;
    private final Set<Integer> knownDiseases = new TreeSet<>();

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

    /** Declara que la enfermedad existe, para que los pacientes puedan usarla (clave foránea). */
    void addDisease(int diseaseId) {
        knownDiseases.add(diseaseId);
    }

    // ---------- lo que ven las pruebas (solo lo confirmado) ----------

    List<Integer> caseIds() {
        return new ArrayList<>(committed.cases.keySet());
    }

    Object caseColumn(int caseId, String column) {
        return committed.cases.get(caseId).get(column);
    }

    List<Integer> patientIds(int caseId) {
        return committed.patients.keySet().stream().filter(key -> key.get(0) == caseId).map(key -> key.get(1)).toList();
    }

    Object patientColumn(int caseId, int patientId, String column) {
        return committed.patients.get(List.of(caseId, patientId)).get(column);
    }

    List<String> symptomsOf(int caseId, int patientId) {
        return new ArrayList<>(committed.symptoms.getOrDefault(List.of(caseId, patientId), new TreeMap<>()).values());
    }

    /** Cambia a la fuerza un dato guardado de un caso, para simular datos dañados. */
    void corruptCase(int caseId, String column, Object value) {
        committed.cases.get(caseId).put(column, value);
    }

    /** Cambia a la fuerza un dato guardado de un paciente, para simular datos dañados. */
    void corruptPatient(int caseId, int patientId, String column, Object value) {
        committed.patients.get(List.of(caseId, patientId)).put(column, value);
    }

    /** Cambia a la fuerza una enfermedad "conocida", para simular que la enfermedad se borró. */
    void removeDisease(int diseaseId) {
        knownDiseases.remove(diseaseId);
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
        return (Connection) Proxy.newProxyInstance(FakeCaseDatabase.class.getClassLoader(),
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
            case "executeUpdate" -> {
                int changed = update(sql, parameters, generatedKey);
                yield changed;
            }
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
        return (PreparedStatement) Proxy.newProxyInstance(FakeCaseDatabase.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, handler);
    }

    // ---------- sentencias de cambio ----------

    private int update(String sql, Map<Integer, Object> p, int[] generatedKey) throws SQLException {
        switch (sql) {
            case OracleCaseDao.INSERT_CASE -> {
                for (Map<String, Object> other : working.cases.values()) {
                    if (other.get("seed").equals(p.get(1)) && other.get("scenario").equals(p.get(2))
                            && other.get("patient_count").equals(p.get(3))
                            && other.get("duration_minutes").equals(p.get(4))) {
                        throw new SQLException("ORA-00001: restricción única (UQ_SIM_CASE_CONFIG) violada");
                    }
                }
                int id = working.nextId++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("seed", p.get(1));
                row.put("scenario", p.get(2));
                row.put("patient_count", p.get(3));
                row.put("duration_minutes", p.get(4));
                working.cases.put(id, row);
                generatedKey[0] = id;
                return 1;
            }
            case OracleCaseDao.INSERT_PATIENT -> {
                List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
                if (!working.cases.containsKey(key.get(0))) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_CASE_PATIENT_CASE) violada");
                }
                if (!knownDiseases.contains((Integer) p.get(6))) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_CASE_PATIENT_DISEASE) violada");
                }
                if (working.patients.containsKey(key)) {
                    throw new SQLException("ORA-00001: restricción única (PK_CASE_PATIENT) violada");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < PATIENT_COLUMNS.size(); i++) {
                    row.put(PATIENT_COLUMNS.get(i), p.get(i + 1));
                }
                working.patients.put(key, row);
                return 1;
            }
            case OracleCaseDao.INSERT_SYMPTOM -> {
                List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
                if (!working.patients.containsKey(key)) {
                    throw new SQLException("ORA-02291: restricción de integridad (FK_CASE_SYMPTOM_PATIENT) violada");
                }
                TreeMap<Integer, String> map = working.symptoms.computeIfAbsent(key, k -> new TreeMap<>());
                if (map.containsKey((Integer) p.get(3))) {
                    throw new SQLException("ORA-00001: restricción única (PK_CASE_PATIENT_SYMPTOM) violada");
                }
                map.put((Integer) p.get(3), (String) p.get(4));
                return 1;
            }
            case OracleCaseDao.DELETE_CASE -> {
                int id = (Integer) p.get(1);
                working.patients.keySet().removeIf(key -> key.get(0) == id);
                working.symptoms.keySet().removeIf(key -> key.get(0) == id);
                return working.cases.remove(id) == null ? 0 : 1;
            }
            default -> throw new SQLException("Sentencia de cambio no reconocida: " + sql);
        }
    }

    // ---------- consultas ----------

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleCaseDao.SELECT_CASES -> working.cases.values().forEach(rows::add);
            case OracleCaseDao.SELECT_CASE -> {
                Map<String, Object> row = working.cases.get((Integer) p.get(1));
                if (row != null) {
                    rows.add(row);
                }
            }
            case OracleCaseDao.SELECT_ID_BY_CONFIG -> working.cases.values().stream()
                    .filter(row -> row.get("seed").equals(p.get(1)) && row.get("scenario").equals(p.get(2))
                            && row.get("patient_count").equals(p.get(3))
                            && row.get("duration_minutes").equals(p.get(4)))
                    .forEach(rows::add);
            case OracleCaseDao.SELECT_PATIENTS, OracleCaseDao.SELECT_PATIENTS_OF -> {
                Integer only = (Integer) p.get(1);
                working.patients.forEach((key, row) -> {
                    if (only == null || only.equals(key.get(0))) {
                        rows.add(row);
                    }
                });
            }
            case OracleCaseDao.SELECT_SYMPTOMS, OracleCaseDao.SELECT_SYMPTOMS_OF -> {
                Integer only = (Integer) p.get(1);
                working.symptoms.forEach((key, map) -> {
                    if (only == null || only.equals(key.get(0))) {
                        map.values().forEach(name -> rows.add(
                                Map.of("case_id", key.get(0), "patient_id", key.get(1), "symptom", name)));
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
        return (ResultSet) Proxy.newProxyInstance(FakeCaseDatabase.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, handler);
    }
}