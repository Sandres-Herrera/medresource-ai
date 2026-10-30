package com.medresource.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Abre las conexiones con la base de datos.
 *
 * <p>Es la única clase que sabe cómo se llega a Oracle: los DAO le piden una conexión y la
 * cierran al terminar (con {@code try-with-resources}). Si la conexión falla, traduce el error
 * de JDBC a una {@link DataAccessException} cuyo mensaje nunca incluye la clave.</p>
 */
public class ConnectionManager {

    /** Forma de abrir una conexión; permite probar sin una base de datos real. */
    @FunctionalInterface
    interface ConnectionOpener {

        Connection open(String url, String user, String password) throws SQLException;
    }

    private final DatabaseConfig config;
    private final ConnectionOpener opener;

    /**
     * Crea un administrador que se conecta con {@link DriverManager}.
     *
     * @param config datos de conexión
     * @throws IllegalArgumentException si la configuración es nula
     */
    public ConnectionManager(DatabaseConfig config) {
        this(config, DriverManager::getConnection);
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
}