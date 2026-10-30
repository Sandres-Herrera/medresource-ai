package com.medresource.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Datos para conectarse a la base de datos: dirección, usuario y clave.
 *
 * <p>Se leen de un archivo local {@code db.properties} que está en {@code .gitignore}, así la
 * clave nunca se sube al repositorio. En {@code db.properties.example} está el formato.
 * El texto de {@link #toString()} oculta la clave para que no salga en mensajes ni registros.</p>
 *
 * @param url      dirección JDBC, por ejemplo {@code jdbc:oracle:thin:@//localhost:1521/XEPDB1}
 * @param user     usuario de la base de datos
 * @param password clave del usuario
 */
public record DatabaseConfig(String url, String user, String password) {

    /** Nombre del archivo de credenciales dentro de {@code src/main/resources}. */
    public static final String RESOURCE_NAME = "/db.properties";

    /** Propiedad con la dirección JDBC. */
    public static final String URL_KEY = "db.url";

    /** Propiedad con el usuario. */
    public static final String USER_KEY = "db.user";

    /** Propiedad con la clave. */
    public static final String PASSWORD_KEY = "db.password";
    /**
     * Valida los datos de conexión.
     *
     * @throws IllegalArgumentException si falta la dirección, no empieza por {@code jdbc:},
     *                                  o si falta el usuario o la clave
     */
    public DatabaseConfig {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("La dirección de la base de datos es obligatoria.");
        }
        if (!url.startsWith("jdbc:")) {
            throw new IllegalArgumentException("La dirección de la base de datos debe empezar por «jdbc:».");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("El usuario de la base de datos es obligatorio.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("La clave de la base de datos es obligatoria.");
        }
    }

    /**
     * Arma la configuración con las propiedades {@code db.url}, {@code db.user} y {@code db.password}.
     * Los espacios al inicio y al final de cada valor se ignoran.
     *
     * @param properties propiedades leídas de un archivo
     * @return configuración de conexión
     * @throws IllegalArgumentException si las propiedades son nulas o falta algún dato
     */
    public static DatabaseConfig fromProperties(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("Las propiedades son obligatorias.");
        }
        return new DatabaseConfig(trimmed(properties, URL_KEY), trimmed(properties, USER_KEY),
                trimmed(properties, PASSWORD_KEY));
    }

    /**
     * Lee la configuración de un archivo de propiedades.
     *
     * @param file archivo con el formato de {@code db.properties.example}
     * @return configuración de conexión
     * @throws IllegalArgumentException si el archivo no trae todos los datos
     * @throws DataAccessException      si el archivo no existe o no se puede leer
     */
    public static DatabaseConfig fromFile(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("El archivo de configuración es obligatorio.");
        }
        if (!Files.isRegularFile(file)) {
            throw new DataAccessException("No se encontró el archivo de configuración: " + file);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return fromProperties(load(reader));
        } catch (IOException e) {
            throw new DataAccessException("No se pudo leer el archivo de configuración: " + file, e);
        }
    }

    /**
     * Lee la configuración de {@code src/main/resources/db.properties}, que Maven copia al
     * classpath al compilar.
     *
     * @return configuración de conexión
     * @throws IllegalArgumentException si el archivo no trae todos los datos
     * @throws DataAccessException      si el archivo no existe o no se puede leer
     */
    public static DatabaseConfig fromClasspath() {
        return fromResource(RESOURCE_NAME);
    }

    static DatabaseConfig fromResource(String resourceName) {
        try (InputStream stream = DatabaseConfig.class.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new DataAccessException("No se encontró " + resourceName + ". Copia db.properties.example como "
                        + "db.properties en src/main/resources y completa tus datos.");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return fromProperties(load(reader));
            }
        } catch (IOException e) {
            throw new DataAccessException("No se pudo leer " + resourceName + ".", e);
        }
    }
    /** Muestra la dirección y el usuario, y oculta la clave. */
    @Override
    public String toString() {
        return "DatabaseConfig[url=" + url + ", user=" + user + ", password=****]";
    }

    private static Properties load(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        return properties;
    }

    private static String trimmed(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? null : value.strip();
    }
}