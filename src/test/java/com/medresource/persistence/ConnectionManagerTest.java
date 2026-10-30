package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del administrador de conexiones.
 */
class ConnectionManagerTest {

    private static final DatabaseConfig CONFIG =
            new DatabaseConfig("jdbc:oracle:thin:@//localhost:1521/XEPDB1", "medresource", "secreta123");

    @Test
    void opensTheConnectionWithTheConfiguredData() {
        List<String> received = new ArrayList<>();
        Connection fake = FakeJdbc.connection(new FakeJdbc.Recorder());
        ConnectionManager manager = new ConnectionManager(CONFIG, (url, user, password) -> {
            received.add(url);
            received.add(user);
            received.add(password);
            return fake;
        });

        Connection connection = manager.getConnection();

        assertSame(fake, connection);
        assertEquals(List.of("jdbc:oracle:thin:@//localhost:1521/XEPDB1", "medresource", "secreta123"), received);
    }

    @Test
    void everyCallOpensANewConnection() {
        FakeJdbc.Recorder recorder = new FakeJdbc.Recorder();
        ConnectionManager manager = new ConnectionManager(CONFIG, (url, user, password) -> FakeJdbc.connection(recorder));

        assertNotSame(manager.getConnection(), manager.getConnection());
    }

    @Test
    void translatesAFailedConnectionIntoADataAccessException() {
        SQLException original = new SQLException("ORA-12514: servicio desconocido");
        ConnectionManager manager = new ConnectionManager(CONFIG, (url, user, password) -> {
            throw original;
        });

        DataAccessException exception = assertThrows(DataAccessException.class, manager::getConnection);

        assertSame(original, exception.getCause());
        assertTrue(exception.getMessage().contains("ORA-12514"));
        assertTrue(exception.getMessage().contains("jdbc:oracle:thin:@//localhost:1521/XEPDB1"));
        assertTrue(exception.getMessage().contains("medresource"));
    }

    @Test
    void theErrorMessageNeverShowsThePassword() {
        ConnectionManager manager = new ConnectionManager(CONFIG, (url, user, password) -> {
            throw new SQLException("ORA-01017: usuario o clave inválidos");
        });

        DataAccessException exception = assertThrows(DataAccessException.class, manager::getConnection);

        assertFalse(exception.getMessage().contains("secreta123"));
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionManager(null));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionManager(null, (u, n, p) -> null));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionManager(CONFIG, null));
    }
}