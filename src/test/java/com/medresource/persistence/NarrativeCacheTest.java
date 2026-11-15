package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la caché de las respuestas de la IA.
 */
class NarrativeCacheTest {

    private static Path newDirectory() throws IOException {
        return Files.createTempDirectory("ai-cache-test").resolve("cache");
    }

    @Test
    void theSameRequestHasTheSameKey() {
        String key = NarrativeCache.keyFor("gemini-x", "Redacta.");

        assertEquals(64, key.length());
        assertEquals(key, NarrativeCache.keyFor("gemini-x", "Redacta."));
        assertNotEquals(key, NarrativeCache.keyFor("gemini-y", "Redacta."));
        assertNotEquals(key, NarrativeCache.keyFor("gemini-x", "Redacta otra cosa."));
    }

    @Test
    void savesAndFindsAnAnswer() throws IOException {
        NarrativeCache cache = new NarrativeCache(newDirectory());
        String key = NarrativeCache.keyFor("gemini-x", "Redacta.");

        assertTrue(cache.find(key).isEmpty());
        cache.save(key, "{\"resumen\":\"Hola\"}");
        assertEquals("{\"resumen\":\"Hola\"}", cache.find(key).orElseThrow());
    }

    @Test
    void estimatesTheNextCallWithTheLastOnes() throws IOException {
        NarrativeCache cache = new NarrativeCache(newDirectory());

        assertEquals(NarrativeCache.DEFAULT_CALL_MILLIS, cache.averageCallMillis());
        cache.recordCallMillis(1000);
        cache.recordCallMillis(3000);
        assertEquals(2000, cache.averageCallMillis());
    }

    @Test
    void remembersOnlyTheLastCalls() throws IOException {
        NarrativeCache cache = new NarrativeCache(newDirectory());
        cache.recordCallMillis(100_000);
        for (int i = 0; i < NarrativeCache.TIMINGS_KEPT; i++) {
            cache.recordCallMillis(500);
        }

        assertEquals(500, cache.averageCallMillis());
    }

    @Test
    void rejectsInvalidData() throws IOException {
        NarrativeCache cache = new NarrativeCache(newDirectory());

        assertThrows(IllegalArgumentException.class, () -> cache.find("../../secreto"));
        assertThrows(IllegalArgumentException.class, () -> cache.save(NarrativeCache.keyFor("m", "p"), " "));
        assertThrows(IllegalArgumentException.class, () -> cache.recordCallMillis(-1));
        assertThrows(IllegalArgumentException.class, () -> new NarrativeCache(null));
    }
}
