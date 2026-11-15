package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del error de la IA.
 */
class AiClientExceptionTest {

    @Test
    void explainsTheReasonToTheUser() {
        AiClientException error = new AiClientException(AiClientException.Reason.QUOTA, null);

        assertEquals(AiClientException.Reason.QUOTA, error.getReason());
        assertEquals("Se agotó la cuota de la IA; se reinicia cada día", error.getMessage());
    }

    @Test
    void addsTheDetailAndKeepsTheCause() {
        IOException cause = new IOException("sin red");
        AiClientException error = new AiClientException(AiClientException.Reason.NETWORK, "sin red", cause);

        assertEquals("No hay conexión con la IA: sin red", error.getMessage());
        assertSame(cause, error.getCause());
    }

    @Test
    void needsAReason() {
        assertThrows(IllegalArgumentException.class, () -> new AiClientException(null, "x"));
    }
}
