package com.medresource.services.strategy;

import com.medresource.model.Objective;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del almacén de redes entrenadas.
 */
class ModelStoreTest {

    private static final double[] SAMPLE = new double[FeatureExtractor.FEATURE_COUNT];

    private static Path newFolder() throws IOException {
        return Files.createTempDirectory("modelos-");
    }

    private static void deleteAll(Path folder) throws IOException {
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private static NeuralNetwork network(long seed) {
        return new NeuralNetwork(seed, FeatureExtractor.FEATURE_COUNT, 4, 1);
    }

    @Test
    void eachObjectiveHasItsOwnFile() throws IOException {
        Path folder = newFolder();
        try {
            ModelStore store = new ModelStore(folder);
            Set<Path> files = new HashSet<>();
            for (Objective objective : Objective.values()) {
                files.add(store.fileFor(objective));
            }

            assertEquals(Objective.values().length, files.size());
            assertEquals(folder.resolve("maximize_lives.txt"), store.fileFor(Objective.MAXIMIZE_LIVES));
            assertEquals(folder.resolve("minimize_wait.txt"), store.fileFor(Objective.MINIMIZE_WAIT));
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void savesAndLoadsANetworkWithTheSamePredictions() throws IOException {
        Path folder = newFolder();
        try {
            ModelStore store = new ModelStore(folder);
            NeuralNetwork original = network(5);
            store.save(Objective.EQUITY, original);

            NeuralNetwork loaded = store.load(Objective.EQUITY).orElseThrow();

            assertEquals(original.predict(SAMPLE), loaded.predict(SAMPLE), 1e-12);
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void keepsEachObjectivesNetworkApart() throws IOException {
        Path folder = newFolder();
        try {
            ModelStore store = new ModelStore(folder);
            NeuralNetwork lives = network(1);
            NeuralNetwork wait = network(2);
            store.save(Objective.MAXIMIZE_LIVES, lives);
            store.save(Objective.MINIMIZE_WAIT, wait);

            assertEquals(lives.predict(SAMPLE), store.load(Objective.MAXIMIZE_LIVES).orElseThrow().predict(SAMPLE), 1e-12);
            assertEquals(wait.predict(SAMPLE), store.load(Objective.MINIMIZE_WAIT).orElseThrow().predict(SAMPLE), 1e-12);
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void savingAgainReplacesTheNetwork() throws IOException {
        Path folder = newFolder();
        try {
            ModelStore store = new ModelStore(folder);
            store.save(Objective.EQUITY, network(1));
            NeuralNetwork newer = network(2);
            store.save(Objective.EQUITY, newer);

            assertEquals(newer.predict(SAMPLE), store.load(Objective.EQUITY).orElseThrow().predict(SAMPLE), 1e-12);
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void createsTheFolderWhenSaving() throws IOException {
        Path folder = newFolder();
        try {
            Path inner = folder.resolve("a").resolve("b");
            new ModelStore(inner).save(Objective.EQUITY, network(1));

            assertTrue(Files.isRegularFile(inner.resolve("equity.txt")));
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void knowsWhichObjectivesHaveANetwork() throws IOException {
        Path folder = newFolder();
        try {
            ModelStore store = new ModelStore(folder);
            assertFalse(store.contains(Objective.EQUITY));

            store.save(Objective.EQUITY, network(1));

            assertTrue(store.contains(Objective.EQUITY));
            assertFalse(store.contains(Objective.MAXIMIZE_LIVES));
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void loadingWithoutANetworkGivesNothing() throws IOException {
        Path folder = newFolder();
        try {
            assertEquals(Optional.empty(), new ModelStore(folder).load(Objective.EQUITY));
            assertEquals(Optional.empty(), new ModelStore(folder.resolve("no-existe")).load(Objective.EQUITY));
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void aFileThatIsNotANetworkIsRejected() throws IOException {
        Path folder = newFolder();
        try {
            ModelStore store = new ModelStore(folder);
            Files.writeString(store.fileFor(Objective.EQUITY), "esto no es una red");

            assertThrows(IllegalArgumentException.class, () -> store.load(Objective.EQUITY));
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void reportsAFolderThatCannotBeCreated() throws IOException {
        Path folder = newFolder();
        try {
            Path occupied = Files.writeString(folder.resolve("archivo"), "x");
            ModelStore store = new ModelStore(occupied.resolve("modelos"));

            assertThrows(UncheckedIOException.class, () -> store.save(Objective.EQUITY, network(1)));
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        Path folder = newFolder();
        try {
            ModelStore store = new ModelStore(folder);

            assertThrows(IllegalArgumentException.class, () -> new ModelStore(null));
            assertThrows(IllegalArgumentException.class, () -> store.fileFor(null));
            assertThrows(IllegalArgumentException.class, () -> store.contains(null));
            assertThrows(IllegalArgumentException.class, () -> store.load(null));
            assertThrows(IllegalArgumentException.class, () -> store.save(null, network(1)));
            assertThrows(IllegalArgumentException.class, () -> store.save(Objective.EQUITY, null));
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void theDefaultFolderIsCalledModels() {
        assertEquals(Path.of("models"), ModelStore.DEFAULT_DIRECTORY);
    }
}