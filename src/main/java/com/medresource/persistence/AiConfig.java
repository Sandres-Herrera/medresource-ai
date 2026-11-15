package com.medresource.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;

/**
 * Datos para usar la IA que redacta el reporte (Gemini): la clave de la API, el modelo y cuánto se espera la
 * respuesta.
 *
 * <p>Se leen de un archivo local {@code ai.properties} que está en {@code .gitignore}, así la clave nunca se
 * sube al repositorio; en {@code ai.properties.example} está el formato. Si el archivo no existe o no trae
 * clave, se usa la variable de entorno {@value #ENV_KEY}. Sin ninguna de las dos, no hay IA y el reporte se
 * redacta con plantillas. El texto de {@link #toString()} oculta la clave.</p>
 *
 * @param apiKey         clave de la API
 * @param model          modelo, por ejemplo {@value #DEFAULT_MODEL}
 * @param timeoutSeconds segundos que se espera la respuesta, de {@value #MIN_TIMEOUT_SECONDS} a
 *                       {@value #MAX_TIMEOUT_SECONDS}
 */
public record AiConfig(String apiKey, String model, int timeoutSeconds) {

    /** Nombre del archivo de configuración dentro de {@code src/main/resources}. */
    public static final String RESOURCE_NAME = "/ai.properties";

    /** Propiedad con la clave. */
    public static final String API_KEY_KEY = "gemini.apiKey";

    /** Propiedad con el modelo. */
    public static final String MODEL_KEY = "gemini.model";

    /** Propiedad con los segundos de espera. */
    public static final String TIMEOUT_KEY = "gemini.timeoutSeconds";

    /** Variable de entorno con la clave, si no hay archivo. */
    public static final String ENV_KEY = "GEMINI_API_KEY";

    /** Modelo cuando no se indica otro: siempre el Flash-Lite más reciente. */
    public static final String DEFAULT_MODEL = "gemini-flash-lite-latest";

    /** Segundos de espera cuando no se indican otros. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** Mínimo de segundos de espera. */
    public static final int MIN_TIMEOUT_SECONDS = 5;

    /** Máximo de segundos de espera. */
    public static final int MAX_TIMEOUT_SECONDS = 120;

    /** Texto del archivo de ejemplo en lugar de la clave: no es una clave. */
    static final String PLACEHOLDER = "PEGA_AQUI_TU_CLAVE";

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta la clave o el modelo, o si los segundos están fuera de rango
     */
    public AiConfig {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("La clave de la IA es obligatoria.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("El modelo de la IA es obligatorio.");
        }
        if (timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("La espera de la IA debe estar entre " + MIN_TIMEOUT_SECONDS + " y "
                    + MAX_TIMEOUT_SECONDS + " segundos.");
        }
    }

    /**
     * Arma la configuración con las propiedades del archivo y, si no traen clave, con la clave de la variable de
     * entorno. El modelo y la espera tienen valores por defecto.
     *
     * @param properties propiedades leídas del archivo; vacías si no hay archivo
     * @param envKey     valor de la variable de entorno; {@code null} si no existe
     * @return configuración; vacía si no hay clave en ninguna parte
     * @throws IllegalArgumentException si faltan las propiedades o si los segundos no son un número válido
     */
    public static Optional<AiConfig> from(Properties properties, String envKey) {
        if (properties == null) {
            throw new IllegalArgumentException("Las propiedades son obligatorias.");
        }
        String key = trimmed(properties.getProperty(API_KEY_KEY));
        if (key == null || key.equals(PLACEHOLDER)) {
            key = trimmed(envKey);
        }
        if (key == null) {
            return Optional.empty();
        }
        String model = trimmed(properties.getProperty(MODEL_KEY));
        String timeout = trimmed(properties.getProperty(TIMEOUT_KEY));
        int seconds;
        try {
            seconds = timeout == null ? DEFAULT_TIMEOUT_SECONDS : Integer.parseInt(timeout);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Los segundos de espera de la IA deben ser un número entero.", e);
        }
        return Optional.of(new AiConfig(key, model == null ? DEFAULT_MODEL : model, seconds));
    }

    /**
     * Lee la configuración de {@code src/main/resources/ai.properties} (que Maven copia al classpath) y de la
     * variable de entorno {@value #ENV_KEY}.
     *
     * @return configuración; vacía si no hay clave
     * @throws DataAccessException si el archivo existe pero no se puede leer
     */
    public static Optional<AiConfig> load() {
        return from(readResource(RESOURCE_NAME), System.getenv(ENV_KEY));
    }

    /** Muestra el modelo y la espera, y oculta la clave. */
    @Override
    public String toString() {
        return "AiConfig[apiKey=****, model=" + model + ", timeoutSeconds=" + timeoutSeconds + "]";
    }

    static Properties readResource(String resourceName) {
        Properties properties = new Properties();
        try (InputStream stream = AiConfig.class.getResourceAsStream(resourceName)) {
            if (stream == null) {
                return properties;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (IOException e) {
            throw new DataAccessException("No se pudo leer " + resourceName + ".", e);
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
