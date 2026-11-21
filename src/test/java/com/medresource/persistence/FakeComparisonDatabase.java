package com.medresource.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
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
 * Base de datos falsa, en memoria, con la tabla de las comparaciones y el procedimiento que las guarda.
 *
 * <p>No es una prueba: reconoce exactamente las sentencias de {@link OracleComparisonDao} y las ejecuta sobre
 * mapas, con transacciones (confirmar y deshacer). El procedimiento {@code pr_guardar_comparacion} se imita con
 * sus reglas: las dos ejecuciones existen, son distintas y del mismo caso, tienen otra estrategia u otros recursos,
 * y el mismo par no se guarda dos veces. Las ejecuciones se declaran con {@link #addRun}. Que el PL/SQL compile y
 * haga lo mismo en Oracle lo comprueba {@code OracleComparisonDaoIntegrationTest}.</p>
 */
final class FakeComparisonDatabase {

    /** Hora a la que se guarda la primera comparación; cada una se guarda un minuto después de la anterior. */
    static final LocalDateTime FIRST_SAVE = LocalDateTime.of(2026, 11, 16, 11, 55);

    /** Datos de una ejecución que usa la validación. */
    private record Run(int caseId, String strategy, String resources) {
    }

    /** Contenido de la tabla y el siguiente identificador. */
    private static final class Tables {

        int nextId = 1;
        final Map<Integer, Map<String, Object>> comparisons = new TreeMap<>();

        Tables copy() {
            Tables copy = new Tables();
            copy.nextId = nextId;
            comparisons.forEach((id, row) -> copy.comparisons.put(id, new LinkedHashMap<>(row)));
            return copy;
        }
    }

    private Tables committed = new Tables();
    private Tables working = committed;
    private final Map<Integer, Run> runs = new HashMap<>();
    private LocalDateTime clock = FIRST_SAVE;

    /** Si no es {@code null}, preparar una sentencia que contenga este texto lanza un error. */
    String failWhenContaining;

    int commits;
    int rollbacks;
    int connectionsClosed;
    int statementsClosed;

    /** Administrador de conexiones que abre conexiones a esta base falsa. */
    ConnectionManager manager() {
        return new ConnectionManager(new DatabaseConfig("jdbc:falso", "usuario", "clave"),
                (url, user, password) -> connection());
    }

    /** Declara una ejecución guardada, para que las comparaciones puedan usarla. */
    void addRun(int runId, int caseId, String strategy, String resources) {
        runs.put(runId, new Run(caseId, strategy, resources));
    }

    /** Simula que Oracle borra en cascada las comparaciones de una ejecución que se borró. */
    void deleteRun(int runId) {
        runs.remove(runId);
        committed.comparisons.values().removeIf(row -> row.get("id_ejecucion_a").equals(runId)
                || row.get("id_ejecucion_b").equals(runId));
        working = committed;
    }

    // ---------- lo que ven las pruebas (solo lo confirmado) ----------

    List<Integer> comparisonIds() {
        return new ArrayList<>(committed.comparisons.keySet());
    }

    Object column(int id, String column) {
        return committed.comparisons.get(id).get(column);
    }

    /** Cambia a la fuerza un dato guardado, para simular datos dañados. */
    void corrupt(int id, String column, Object value) {
        committed.comparisons.get(id).put(column, value);
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
        return (Connection) Proxy.newProxyInstance(FakeComparisonDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    private void failIfAsked(String sql) throws SQLException {
        if (failWhenContaining != null && sql.contains(failWhenContaining)) {
            throw new SQLException("fallo simulado");
        }
    }

    private CallableStatement prepareCall(String sql) throws SQLException {
        failIfAsked(sql);
        Map<Integer, Object> parameters = new HashMap<>();
        Map<Integer, Object> outputs = new HashMap<>();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "setInt", "setString" -> {
                parameters.put((Integer) args[0], args[1]);
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
        return (CallableStatement) Proxy.newProxyInstance(FakeComparisonDatabase.class.getClassLoader(),
                new Class<?>[]{CallableStatement.class}, handler);
    }

    /** Imita los procedimientos de comparaciones: deja en {@code outputs} lo que Oracle devolvería. */
    private void call(String sql, Map<Integer, Object> p, Map<Integer, Object> outputs) throws SQLException {
        switch (sql) {
            case OracleComparisonDao.SAVE_COMPARISON -> save(p, outputs);
            case OracleComparisonDao.DELETE_COMPARISON -> outputs.put(2,
                    working.comparisons.remove((Integer) p.get(1)) == null ? 0 : 1);
            case OracleComparisonDao.SELECT_COMPARISON -> outputs.put(2, query(sql, p));
            case OracleComparisonDao.SELECT_BY_RUNS -> outputs.put(3, query(sql, p));
            case OracleComparisonDao.SELECT_COMPARISONS -> outputs.put(1, query(sql, p));
            default -> throw new SQLException("Llamada no reconocida: " + sql);
        }
    }

    /** Imita a {@code pr_guardar_comparacion}. */
    private void save(Map<Integer, Object> p, Map<Integer, Object> outputs) throws SQLException {
        int a = (Integer) p.get(1);
        int b = (Integer) p.get(2);
        String type = validate(a, b);
        for (Map.Entry<Integer, Map<String, Object>> entry : working.comparisons.entrySet()) {
            Map<String, Object> row = entry.getValue();
            int storedA = (Integer) row.get("id_ejecucion_a");
            int storedB = (Integer) row.get("id_ejecucion_b");
            if (Math.min(storedA, storedB) == Math.min(a, b) && Math.max(storedA, storedB) == Math.max(a, b)) {
                outputs.put(4, entry.getKey());
                outputs.put(5, 0);
                return;
            }
        }
        int id = working.nextId++;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("id_caso", runs.get(a).caseId());
        row.put("id_ejecucion_a", a);
        row.put("id_ejecucion_b", b);
        row.put("tipo", type);
        row.put("contenido_json", p.get(3));
        row.put("guardado_en", Timestamp.valueOf(clock));
        clock = clock.plusMinutes(1);
        working.comparisons.put(id, row);
        outputs.put(4, id);
        outputs.put(5, 1);
    }

    /** Imita a {@code pr_validar_comparacion}; si no es válida, lanza el error ORA-20003 con el motivo. */
    private String validate(int a, int b) throws SQLException {
        String reason = null;
        String type = null;
        if (a == b) {
            reason = "Elige dos ejecuciones distintas: una ejecución no se compara consigo misma.";
        } else if (!runs.containsKey(a) || !runs.containsKey(b)) {
            reason = "No existe la ejecución #" + (runs.containsKey(a) ? b : a) + ".";
        } else if (runs.get(a).caseId() != runs.get(b).caseId()) {
            reason = "Las ejecuciones #" + a + " y #" + b + " son de casos distintos.";
        } else {
            boolean strategy = !runs.get(a).strategy().equals(runs.get(b).strategy());
            boolean resources = !runs.get(a).resources().equals(runs.get(b).resources());
            if (strategy && resources) {
                type = "MIXED";
            } else if (strategy) {
                type = "STRATEGY";
            } else if (resources) {
                type = "RESOURCES";
            } else {
                reason = "Las dos ejecuciones tienen la misma estrategia y los mismos recursos: darían los mismos "
                        + "resultados.";
            }
        }
        if (reason != null) {
            throw new SQLException("ORA-20003: " + reason + "\nORA-06512: en \"PR_GUARDAR_COMPARACION\", línea 9",
                    "72000", OracleComparisonDao.INVALID_COMPARISON);
        }
        return type;
    }

    private ResultSet query(String sql, Map<Integer, Object> p) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (sql) {
            case OracleComparisonDao.SELECT_COMPARISON -> {
                Map<String, Object> row = working.comparisons.get((Integer) p.get(1));
                if (row != null) {
                    rows.add(row);
                }
            }
            case OracleComparisonDao.SELECT_BY_RUNS -> {
                int a = (Integer) p.get(1);
                int b = (Integer) p.get(2);
                working.comparisons.values().stream().filter(row -> {
                    int storedA = (Integer) row.get("id_ejecucion_a");
                    int storedB = (Integer) row.get("id_ejecucion_b");
                    return Math.min(storedA, storedB) == Math.min(a, b) && Math.max(storedA, storedB) == Math.max(a, b);
                }).forEach(rows::add);
            }
            case OracleComparisonDao.SELECT_COMPARISONS -> working.comparisons.values().stream()
                    .sorted(Comparator.comparing((Map<String, Object> row) -> (Timestamp) row.get("guardado_en"))
                            .thenComparing(row -> (Integer) row.get("id")).reversed())
                    .forEach(rows::add);
            default -> throw new SQLException("Consulta no reconocida: " + sql);
        }
        return resultSet(rows);
    }

    private ResultSet resultSet(List<Map<String, Object>> rows) {
        int[] position = {-1};
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "next" -> ++position[0] < rows.size();
            case "getInt" -> ((Number) rows.get(position[0]).get((String) args[0])).intValue();
            case "getString" -> (String) rows.get(position[0]).get((String) args[0]);
            case "getTimestamp" -> (Timestamp) rows.get(position[0]).get((String) args[0]);
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (ResultSet) Proxy.newProxyInstance(FakeComparisonDatabase.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, handler);
    }
}
