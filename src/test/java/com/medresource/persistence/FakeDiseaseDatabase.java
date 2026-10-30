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
 * Base de datos falsa, en memoria, con las tres tablas de las enfermedades.
 *
 * <p>No es una prueba: reconoce exactamente las sentencias de {@link OracleDiseaseDao} y las ejecuta
 * sobre mapas, con transacciones (confirmar y deshacer), la clave primaria, la clave foránea y el
 * borrado en cascada que tiene el esquema real. Así se prueba el DAO sin Oracle. Que el SQL sea
 * válido en Oracle lo comprueba {@code OracleDiseaseDaoIntegrationTest}.</p>
 */
final class FakeDiseaseDatabase {

    private static final List<String> DISEASE_COLUMNS = List.of("id", "name", "main_specialty", "treatment",
            "curable", "requires_blood", "requires_icu", "deterioration_rate", "irreducible_mortality",
            "min_duration_minutes", "max_duration_minutes", "min_age", "max_age", "frequency");

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
            case "prepareStatement" -> prepare((String) args[0]);
            case "close" -> {
                connectionsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (Connection) Proxy.newProxyInstance(FakeDiseaseDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private PreparedStatement prepare(String sql) throws SQLException {
        if (failWhenContaining != null && sql.contains(failWhenContaining)) {
            throw new SQLException("fallo simulado");
        }
        Map<Integer, Object> parameters = new HashMap<>();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setInt", "setString", "setDouble" -> {
                parameters.put((Integer) args[0], args[1]);
                yield null;
            }
            case "executeUpdate" -> update(sql, parameters);
            case "executeQuery" -> query(sql, parameters);
            case "close" -> {
                statementsClosed++;
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (PreparedStatement) Proxy.newProxyInstance(FakeDiseaseDatabase.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, handler);
    }

    // ---------- sentencias de cambio ----------

    private int update(String sql, Map<Integer, Object> p) throws SQLException {
        switch (sql) {
            case OracleDiseaseDao.INSERT_DISEASE -> {
                int id = (Integer) p.get(1);
                if (working.diseases.containsKey(id)) {
                    throw new SQLException("ORA-00001: restricción única (PK_DISEASE) violada");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < DISEASE_COLUMNS.size(); i++) {
                    row.put(DISEASE_COLUMNS.get(i), p.get(i + 1));
                }
                requireUniqueName(row);
                working.diseases.put(id, row);
                return 1;
            }
            case OracleDiseaseDao.INSERT_SECONDARY -> {
                int id = requireParent((Integer) p.get(1));
                List<String> list = working.secondary.computeIfAbsent(id, key -> new ArrayList<>());
                if (list.contains((String) p.get(2))) {
                    throw new SQLException("ORA-00001: restricción única (PK_DISEASE_SECONDARY) violada");
                }
                list.add((String) p.get(2));
                return 1;
            }
            case OracleDiseaseDao.INSERT_SYMPTOM -> {
                int id = requireParent((Integer) p.get(1));
                TreeMap<Integer, String> map = working.symptoms.computeIfAbsent(id, key -> new TreeMap<>());
                if (map.containsKey((Integer) p.get(2))) {
                    throw new SQLException("ORA-00001: restricción única (PK_DISEASE_SYMPTOM) violada");
                }
                map.put((Integer) p.get(2), (String) p.get(3));
                return 1;
            }
            case OracleDiseaseDao.UPDATE_DISEASE -> {
                Map<String, Object> row = working.diseases.get((Integer) p.get(14));
                if (row == null) {
                    return 0;
                }
                Map<String, Object> changed = new LinkedHashMap<>(row);
                for (int i = 1; i < DISEASE_COLUMNS.size(); i++) {
                    changed.put(DISEASE_COLUMNS.get(i), p.get(i));
                }
                requireUniqueName(changed);
                working.diseases.put((Integer) p.get(14), changed);
                return 1;
            }
            case OracleDiseaseDao.DELETE_SECONDARY -> {
                List<String> removed = working.secondary.remove((Integer) p.get(1));
                return removed == null ? 0 : removed.size();
            }
            case OracleDiseaseDao.DELETE_SYMPTOMS -> {
                TreeMap<Integer, String> removed = working.symptoms.remove((Integer) p.get(1));
                return removed == null ? 0 : removed.size();
            }
            case OracleDiseaseDao.DELETE_DISEASE -> {
                int id = (Integer) p.get(1);
                working.secondary.remove(id);
                working.symptoms.remove(id);
                return working.diseases.remove(id) == null ? 0 : 1;
            }
            default -> throw new SQLException("Sentencia de cambio no reconocida: " + sql);
        }
    }

    private int requireParent(int diseaseId) throws SQLException {
        if (!working.diseases.containsKey(diseaseId)) {
            throw new SQLException("ORA-02291: restricción de integridad (FK) violada - no se encontró la clave principal");
        }
        return diseaseId;
    }

    private void requireUniqueName(Map<String, Object> candidate) throws SQLException {
        for (Map<String, Object> other : working.diseases.values()) {
            if (!other.get("id").equals(candidate.get("id")) && other.get("name").equals(candidate.get("name"))) {
                throw new SQLException("ORA-00001: restricción única (UQ_DISEASE_NAME) violada");
            }
        }
    }

    // ---------- consultas ----------

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleDiseaseDao.SELECT_DISEASES -> working.diseases.values().forEach(rows::add);
            case OracleDiseaseDao.SELECT_DISEASE -> {
                Map<String, Object> row = working.diseases.get((Integer) p.get(1));
                if (row != null) {
                    rows.add(row);
                }
            }
            case OracleDiseaseDao.SELECT_SECONDARY, OracleDiseaseDao.SELECT_SECONDARY_OF -> {
                Integer only = (Integer) p.get(1);
                new TreeMap<>(working.secondary).forEach((id, list) -> {
                    if (only == null || only.equals(id)) {
                        list.stream().sorted().forEach(name -> rows.add(Map.of("disease_id", id, "specialty", name)));
                    }
                });
            }
            case OracleDiseaseDao.SELECT_SYMPTOMS, OracleDiseaseDao.SELECT_SYMPTOMS_OF -> {
                Integer only = (Integer) p.get(1);
                new TreeMap<>(working.symptoms).forEach((id, map) -> {
                    if (only == null || only.equals(id)) {
                        map.values().forEach(name -> rows.add(Map.of("disease_id", id, "symptom", name)));
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