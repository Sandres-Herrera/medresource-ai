package com.medresource.persistence;

/**
 * Error al pedirle un texto a la IA, con el motivo para explicárselo al usuario.
 *
 * <p>Nunca incluye la clave de la API en el mensaje.</p>
 */
public class AiClientException extends RuntimeException {

    /** Motivo del error. */
    public enum Reason {

        QUOTA("Se agotó la cuota de la IA; se reinicia cada día"),
        TIMEOUT("La IA no respondió a tiempo"),
        NETWORK("No hay conexión con la IA"),
        REJECTED("La IA rechazó la solicitud (revisa la clave y el modelo)"),
        SERVER("El servicio de la IA falló"),
        INVALID_RESPONSE("La respuesta de la IA no se pudo leer");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        /**
         * Devuelve la explicación para el usuario.
         *
         * @return mensaje en español
         */
        public String getMessage() {
            return message;
        }
    }

    private final Reason reason;

    /**
     * Crea el error.
     *
     * @param reason motivo
     * @param detail detalle técnico, sin la clave; puede ser {@code null}
     * @param cause  causa original; puede ser {@code null}
     * @throws IllegalArgumentException si falta el motivo
     */
    public AiClientException(Reason reason, String detail, Throwable cause) {
        super(messageOf(reason, detail), cause);
        this.reason = reason;
    }

    /**
     * Crea el error sin causa original.
     *
     * @param reason motivo
     * @param detail detalle técnico, sin la clave; puede ser {@code null}
     */
    public AiClientException(Reason reason, String detail) {
        this(reason, detail, null);
    }

    public Reason getReason() {
        return reason;
    }

    private static String messageOf(Reason reason, String detail) {
        if (reason == null) {
            throw new IllegalArgumentException("El motivo del error es obligatorio.");
        }
        return detail == null || detail.isBlank() ? reason.getMessage() : reason.getMessage() + ": " + detail;
    }
}
