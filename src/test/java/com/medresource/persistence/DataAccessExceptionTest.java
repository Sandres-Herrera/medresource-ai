package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la excepción de acceso a datos.
 */
class DataAccessExceptionTest {

    @Test
    void keepsTheMessage() {
        DataAccessException exception = new DataAccessException("No hay conexión.");

        assertEquals("No hay conexión.", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void keepsTheMessageAndTheOriginalCause() {
        SQLException cause = new SQLException("ORA-12514");

        DataAccessException exception = new DataAccessException("No se pudo conectar.", cause);

        assertEquals("No se pudo conectar.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void isAnUncheckedException() {
        assertTrue(RuntimeException.class.isAssignableFrom(DataAccessException.class));
    }
}