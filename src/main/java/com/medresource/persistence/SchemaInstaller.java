package com.medresource.persistence;

import java.util.ArrayList;
import java.util.List;

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
}