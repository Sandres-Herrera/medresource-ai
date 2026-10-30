package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los datos de conexión a la base de datos.
 */
class DatabaseConfigTest {

    private static final String URL = "jdbc:oracle:thin:@//localhost:1521/XEPDB1";

    // ---------- datos ----------

    @Test
    void keepsTheConnectionData() {
        DatabaseConfig config = new DatabaseConfig(URL, "medresource", "secreta123");

        assertEquals(URL, config.url());
        assertEquals("medresource", config.user());
        assertEquals("secreta123", config.password());
    }

    @Test
    void rejectsAMissingOrBlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(null, "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig("", "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig("   ", "u", "p"));
    }

    @Test
    void rejectsAUrlThatDoesNotStartWithJdbc() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig("localhost:1521/XEPDB1", "u", "p"));
        new DatabaseConfig("jdbc:x", "u", "p");
    }

    @Test
    void rejectsAMissingOrBlankUser() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, null, "p"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "", "p"));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "  ", "p"));
    }

    @Test
    void rejectsAMissingOrEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "u", null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseConfig(URL, "u", ""));
    }

    @Test
    void aPasswordMadeOfSpacesIsStillAPassword() {
        assertEquals(" ", new DatabaseConfig(URL, "u", " ").password());
    }

    @Test
    void theTextNeverShowsThePassword() {
        String text = new DatabaseConfig(URL, "medresource", "secreta123").toString();

        assertFalse(text.contains("secreta123"));
        assertTrue(text.contains(URL));
        assertTrue(text.contains("medresource"));
    }
}