package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la configuración de la IA.
 */
class AiConfigTest {

    private static Properties properties(String key, String model, String timeout) {
        Properties properties = new Properties();
        if (key != null) {
            properties.setProperty(AiConfig.API_KEY_KEY, key);
        }
        if (model != null) {
            properties.setProperty(AiConfig.MODEL_KEY, model);
        }
        if (timeout != null) {
            properties.setProperty(AiConfig.TIMEOUT_KEY, timeout);
        }
        return properties;
    }

    @Test
    void readsTheKeyTheModelAndTheWaitFromTheFile() {
        AiConfig config = AiConfig.from(properties(" clave-1 ", "gemini-x", "45"), null).orElseThrow();

        assertEquals("clave-1", config.apiKey());
        assertEquals("gemini-x", config.model());
        assertEquals(45, config.timeoutSeconds());
    }

    @Test
    void usesTheDefaultModelAndWait() {
        AiConfig config = AiConfig.from(properties("clave-1", null, null), null).orElseThrow();

        assertEquals(AiConfig.DEFAULT_MODEL, config.model());
        assertEquals(AiConfig.DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
    }

    @Test
    void withoutAKeyInTheFileItUsesTheEnvironmentVariable() {
        assertEquals("clave-env", AiConfig.from(properties(null, null, null), "clave-env").orElseThrow().apiKey());
        assertEquals("clave-env",
                AiConfig.from(properties(AiConfig.PLACEHOLDER, null, null), "clave-env").orElseThrow().apiKey());
    }

    @Test
    void withoutAnyKeyThereIsNoAi() {
        assertTrue(AiConfig.from(new Properties(), null).isEmpty());
        assertTrue(AiConfig.from(properties(AiConfig.PLACEHOLDER, null, null), " ").isEmpty());
    }

    @Test
    void aMissingFileGivesNoProperties() {
        assertTrue(AiConfig.readResource("/no-existe.properties").isEmpty());
    }

    @Test
    void neverShowsTheKey() {
        AiConfig config = new AiConfig("secreta-123", "gemini-x", 30);

        assertFalse(config.toString().contains("secreta-123"));
        assertTrue(config.toString().contains("gemini-x"));
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new AiConfig(" ", "gemini-x", 30));
        assertThrows(IllegalArgumentException.class, () -> new AiConfig("clave", "gemini-x", 2));
        assertThrows(IllegalArgumentException.class,
                () -> AiConfig.from(properties("clave", null, "mucho"), null));
        assertThrows(IllegalArgumentException.class, () -> AiConfig.from(null, null));
    }
}
