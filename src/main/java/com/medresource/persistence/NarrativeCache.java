package com.medresource.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Caché en disco de las respuestas de la IA, y registro de cuánto tardan sus llamadas.
 *
 * <p>Cada respuesta se guarda en un archivo cuyo nombre es la huella SHA-256 de lo que se le pidió (modelo e
 * instrucciones): la misma simulación con la misma semilla produce las mismas instrucciones, así que su
 * reporte sale de la caché, sin internet y sin gastar cuota. Por eso la carpeta {@value #DEFAULT_DIRECTORY} se
 * puede subir al repositorio para la demostración.</p>
 *
 * <p>También guarda los milisegundos de las últimas {@value #TIMINGS_KEPT} llamadas en
 * {@value #TIMINGS_FILE}, para estimar cuánto tardará la próxima; sin llamadas anteriores se supone
 * {@value #DEFAULT_CALL_MILLIS} ms.</p>
 */
public class NarrativeCache {

    /** Carpeta de la caché, relativa a la carpeta desde donde se ejecuta la aplicación. */
    public static final String DEFAULT_DIRECTORY = "ai-cache";

    /** Archivo con los tiempos de las últimas llamadas. */
    public static final String TIMINGS_FILE = "tiempos.txt";

    /** Llamadas cuyo tiempo se recuerda. */
    public static final int TIMINGS_KEPT = 10;

    /** Milisegundos que se suponen para una llamada cuando no hay ninguna anterior (supuesto). */
    public static final long DEFAULT_CALL_MILLIS = 20_000;

    private final Path directory;

    /**
     * Crea la caché en una carpeta; la carpeta se crea al guardar la primera respuesta.
     *
     * @param directory carpeta de la caché
     * @throws IllegalArgumentException si falta la carpeta
     */
    public NarrativeCache(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("La carpeta de la caché es obligatoria.");
        }
        this.directory = directory;
    }

    /**
     * Crea la caché en {@value #DEFAULT_DIRECTORY}.
     *
     * @return caché en la carpeta por defecto
     */
    public static NarrativeCache inDefaultDirectory() {
        return new NarrativeCache(Path.of(DEFAULT_DIRECTORY));
    }

    /**
     * Calcula la clave de una solicitud: la huella SHA-256 del modelo y de las instrucciones.
     *
     * @param model  nombre del modelo
     * @param prompt instrucciones
     * @return 64 caracteres hexadecimales
     * @throws IllegalArgumentException si falta el modelo o las instrucciones
     */
    public static String keyFor(String model, String prompt) {
        if (model == null || prompt == null) {
            throw new IllegalArgumentException("El modelo y las instrucciones son obligatorios.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((model + "\n" + prompt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java no tiene SHA-256.", e);
        }
    }

    /**
     * Busca una respuesta guardada.
     *
     * @param key clave de la solicitud
     * @return la respuesta; vacía si no está
     * @throws DataAccessException si el archivo existe pero no se puede leer
     */
    public Optional<String> find(String key) {
        Path file = fileOf(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DataAccessException("No se pudo leer la caché de la IA: " + file, e);
        }
    }

    /**
     * Guarda una respuesta.
     *
     * @param key  clave de la solicitud
     * @param text respuesta de la IA
     * @throws IllegalArgumentException si falta el texto
     * @throws DataAccessException      si no se puede escribir
     */
    public void save(String key, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("La respuesta que se guarda no puede estar vacía.");
        }
        Path file = fileOf(key);
        try {
            Files.createDirectories(directory);
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("No se pudo escribir la caché de la IA: " + file, e);
        }
    }

    /**
     * Anota cuánto tardó una llamada; se guardan solo las últimas {@value #TIMINGS_KEPT}.
     *
     * @param millis milisegundos que tardó
     * @throws IllegalArgumentException si es negativo
     * @throws DataAccessException      si no se puede escribir
     */
    public void recordCallMillis(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("El tiempo de una llamada no puede ser negativo.");
        }
        List<Long> timings = new ArrayList<>(readTimings());
        timings.add(millis);
        List<Long> kept = timings.subList(Math.max(0, timings.size() - TIMINGS_KEPT), timings.size());
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(TIMINGS_FILE), kept.stream().map(String::valueOf).toList(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("No se pudieron guardar los tiempos de la IA.", e);
        }
    }

    /**
     * Estima cuánto tardará una llamada: el promedio de las últimas.
     *
     * @return milisegundos; {@value #DEFAULT_CALL_MILLIS} si todavía no hay llamadas anotadas
     */
    public long averageCallMillis() {
        List<Long> timings = readTimings();
        return timings.isEmpty() ? DEFAULT_CALL_MILLIS
                : Math.round(timings.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private List<Long> readTimings() {
        Path file = directory.resolve(TIMINGS_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<Long> timings = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    timings.add(Long.parseLong(line.trim()));
                } catch (NumberFormatException e) {
                    // Una línea dañada no impide estimar con las demás.
                }
            }
            return timings;
        } catch (IOException e) {
            throw new DataAccessException("No se pudieron leer los tiempos de la IA.", e);
        }
    }

    private Path fileOf(String key) {
        if (key == null || !key.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("La clave de la caché no es válida.");
        }
        return directory.resolve(key + ".json");
    }
}
