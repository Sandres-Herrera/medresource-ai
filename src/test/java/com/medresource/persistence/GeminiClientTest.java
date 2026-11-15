package com.medresource.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del cliente de Gemini con un transporte falso: sin internet ni clave.
 */
class GeminiClientTest {

    private static final AiConfig CONFIG = new AiConfig("clave-secreta", "gemini-flash-lite-latest", 30);

    /** Respuesta de Gemini con una parte de razonamiento y dos de texto. */
    private static final String OK_BODY = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"text":"pensando...","thought":true},
              {"text":"{\\"resumen\\":"},
              {"text":"\\"Hola\\"}"}]}}]}
            """;

    /** Transporte falso que recuerda lo que se le envió y devuelve una respuesta fija. */
    private static final class FakeTransport implements GeminiClient.Transport {
        private final GeminiClient.Response response;
        private URI uri;
        private String apiKey;
        private String body;
        private Duration timeout;

        FakeTransport(int status, String body) {
            this.response = new GeminiClient.Response(status, body);
        }

        @Override
        public GeminiClient.Response send(URI uri, String apiKey, String body, Duration timeout) {
            this.uri = uri;
            this.apiKey = apiKey;
            this.body = body;
            this.timeout = timeout;
            return response;
        }
    }

    @Test
    void sendsThePromptToTheModelWithTheKey() {
        FakeTransport transport = new FakeTransport(200, OK_BODY);
        new GeminiClient(CONFIG, transport).generate("Redacta el reporte.");

        assertEquals(GeminiClient.BASE_URL + "gemini-flash-lite-latest:generateContent", transport.uri.toString());
        assertEquals("clave-secreta", transport.apiKey);
        assertEquals(Duration.ofSeconds(30), transport.timeout);
        JsonObject body = JsonParser.parseString(transport.body).getAsJsonObject();
        assertEquals("Redacta el reporte.", body.getAsJsonArray("contents").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("application/json", body.getAsJsonObject("generationConfig").get("responseMimeType")
                .getAsString());
    }

    @Test
    void joinsTheTextPartsAndSkipsTheReasoning() {
        String text = new GeminiClient(CONFIG, new FakeTransport(200, OK_BODY)).generate("x");

        assertEquals("{\"resumen\":\"Hola\"}", text);
    }

    @Test
    void acceptsTheModelNameWithItsPrefix() {
        GeminiClient client = new GeminiClient(new AiConfig("k", "models/gemini-x", 30), new FakeTransport(200, OK_BODY));

        assertTrue(client.endpoint().toString().endsWith("/models/gemini-x:generateContent"));
        assertEquals("models/gemini-x", client.modelName());
    }

    @Test
    void anExhaustedQuotaIsAQuotaError() {
        AiClientException error = assertThrows(AiClientException.class, () -> new GeminiClient(CONFIG,
                new FakeTransport(429, "{\"error\":{\"message\":\"Quota exceeded\"}}")).generate("x"));

        assertEquals(AiClientException.Reason.QUOTA, error.getReason());
        assertTrue(error.getMessage().contains("Quota exceeded"));
    }

    @Test
    void aWrongKeyOrModelIsARejectedRequestAndTheKeyIsNeverShown() {
        AiClientException error = assertThrows(AiClientException.class, () -> new GeminiClient(CONFIG,
                new FakeTransport(400, "{\"error\":{\"message\":\"API key not valid\"}}")).generate("x"));

        assertEquals(AiClientException.Reason.REJECTED, error.getReason());
        assertFalse(error.getMessage().contains("clave-secreta"));
        assertEquals(AiClientException.Reason.SERVER, GeminiClient.errorFor(503, "no json").getReason());
    }

    @Test
    void aSlowAnswerIsATimeoutAndNoNetworkIsANetworkError() {
        GeminiClient slow = new GeminiClient(CONFIG, (uri, key, body, timeout) -> {
            throw new HttpTimeoutException("lento");
        });
        GeminiClient offline = new GeminiClient(CONFIG, (uri, key, body, timeout) -> {
            throw new IOException("sin red");
        });

        assertEquals(AiClientException.Reason.TIMEOUT,
                assertThrows(AiClientException.class, () -> slow.generate("x")).getReason());
        assertEquals(AiClientException.Reason.NETWORK,
                assertThrows(AiClientException.class, () -> offline.generate("x")).getReason());
    }

    @Test
    void anAnswerWithoutTextCannotBeRead() {
        assertEquals(AiClientException.Reason.INVALID_RESPONSE, assertThrows(AiClientException.class,
                () -> GeminiClient.textOf("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")).getReason());
        assertEquals(AiClientException.Reason.INVALID_RESPONSE,
                assertThrows(AiClientException.class, () -> GeminiClient.textOf("<html>")).getReason());
        assertEquals(AiClientException.Reason.INVALID_RESPONSE, assertThrows(AiClientException.class,
                () -> GeminiClient.textOf("{\"candidates\":[{\"content\":{\"parts\":[]}}]}")).getReason());
    }

    @Test
    void needsAPromptAndAConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeminiClient(CONFIG, new FakeTransport(200, OK_BODY)).generate(" "));
        assertThrows(IllegalArgumentException.class, () -> new GeminiClient(null));
    }
}
