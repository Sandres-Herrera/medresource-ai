package com.medresource.persistence;

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

    /** Muestra la dirección y el usuario, y oculta la clave. */
    @Override
    public String toString() {
        return "DatabaseConfig[url=" + url + ", user=" + user + ", password=****]";
    }
}