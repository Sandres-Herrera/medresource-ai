package com.medresource.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hace un trabajo con la base de datos dentro de una transacción.
 *
 * <p>Abre una conexión nueva, ejecuta el trabajo y, si termina bien, confirma los cambios; si falla,
 * los deshace. Siempre cierra la conexión. Lo usan los DAO de Oracle para que cada operación sea
 * atómica: o se guarda todo o no se guarda nada.</p>
 */
final class TransactionRunner {

    /** Trabajo que se hace con una conexión dentro de una transacción. */
    @FunctionalInterface
    interface SqlWork<R> {

        R run(Connection connection) throws SQLException;
    }

    private final ConnectionManager connections;

    /**
     * Crea el ejecutor.
     *
     * @param connections administrador de conexiones
     * @throws IllegalArgumentException si el administrador es nulo
     */
    TransactionRunner(ConnectionManager connections) {
        if (connections == null) {
            throw new IllegalArgumentException("El administrador de conexiones es obligatorio.");
        }
        this.connections = connections;
    }

    /**
     * Ejecuta el trabajo en una transacción.
     *
     * @param action descripción de lo que se hace, para el mensaje de error ("guardar el caso")
     * @param work   trabajo que se hace con la conexión
     * @param <R>    tipo del resultado
     * @return lo que devuelve el trabajo
     * @throws DataAccessException si Oracle rechaza la operación; las excepciones propias del trabajo
     *                             ({@link RuntimeException}) se dejan pasar sin cambios
     */
    <R> R run(String action, SqlWork<R> work) {
        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try {
                R result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo " + action + ": " + e.getMessage(), e);
        }
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }
}