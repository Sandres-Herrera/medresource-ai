package com.medresource.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Abre las conexiones con la base de datos.
 *
 * <p>Es la única clase que sabe cómo se llega a Oracle: los DAO le piden una conexión y la
 * cierran al terminar (con {@code try-with-resources}). Si la conexión falla, traduce el error
 * de JDBC a una {@link DataAccessException} cuyo mensaje nunca incluye la clave.</p>
 *
 * <p>Las conexiones se abren con la propiedad {@value #BINARY_DOUBLES} del driver de Oracle. Sin ella,
 * {@code setDouble} envía cada {@code double} como un {@code NUMBER} de 15 cifras y Oracle lo guarda redondeado en
 * las columnas {@code BINARY_DOUBLE} (91.44319999999999 queda como 91.4432). Con ella se envían los bits exactos y
 * lo que se lee es justo lo que se guardó.</p>
 */
public class ConnectionManager {

    /** Forma de abrir una conexión; permite probar sin una base de datos real. */
    @FunctionalInterface
    interface ConnectionOpener {

        Connection open(String url, String user, String password) throws SQLException;
    }

    /** Propiedad del driver de Oracle que envía los {@code double} en binario, sin redondearlos. */
    static final String BINARY_DOUBLES = "SetFloatAndDoubleUseBinary";

    private final DatabaseConfig config;
    private final ConnectionOpener opener;

    /**
     * Crea un administrador que se conecta con {@link DriverManager}.
     *
     * @param config datos de conexión
     * @throws IllegalArgumentException si la configuración es nula
     */
    public ConnectionManager(DatabaseConfig config) {
        this(config, (url, user, password) -> DriverManager.getConnection(url, properties(user, password)));
    }

    ConnectionManager(DatabaseConfig config, ConnectionOpener opener) {
        if (config == null || opener == null) {
            throw new IllegalArgumentException("La configuración de la base de datos es obligatoria.");
        }
        this.config = config;
        this.opener = opener;
    }

    /**
     * Abre una conexión nueva. Quien la recibe debe cerrarla.
     *
     * @return conexión abierta
     * @throws DataAccessException si no se puede conectar (base apagada, usuario o clave incorrectos…)
     */
    public Connection getConnection() {
        try {
            return opener.open(config.url(), config.user(), config.password());
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo conectar con la base de datos (" + config.url()
                    + ", usuario " + config.user() + "): " + e.getMessage(), e);
        }
    }

    /**
     * Propiedades con las que se abre cada conexión: el usuario, la clave y los {@code double} en binario.
     *
     * @param user     usuario de la base de datos
     * @param password clave
     * @return las propiedades
     */
    static Properties properties(String user, String password) {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty(BINARY_DOUBLES, "true");
        return properties;
    }
}
