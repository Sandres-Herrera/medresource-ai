package com.medresource.persistence;

/**
 * Error al acceder a la base de datos.
 *
 * <p>Traduce los errores propios de JDBC ({@link java.sql.SQLException}) a una excepción
 * del proyecto, de modo que las capas superiores no dependan de SQL. Es una excepción no
 * verificada: quien la recibe normalmente solo puede avisar al usuario.</p>
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Crea el error con un mensaje.
     *
     * @param message explicación del problema, en español
     */
    public DataAccessException(String message) {
        super(message);
    }

    /**
     * Crea el error con un mensaje y la causa original.
     *
     * @param message explicación del problema, en español
     * @param cause   error original, normalmente un {@link java.sql.SQLException}
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}