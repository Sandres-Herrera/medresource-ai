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

    /** Ruta del script de funciones, procedimientos y triggers dentro del classpath. */
    public static final String PLSQL_RESOURCE = "/db/plsql.sql";

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
     * Separa un script PL/SQL en sus unidades (funciones, procedimientos y triggers). Cada unidad termina en una
     * línea que solo tiene {@code /}, como en SQL*Plus y SQL Developer; la línea de la barra no se incluye. Se
     * quitan las líneas en blanco y de comentario que hay antes de cada unidad, pero no las de adentro, porque
     * dentro de PL/SQL el {@code ;} y los comentarios son parte del código.
     *
     * @param script texto del script
     * @return unidades en el orden del script, cada una terminada en su {@code END ...;}
     * @throws IllegalArgumentException si el script es nulo
     */
    static List<String> programUnitsFrom(String script) {
        if (script == null) {
            throw new IllegalArgumentException("El script es obligatorio.");
        }
        List<String> units = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (line.strip().equals("/")) {
                addUnit(units, current);
                current.setLength(0);
            } else if (!current.isEmpty() || !(line.isBlank() || line.strip().startsWith(COMMENT_PREFIX))) {
                current.append(line).append('\n');
            }
        }
        addUnit(units, current);
        return units;
    }

    private static void addUnit(List<String> units, StringBuilder text) {
        String unit = text.toString().strip();
        if (!unit.isEmpty()) {
            units.add(unit);
        }
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