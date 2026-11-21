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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Base de datos falsa, en memoria, con las tres tablas de los casos.
 *
 * <p>No es una prueba: reconoce exactamente los procedimientos de {@link OracleCaseDao} y los ejecuta sobre
 * mapas, con transacciones (confirmar y deshacer), el identificador generado, la configuración única, las
 * claves foráneas (los pacientes necesitan su caso y su enfermedad) y el borrado en cascada que tiene el
 * esquema real. Que el PL/SQL sea válido en Oracle lo comprueba {@code OracleCaseDaoIntegrationTest}.</p>
 */
final class FakeCaseDatabase {

    private static final List<String> PATIENT_COLUMNS = List.of("id_caso", "id_paciente", "edad", "sexo",
            "tipo_sangre", "id_enfermedad", "minuto_llegada", "salud_inicial", "irrecuperable");

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

    /** Si no es {@code null}, preparar una llamada que contenga este texto lanza un error. */
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
            case "prepareCall" -> prepareCall((String) args[0]);
            case "close" -> {
                connectionsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Connection) Proxy.newProxyInstance(FakeCaseDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private CallableStatement prepareCall(String sql) throws SQLException {
        if (failWhenContaining != null && sql.contains(failWhenContaining)) {
            throw new SQLException("fallo simulado");
        }
        Map<Integer, Object> parameters = new HashMap<>();
        Map<Integer, Object> outputs = new HashMap<>();
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
            case "getInt" -> ((Number) outputs.get((Integer) args[0])).intValue();
            case "getObject" -> outputs.get((Integer) args[0]);
            case "close" -> {
                statementsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (CallableStatement) Proxy.newProxyInstance(FakeCaseDatabase.class.getClassLoader(),
                new Class<?>[]{CallableStatement.class}, handler);
    }

    /** Imita los procedimientos de casos: deja en {@code outputs} lo que Oracle devolvería. */
    private void call(String sql, Map<Integer, Object> p, Map<Integer, Object> outputs) throws SQLException {
        switch (sql) {
            case OracleCaseDao.INSERT_CASE -> outputs.put(5, insertCase(p));
            case OracleCaseDao.INSERT_PATIENT -> insertPatient(p);
            case OracleCaseDao.DELETE_CASE -> outputs.put(2, deleteCase((Integer) p.get(1)));
            case OracleCaseDao.SELECT_ID_BY_CONFIG -> outputs.put(5, findId(p));
            case OracleCaseDao.SELECT_CASES, OracleCaseDao.SELECT_PATIENTS, OracleCaseDao.SELECT_SYMPTOMS ->
                    outputs.put(2, query(sql, p));
            default -> throw new SQLException("Procedimiento no reconocido: " + sql);
        }
    }

    // ---------- procedimientos de cambio ----------

    /** Lo que hace {@code pr_crear_caso}: valida la configuración única y devuelve el identificador. */
    private int insertCase(Map<Integer, Object> p) throws SQLException {
        for (Map<String, Object> other : working.cases.values()) {
            if (other.get("semilla").equals(p.get(1)) && other.get("escenario").equals(p.get(2))
                    && other.get("cantidad_pacientes").equals(p.get(3))
                    && other.get("duracion_minutos").equals(p.get(4))) {
                throw new SQLException("ORA-00001: restricción única (UQ_CASO_CONFIGURACION) violada");
            }
        }
        int id = working.nextId++;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("semilla", p.get(1));
        row.put("escenario", p.get(2));
        row.put("cantidad_pacientes", p.get(3));
        row.put("duracion_minutos", p.get(4));
        working.cases.put(id, row);
        return id;
    }

    /** Lo que hace {@code pr_agregar_paciente}: guarda el paciente y, uno por uno, sus síntomas. */
    private void insertPatient(Map<Integer, Object> p) throws SQLException {
        List<Integer> key = List.of((Integer) p.get(1), (Integer) p.get(2));
        if (!working.cases.containsKey(key.get(0))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_CASO_PACIENTE_CASO) violada");
        }
        if (!knownDiseases.contains((Integer) p.get(6))) {
            throw new SQLException("ORA-02291: restricción de integridad (FK_CASO_PACIENTE_ENFERMEDAD) violada");
        }
        if (working.patients.containsKey(key)) {
            throw new SQLException("ORA-00001: restricción única (PK_CASO_PACIENTE) violada");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < PATIENT_COLUMNS.size(); i++) {
            row.put(PATIENT_COLUMNS.get(i), p.get(i + 1));
        }
        working.patients.put(key, row);
        String names = (String) p.get(10);
        if (names != null && !names.isEmpty()) {
            TreeMap<Integer, String> map = new TreeMap<>();
            String[] parts = names.split(",");
            for (int order = 0; order < parts.length; order++) {
                map.put(order, parts[order]);
            }
            working.symptoms.put(key, map);
        }
    }

    /** Lo que hace {@code pr_borrar_caso}: borra el caso con sus pacientes y síntomas. */
    private int deleteCase(int id) {
        working.patients.keySet().removeIf(key -> key.get(0) == id);
        working.symptoms.keySet().removeIf(key -> key.get(0) == id);
        return working.cases.remove(id) == null ? 0 : 1;
    }

    /** Lo que hace {@code pr_buscar_id_caso}: el identificador de la configuración, o {@code null}. */
    private Integer findId(Map<Integer, Object> p) {
        return working.cases.values().stream()
                .filter(row -> row.get("semilla").equals(p.get(1)) && row.get("escenario").equals(p.get(2))
                        && row.get("cantidad_pacientes").equals(p.get(3))
                        && row.get("duracion_minutos").equals(p.get(4)))
                .map(row -> (Integer) row.get("id"))
                .findFirst()
                .orElse(null);
    }

    // ---------- lecturas (con filtro opcional: id nulo = todos) ----------

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        Integer only = (Integer) p.get(1);
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleCaseDao.SELECT_CASES -> working.cases.forEach((id, row) -> {
                if (only == null || only.equals(id)) {
                    rows.add(row);
                }
            });
            case OracleCaseDao.SELECT_PATIENTS -> working.patients.forEach((key, row) -> {
                if (only == null || only.equals(key.get(0))) {
                    rows.add(row);
                }
            });
            case OracleCaseDao.SELECT_SYMPTOMS -> working.symptoms.forEach((key, map) -> {
                if (only == null || only.equals(key.get(0))) {
                    map.values().forEach(name -> rows.add(
                            Map.of("id_caso", key.get(0), "id_paciente", key.get(1), "sintoma", name)));
                }
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
