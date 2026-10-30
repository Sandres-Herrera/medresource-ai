package com.medresource.services.strategy;

import com.medresource.model.Objective;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Guarda y carga en una carpeta las redes entrenadas, una por objetivo.
 *
 * <p>Cada red es un archivo de texto con el nombre del objetivo, por ejemplo
 * {@code maximize_lives.txt}. Así se entrena una sola vez y después el programa
 * solo lee los pesos.</p>
 */
public class ModelStore {

    /** Carpeta donde el proyecto guarda sus redes entrenadas, junto a la carpeta del programa. */
    public static final Path DEFAULT_DIRECTORY = Path.of("models");

    private final Path directory;

    /**
     * Crea un almacén sobre una carpeta; la carpeta se crea al guardar la primera red.
     *
     * @param directory carpeta de los archivos
     * @throws IllegalArgumentException si la carpeta es nula
     */
    public ModelStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("La carpeta es obligatoria.");
        }
        this.directory = directory;
    }

    /**
     * Devuelve el archivo de un objetivo.
     *
     * @param objective objetivo de la red
     * @return ruta del archivo, exista o no
     * @throws IllegalArgumentException si el objetivo es nulo
     */
    public Path fileFor(Objective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("El objetivo es obligatorio.");
        }
        return directory.resolve(objective.name().toLowerCase() + ".txt");
    }

    /**
     * Indica si ya hay una red guardada para el objetivo.
     *
     * @param objective objetivo de la red
     * @return {@code true} si el archivo existe
     * @throws IllegalArgumentException si el objetivo es nulo
     */
    public boolean contains(Objective objective) {
        return Files.isRegularFile(fileFor(objective));
    }

    /**
     * Guarda la red de un objetivo; reemplaza la anterior.
     *
     * @param objective objetivo de la red
     * @param network   red entrenada
     * @throws IllegalArgumentException si el objetivo o la red son nulos
     * @throws UncheckedIOException     si no se puede escribir
     */
    public void save(Objective objective, NeuralNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("La red es obligatoria.");
        }
        Path file = fileFor(objective);
        try {
            Files.createDirectories(directory);
            network.save(file);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la red en " + file + ".", e);
        }
    }

    /**
     * Carga la red de un objetivo.
     *
     * @param objective objetivo de la red
     * @return la red, o vacío si todavía no se entrenó
     * @throws IllegalArgumentException si el objetivo es nulo o el archivo no es una red válida
     * @throws UncheckedIOException     si no se puede leer
     */
    public Optional<NeuralNetwork> load(Objective objective) {
        Path file = fileFor(objective);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(NeuralNetwork.load(file));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la red de " + file + ".", e);
        }
    }
}