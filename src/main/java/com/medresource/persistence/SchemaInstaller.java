package com.medresource.persistence;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Crea las tablas de la base de datos ejecutando el script {@code db/schema.sql}.
 *
 * <p>El mismo script se puede correr a mano en SQL*Plus; esta clase permite hacerlo desde la
 * aplicación. Si las tablas ya existen, Oracle rechaza el script y se lanza una
 * {@link DataAccessException}: primero hay que ejecutar {@code db/drop_schema.sql}.</p>
 */
public class SchemaInstaller {

    /** Ruta del script de creación dentro del classpath. */
    public static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private static final String COMMENT_PREFIX = "--";

    private final ConnectionManager connections;

    /**
     * Prepara el instalador.
     *
     * @param connections administrador de conexiones
     * @throws IllegalArgumentException si el administrador es nulo
     */
    public SchemaInstaller(ConnectionManager connections) {
        if (connections == null) {
            throw new IllegalArgumentException("El administrador de conexiones es obligatorio.");
        }
        this.connections = connections;
    }

    /**
     * Separa un script en sus sentencias. Ignora las líneas de comentario (que empiezan con
     * {@code --}) y los espacios sobrantes; cada sentencia termina en {@code ;}, que no se incluye.
     *
     * <p>Solo sirve para scripts simples, sin bloques PL/SQL ni textos que contengan {@code ;}.</p>
     *
     * @param script texto del script
     * @return sentencias en el orden del script
     * @throws IllegalArgumentException si el script es nulo
     */
    static List<String> statementsFrom(String script) {
        if (script == null) {
            throw new IllegalArgumentException("El script es obligatorio.");
        }
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.strip().startsWith(COMMENT_PREFIX)) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            if (!part.isBlank()) {
                statements.add(part.strip());
            }
        }
        return statements;
    }

    /**
     * Crea todas las tablas.
     *
     * @throws DataAccessException si no se encuentra el script, no se puede conectar o Oracle rechaza alguna sentencia
     */
    public void install() {
        List<String> statements = statementsFrom(readScript());
        try (Connection connection = connections.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo crear el esquema de la base de datos: " + e.getMessage(), e);
        }
    }

    private static String readScript() {
        try (InputStream stream = SchemaInstaller.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new DataAccessException("No se encontró el script " + SCHEMA_RESOURCE + ".");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("No se pudo leer el script " + SCHEMA_RESOURCE + ".", e);
        }
    }
}