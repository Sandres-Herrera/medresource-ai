package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del ejecutor de transacciones, con una conexión falsa que anota lo que le piden.
 */
class TransactionRunnerTest {

    private final List<String> calls = new ArrayList<>();
    private boolean failOnRollback;
    private final TransactionRunner runner = new TransactionRunner(
            new ConnectionManager(new DatabaseConfig("jdbc:falso", "usuario", "clave"), (url, user, password) -> connection()));

    private Connection connection() {
        InvocationHandler handler = (proxy, method, args) -> {
            calls.add(method.getName() + (method.getName().equals("setAutoCommit") ? "(" + args[0] + ")" : ""));
            if (method.getName().equals("rollback") && failOnRollback) {
                throw new SQLException("no se pudo deshacer");
            }
            return null;
        };
        return (Connection) Proxy.newProxyInstance(TransactionRunnerTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, handler);
    }

    @Test
    void rejectsAMissingConnectionManager() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionRunner(null));
    }

    @Test
    void returnsTheResultOfTheWorkAfterCommitting() {
        String result = runner.run("hacer algo", connection -> "listo");

        assertEquals("listo", result);
        assertEquals(List.of("setAutoCommit(false)", "commit", "close"), calls);
    }

    @Test
    void undoesTheWorkAndExplainsAnSqlFailure() {
        DataAccessException error = assertThrows(DataAccessException.class, () -> runner.run("guardar el caso", connection -> {
            throw new SQLException("ORA-00001");
        }));

        assertEquals("No se pudo guardar el caso: ORA-00001", error.getMessage());
        assertTrue(error.getCause() instanceof SQLException);
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), calls);
    }

    @Test
    void undoesTheWorkAndLetsOtherErrorsThroughUnchanged() {
        IllegalStateException original = new IllegalStateException("regla rota");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> runner.run("hacer algo", connection -> {
            throw original;
        }));

        assertSame(original, thrown);
        assertEquals(List.of("setAutoCommit(false)", "rollback", "close"), calls);
    }

    @Test
    void keepsTheOriginalErrorWhenUndoingFailsToo() {
        failOnRollback = true;

        DataAccessException error = assertThrows(DataAccessException.class, () -> runner.run("hacer algo", connection -> {
            throw new SQLException("primer fallo");
        }));

        assertEquals("No se pudo hacer algo: primer fallo", error.getMessage());
        assertEquals("no se pudo deshacer", error.getCause().getSuppressed()[0].getMessage());
        assertEquals("close", calls.get(calls.size() - 1));
    }

    @Test
    void reportsAConnectionThatCannotBeOpened() {
        TransactionRunner broken = new TransactionRunner(new ConnectionManager(
                new DatabaseConfig("jdbc:falso", "usuario", "clave"), (url, user, password) -> {
            throw new SQLException("Oracle apagado");
        }));

        assertThrows(DataAccessException.class, () -> broken.run("hacer algo", connection -> "no llega"));
    }
}