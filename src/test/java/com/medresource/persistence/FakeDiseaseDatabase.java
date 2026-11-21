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
 * Base de datos falsa, en memoria, con las tres tablas de las enfermedades.
 *
 * <p>No es una prueba: reconoce exactamente los procedimientos de {@link OracleDiseaseDao} y los ejecuta
 * sobre mapas, con transacciones (confirmar y deshacer), la clave primaria, la clave única del nombre y el
 * borrado en cascada que tiene el esquema real. Así se prueba el DAO sin Oracle. Que el PL/SQL sea válido en
 * Oracle lo comprueba {@code OracleDiseaseDaoIntegrationTest}.</p>
 */
final class FakeDiseaseDatabase {

    /** Los 14 datos de la enfermedad, en el orden de los parámetros (el primero es el identificador). */
    private static final List<String> DISEASE_COLUMNS = List.of("id", "nombre", "especialidad_principal", "tratamiento",
            "curable", "requiere_sangre", "requiere_uci", "tasa_deterioro", "mortalidad_irreducible",
            "duracion_min_minutos", "duracion_max_minutos", "edad_minima", "edad_maxima", "frecuencia");

    /** Contenido de las tres tablas. */
    private static final class Tables {

        final Map<Integer, Map<String, Object>> diseases = new TreeMap<>();
        final Map<Integer, List<String>> secondary = new HashMap<>();
        final Map<Integer, TreeMap<Integer, String>> symptoms = new HashMap<>();

        Tables copy() {
            Tables copy = new Tables();
            diseases.forEach((id, row) -> copy.diseases.put(id, new LinkedHashMap<>(row)));
            secondary.forEach((id, list) -> copy.secondary.put(id, new ArrayList<>(list)));
            symptoms.forEach((id, map) -> copy.symptoms.put(id, new TreeMap<>(map)));
            return copy;
        }
    }

    private Tables committed = new Tables();
    private Tables working = committed;

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

    // ---------- lo que ven las pruebas (solo lo confirmado) ----------

    List<Integer> diseaseIds() {
        return new ArrayList<>(committed.diseases.keySet());
    }

    Object column(int diseaseId, String column) {
        return committed.diseases.get(diseaseId).get(column);
    }

    List<String> secondaryOf(int diseaseId) {
        return committed.secondary.getOrDefault(diseaseId, List.of()).stream().sorted().toList();
    }

    List<String> symptomsOf(int diseaseId) {
        return new ArrayList<>(committed.symptoms.getOrDefault(diseaseId, new TreeMap<>()).values());
    }

    /** Cambia a la fuerza un dato guardado, para simular datos dañados. */
    void corruptColumn(int diseaseId, String column, Object value) {
        committed.diseases.get(diseaseId).put(column, value);
    }

    /** Borra a la fuerza los síntomas de una enfermedad, para simular datos dañados. */
    void removeSymptoms(int diseaseId) {
        committed.symptoms.remove(diseaseId);
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
        return (Connection) Proxy.newProxyInstance(FakeDiseaseDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private CallableStatement prepareCall(String sql) throws SQLException {
        if (failWhenContaining != null && sql.contains(failWhenContaining)) {
            throw new SQLException("fallo simulado");
        }
        Map<Integer, Object> parameters = new HashMap<>();
        Map<Integer, Object> outputs = new HashMap<>();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setInt", "setString", "setDouble" -> {
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
        return (CallableStatement) Proxy.newProxyInstance(FakeDiseaseDatabase.class.getClassLoader(),
                new Class<?>[]{CallableStatement.class}, handler);
    }

    /** Imita los procedimientos de enfermedades: deja en {@code outputs} lo que Oracle devolvería. */
    private void call(String sql, Map<Integer, Object> p, Map<Integer, Object> outputs) throws SQLException {
        switch (sql) {
            case OracleDiseaseDao.INSERT_DISEASE -> insertDisease(p);
            case OracleDiseaseDao.UPDATE_DISEASE -> outputs.put(17, updateDisease(p));
            case OracleDiseaseDao.DELETE_DISEASE -> outputs.put(2, deleteDisease((Integer) p.get(1)));
            case OracleDiseaseDao.SELECT_DISEASES, OracleDiseaseDao.SELECT_SECONDARY, OracleDiseaseDao.SELECT_SYMPTOMS ->
                    outputs.put(2, query(sql, p));
            default -> throw new SQLException("Procedimiento no reconocido: " + sql);
        }
    }

    // ---------- procedimientos de cambio ----------

    /** Lo que hace {@code pr_guardar_enfermedad}: la clave primaria, el nombre único y los hijos. */
    private void insertDisease(Map<Integer, Object> p) throws SQLException {
        int id = (Integer) p.get(1);
        if (working.diseases.containsKey(id)) {
            throw new SQLException("ORA-00001: restricción única (PK_ENFERMEDAD) violada");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < DISEASE_COLUMNS.size(); i++) {
            row.put(DISEASE_COLUMNS.get(i), p.get(i + 1));
        }
        requireUniqueName(row);
        working.diseases.put(id, row);
        storeChildren(id, (String) p.get(15), (String) p.get(16));
    }

    /** Lo que hace {@code pr_actualizar_enfermedad}: devuelve 0 si no existe, o 1 tras reemplazar todo. */
    private int updateDisease(Map<Integer, Object> p) throws SQLException {
        int id = (Integer) p.get(1);
        Map<String, Object> row = working.diseases.get(id);
        if (row == null) {
            return 0;
        }
        Map<String, Object> changed = new LinkedHashMap<>(row);
        for (int i = 1; i < DISEASE_COLUMNS.size(); i++) {
            changed.put(DISEASE_COLUMNS.get(i), p.get(i + 1));
        }
        requireUniqueName(changed);
        working.diseases.put(id, changed);
        storeChildren(id, (String) p.get(15), (String) p.get(16));
        return 1;
    }

    /** Lo que hace {@code pr_borrar_enfermedad}: borra la enfermedad y, en cascada, sus hijos. */
    private int deleteDisease(int id) {
        working.secondary.remove(id);
        working.symptoms.remove(id);
        return working.diseases.remove(id) == null ? 0 : 1;
    }

    /** Guarda las especialidades secundarias y los síntomas (separados por comas) en orden. */
    private void storeChildren(int id, String specialties, String symptoms) {
        working.secondary.put(id, new ArrayList<>(split(specialties)));
        TreeMap<Integer, String> map = new TreeMap<>();
        List<String> names = split(symptoms);
        for (int order = 0; order < names.size(); order++) {
            map.put(order, names.get(order));
        }
        working.symptoms.put(id, map);
    }

    private static List<String> split(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isEmpty()) {
            return List.of();
        }
        return List.of(commaSeparated.split(","));
    }

    private void requireUniqueName(Map<String, Object> candidate) throws SQLException {
        for (Map<String, Object> other : working.diseases.values()) {
            if (!other.get("id").equals(candidate.get("id")) && other.get("nombre").equals(candidate.get("nombre"))) {
                throw new SQLException("ORA-00001: restricción única (UQ_ENFERMEDAD_NOMBRE) violada");
            }
        }
    }

    // ---------- lecturas (con filtro opcional: id nulo = todas) ----------

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        Integer only = (Integer) p.get(1);
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleDiseaseDao.SELECT_DISEASES -> working.diseases.forEach((id, row) -> {
                if (only == null || only.equals(id)) {
                    rows.add(row);
                }
            });
            case OracleDiseaseDao.SELECT_SECONDARY -> new TreeMap<>(working.secondary).forEach((id, list) -> {
                if (only == null || only.equals(id)) {
                    list.stream().sorted()
                            .forEach(name -> rows.add(Map.of("id_enfermedad", id, "especialidad", name)));
                }
            });
            case OracleDiseaseDao.SELECT_SYMPTOMS -> new TreeMap<>(working.symptoms).forEach((id, map) -> {
                if (only == null || only.equals(id)) {
                    map.values().forEach(name -> rows.add(Map.of("id_enfermedad", id, "sintoma", name)));
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
            case "getInt" -> ((Number) rows.get(position[0]).get((String) args[0])).intValue();
            case "getDouble" -> ((Number) rows.get(position[0]).get((String) args[0])).doubleValue();
            case "getString" -> (String) rows.get(position[0]).get((String) args[0]);
            case "close" -> {
                resultSetsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (ResultSet) Proxy.newProxyInstance(FakeDiseaseDatabase.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, handler);
    }
}
