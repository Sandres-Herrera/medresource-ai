package com.medresource.persistence;

import com.medresource.model.RunComparison;
import com.medresource.model.SavedComparison;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guarda y lee en Oracle las comparaciones de dos ejecuciones (tabla {@code comparacion}).
 *
 * <p>Para guardar llama al procedimiento {@code pr_guardar_comparacion} (ver {@code db/plsql.sql}), que vuelve a
 * validar la comparación con las reglas de la base de datos y no repite un par de ejecuciones ya guardado. Así la
 * base de datos es la última palabra aunque la aplicación ya haya validado. Si el procedimiento rechaza la
 * comparación (error ORA-20003), el mensaje de la excepción es el motivo, listo para mostrarlo.</p>
 */
public class OracleComparisonDao implements ComparisonDao {

    /** {@code pr_guardar_comparacion(a, b, json, id, nueva)}: los dos últimos parámetros salen. */
    static final String SAVE_COMPARISON = "{call pr_guardar_comparacion(?, ?, ?, ?, ?)}";
    /** {@code pr_buscar_comparacion(id, cursor)}. */
    static final String SELECT_COMPARISON = "{call pr_buscar_comparacion(?, ?)}";
    /** {@code pr_comparacion_de_ejecuciones(a, b, cursor)}, en cualquier orden. */
    static final String SELECT_BY_RUNS = "{call pr_comparacion_de_ejecuciones(?, ?, ?)}";
    /** {@code pr_listar_comparaciones(cursor)}. */
    static final String SELECT_COMPARISONS = "{call pr_listar_comparaciones(?)}";
    /** {@code pr_borrar_comparacion(id, borrados)}: el segundo parámetro dice cuántas filas se borraron. */
    static final String DELETE_COMPARISON = "{call pr_borrar_comparacion(?, ?)}";

    /** Posición del parámetro de salida que dice cuántas filas borró {@link #DELETE_COMPARISON}. */
    private static final int DELETED_COUNT_POSITION = 2;

    /** Código del error con el que el procedimiento rechaza una comparación que no es válida. */
    static final int INVALID_COMPARISON = 20003;

    private final TransactionRunner transactions;

    /**
     * Crea el DAO.
     *
     * @param connections administrador de conexiones
     * @throws IllegalArgumentException si es nulo
     */
    public OracleComparisonDao(ConnectionManager connections) {
        this.transactions = new TransactionRunner(connections);
    }

    @Override
    public SavedComparison save(RunComparison comparison) {
        if (comparison == null) {
            throw new IllegalArgumentException("La comparación es obligatoria.");
        }
        String json = ComparisonJson.toJson(comparison);
        return transactions.run("guardar la comparación de las ejecuciones #" + comparison.runA() + " y #"
                + comparison.runB(), connection -> {
            int id;
            try (CallableStatement call = connection.prepareCall(SAVE_COMPARISON)) {
                call.setInt(1, comparison.runA());
                call.setInt(2, comparison.runB());
                call.setString(3, json);
                call.registerOutParameter(4, Types.NUMERIC);
                call.registerOutParameter(5, Types.NUMERIC);
                call.execute();
                id = call.getInt(4);
            } catch (SQLException e) {
                if (e.getErrorCode() == INVALID_COMPARISON) {
                    throw new DataAccessException(reasonOf(e), e);
                }
                throw e;
            }
            return load(connection, SELECT_COMPARISON, id).stream().findFirst().orElseThrow(
                    () -> new DataAccessException("La comparación " + id + " desapareció después de guardarse."));
        });
    }

    @Override
    public Optional<SavedComparison> findById(int id) {
        return transactions.run("buscar la comparación " + id,
                connection -> load(connection, SELECT_COMPARISON, id).stream().findFirst());
    }

    @Override
    public Optional<SavedComparison> findByRuns(int runA, int runB) {
        return transactions.run("buscar la comparación de las ejecuciones #" + runA + " y #" + runB,
                connection -> load(connection, SELECT_BY_RUNS, runA, runB).stream().findFirst());
    }

    @Override
    public List<SavedComparison> findAll() {
        return transactions.run("listar las comparaciones", connection -> load(connection, SELECT_COMPARISONS));
    }

    @Override
    public void deleteById(int id) {
        transactions.run("borrar la comparación " + id, connection -> {
            try (CallableStatement call = connection.prepareCall(DELETE_COMPARISON)) {
                call.setInt(1, id);
                call.registerOutParameter(DELETED_COUNT_POSITION, Types.INTEGER);
                call.execute();
                if (call.getInt(DELETED_COUNT_POSITION) == 0) {
                    throw new DataAccessException("No existe la comparación " + id + ".");
                }
            }
            return null;
        });
    }

    /**
     * Ejecuta un procedimiento que devuelve comparaciones en su cursor, que es el último parámetro (después de los
     * identificadores que recibe).
     */
    private static List<SavedComparison> load(Connection connection, String sql, int... parameters)
            throws SQLException {
        List<SavedComparison> comparisons = new ArrayList<>();
        try (CallableStatement call = connection.prepareCall(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                call.setInt(i + 1, parameters[i]);
            }
            int cursor = parameters.length + 1;
            call.registerOutParameter(cursor, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(cursor)) {
                while (rows.next()) {
                    comparisons.add(toComparison(rows));
                }
            }
        }
        return comparisons;
    }

    private static SavedComparison toComparison(ResultSet row) throws SQLException {
        int id = row.getInt("id");
        Timestamp savedAt = row.getTimestamp("guardado_en");
        if (savedAt == null) {
            throw new DataAccessException("La comparación " + id + " no tiene fecha de guardado.");
        }
        RunComparison comparison;
        try {
            comparison = ComparisonJson.fromJson(row.getString("contenido_json"));
        } catch (DataAccessException e) {
            throw new DataAccessException("La comparación " + id + " no se puede abrir: " + e.getMessage(), e);
        }
        return new SavedComparison(id, savedAt.toLocalDateTime(), comparison);
    }

    /**
     * Saca el motivo del mensaje de Oracle, que trae el código y las líneas del PL/SQL: de
     * «ORA-20003: Elige dos…\nORA-06512: …» queda «Elige dos…».
     */
    static String reasonOf(SQLException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String first = message.lines().findFirst().orElse("");
        int colon = first.indexOf(": ");
        String reason = first.startsWith("ORA-") && colon >= 0 ? first.substring(colon + 2) : first;
        return reason.isBlank() ? "La base de datos no aceptó la comparación." : reason.strip();
    }
}
