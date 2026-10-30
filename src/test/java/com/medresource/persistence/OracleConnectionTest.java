package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integración con Oracle: solo se ejecuta en los equipos que tienen
 * {@code src/main/resources/db.properties}; en los demás se omite.
 *
 * <p>Si el archivo existe pero Oracle está apagado o los datos son incorrectos, la prueba falla
 * con el mensaje de {@link DataAccessException}, que dice qué revisar.</p>
 */
class OracleConnectionTest {

    @Test
    void connectsToOracleAndRunsAQuery() throws SQLException {
        assumeTrue(DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) != null,
                "No hay db.properties: se omite la prueba con Oracle.");
        ConnectionManager manager = new ConnectionManager(DatabaseConfig.fromClasspath());

        try (Connection connection = manager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1 FROM DUAL")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }
}