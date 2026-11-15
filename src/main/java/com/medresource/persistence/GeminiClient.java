package com.medresource.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente de la API de Gemini: le envía las instrucciones al modelo y devuelve el texto que responde.
 *
 * <p>Usa el método {@code generateContent} de la API REST
 * ({@value #BASE_URL}{@code <modelo>:generateContent}) con la clave en la cabecera {@code x-goog-api-key} y
 * pide la respuesta en JSON. Los errores se traducen a {@link AiClientException} con su motivo: 429 es la
 * cuota agotada; 400, 401, 403 y 404 son una solicitud rechazada (clave o modelo mal escritos); 500 o más es
 * una falla del servicio. La clave nunca aparece en los mensajes de error.</p>
 */
public class GeminiClient implements AiClient {

    /** Dirección de los modelos de la API de Gemini. */
    public static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    /** Qué tan creativa es la redacción: baja, para que se ciña a los hechos (supuesto). */
    public static final double TEMPERATURE = 0.3;

    /** Respuesta HTTP: el código de estado y el cuerpo. */
    record Response(int status, String body) {
    }

    /** Envía una solicitud HTTP; las pruebas lo reemplazan por uno falso. */
    @FunctionalInterface
    interface Transport {
        Response send(URI uri, String apiKey, String body, Duration timeout) throws IOException, InterruptedException;
    }

    private final AiConfig config;
    private final Transport transport;

    /**
     * Crea el cliente que se conecta de verdad a Gemini.
     *
     * @param config clave, modelo y espera
     * @throws IllegalArgumentException si falta la configuración
     */
    public GeminiClient(AiConfig config) {
        this(config, GeminiClient::sendHttp);
    }

    GeminiClient(AiConfig config, Transport transport) {
        if (config == null || transport == null) {
            throw new IllegalArgumentException("La configuración de la IA es obligatoria.");
        }
        this.config = config;
        this.transport = transport;
    }

    @Override
    public String generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Las instrucciones para la IA son obligatorias.");
        }
        Response response;
        try {
            response = transport.send(endpoint(), config.apiKey(), requestBody(prompt),
                    Duration.ofSeconds(config.timeoutSeconds()));
        } catch (HttpTimeoutException e) {
            throw new AiClientException(AiClientException.Reason.TIMEOUT,
                    "más de " + config.timeoutSeconds() + " s", e);
        } catch (IOException e) {
            throw new AiClientException(AiClientException.Reason.NETWORK, e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Reason.NETWORK, "la espera se interrumpió", e);
        }
        if (response.status() != 200) {
            throw errorFor(response.status(), response.body());
        }
        return textOf(response.body());
    }

    @Override
    public String modelName() {
        return config.model();
    }

    /**
     * Arma la dirección del modelo configurado. Acepta el nombre con o sin el prefijo {@code models/}.
     *
     * @return dirección del método {@code generateContent}
     */
    URI endpoint() {
        String model = config.model().startsWith("models/") ? config.model().substring(7) : config.model();
        return URI.create(BASE_URL + model + ":generateContent");
    }

    /**
     * Arma el cuerpo JSON de la solicitud: las instrucciones como un mensaje del usuario, temperatura baja y
     * respuesta en JSON.
     *
     * @param prompt instrucciones
     * @return cuerpo de la solicitud
     */
    static String requestBody(String prompt) {
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject generation = new JsonObject();
        generation.addProperty("temperature", TEMPERATURE);
        generation.addProperty("responseMimeType", "application/json");
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig", generation);
        return body.toString();
    }

    /**
     * Saca el texto de la respuesta: une el texto de las partes del primer candidato, sin las partes de
     * razonamiento interno.
     *
     * @param body cuerpo JSON de la respuesta
     * @return texto del modelo
     * @throws AiClientException si la respuesta no trae texto o no es JSON
     */
    static String textOf(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new AiClientException(AiClientException.Reason.INVALID_RESPONSE, blockReason(root));
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            StringBuilder text = new StringBuilder();
            if (content != null && content.has("parts")) {
                for (JsonElement element : content.getAsJsonArray("parts")) {
                    JsonObject part = element.getAsJsonObject();
                    boolean thought = part.has("thought") && part.get("thought").getAsBoolean();
                    if (!thought && part.has("text")) {
                        text.append(part.get("text").getAsString());
                    }
                }
            }
            if (text.isEmpty()) {
                throw new AiClientException(AiClientException.Reason.INVALID_RESPONSE, "la respuesta no trae texto");
            }
            return text.toString();
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            throw new AiClientException(AiClientException.Reason.INVALID_RESPONSE, "no es JSON válido", e);
        }
    }

    /**
     * Traduce un código de error HTTP al motivo del error, con el mensaje de Google si lo trae.
     *
     * @param status código HTTP distinto de 200
     * @param body   cuerpo de la respuesta
     * @return error para el usuario
     */
    static AiClientException errorFor(int status, String body) {
        String detail = "HTTP " + status + googleMessage(body);
        if (status == 429) {
            return new AiClientException(AiClientException.Reason.QUOTA, detail);
        }
        if (status >= 500) {
            return new AiClientException(AiClientException.Reason.SERVER, detail);
        }
        return new AiClientException(AiClientException.Reason.REJECTED, detail);
    }

    private static String googleMessage(String body) {
        try {
            JsonObject error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            return error != null && error.has("message") ? " (" + error.get("message").getAsString() + ")" : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String blockReason(JsonObject root) {
        JsonObject feedback = root.getAsJsonObject("promptFeedback");
        if (feedback != null && feedback.has("blockReason")) {
            return "la IA bloqueó la solicitud (" + feedback.get("blockReason").getAsString() + ")";
        }
        return "la respuesta no trae candidatos";
    }

    private static Response sendHttp(URI uri, String apiKey, String body, Duration timeout)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }
}
