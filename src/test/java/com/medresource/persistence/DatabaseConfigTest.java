package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
/**
 * Pruebas de los datos de conexión a la base de datos.
 */
class DatabaseConfigTest {

    private static final String URL = "jdbc:oracle:thin:@//localhost:1521/XEPDB1";

    private static Properties properties(String url, String user, String password) {
        Properties properties = new Properties();
        if (url != null) {
            properties.setProperty("db.url", url);
        }
        if (user != null) {
            properties.setProperty("db.user", user);
        }
        if (password != null) {
            properties.setProperty("db.password", password);
        }
        return properties;
    }

    private static Path fileWith(String content) throws IOException {
        Path file = Files.createTempFile("db-config", ".properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
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

    // ---------- lectura ----------

    @Test
    void readsTheThreePropertiesIgnoringSurroundingSpaces() {
        DatabaseConfig config = DatabaseConfig.fromProperties(properties("  " + URL + " ", " usuario ", " clave \t"));

        assertEquals(URL, config.url());
        assertEquals("usuario", config.user());
        assertEquals("clave", config.password());
    }

    @Test
    void keepsTheSpacesInsideThePassword() {
        assertEquals("mi clave", DatabaseConfig.fromProperties(properties(URL, "u", "mi clave")).password());
    }

    @Test
    void rejectsPropertiesWithMissingData() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromProperties(properties(null, "u", "p")));
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromProperties(properties(URL, null, "p")));
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromProperties(properties(URL, "u", null)));
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromProperties(null));
    }

    @Test
    void readsAFileWithAccentedText() throws IOException {
        Path file = fileWith("db.url=" + URL + "\ndb.user=médico\ndb.password=contraseña\n");
        try {
            DatabaseConfig config = DatabaseConfig.fromFile(file);

            assertEquals(URL, config.url());
            assertEquals("médico", config.user());
            assertEquals("contraseña", config.password());
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void anIncompleteFileIsRejected() throws IOException {
        Path file = fileWith("db.url=" + URL + "\ndb.user=medresource\n");
        try {
            assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromFile(file));
        } finally {
            Files.delete(file);
        }
    }

    @Test
    void aMissingFileIsADataAccessError() throws IOException {
        Path file = Files.createTempFile("db-config", ".properties");
        Files.delete(file);

        DataAccessException exception = assertThrows(DataAccessException.class, () -> DatabaseConfig.fromFile(file));

        assertTrue(exception.getMessage().contains(file.toString()));
    }

    @Test
    void aFolderIsNotAConfigurationFile() throws IOException {
        Path folder = Files.createTempDirectory("db-config");
        try {
            assertThrows(DataAccessException.class, () -> DatabaseConfig.fromFile(folder));
        } finally {
            Files.delete(folder);
        }
    }

    @Test
    void aMissingPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromFile(null));
    }

    @Test
    void readsAResourceFromTheClasspath() {
        DatabaseConfig config = DatabaseConfig.fromResource("/db-test.properties");

        assertEquals("jdbc:oracle:thin:@//servidor-de-prueba:1521/PRUEBA", config.url());
        assertEquals("usuario_de_prueba", config.user());
        assertEquals("clave de prueba ñ", config.password());
    }

    @Test
    void theCredentialsFileIsReadFromTheMainResources() {
        assertEquals("/db.properties", DatabaseConfig.RESOURCE_NAME);
    }

    @Test
    void aMissingResourceExplainsHowToCreateIt() {
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> DatabaseConfig.fromResource("/no-existe.properties"));

        assertTrue(exception.getMessage().contains("/no-existe.properties"));
        assertTrue(exception.getMessage().contains("db.properties.example"));
    }
}