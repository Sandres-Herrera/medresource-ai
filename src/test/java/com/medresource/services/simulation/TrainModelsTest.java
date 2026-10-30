package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;
import com.medresource.model.Objective;
import com.medresource.services.strategy.ModelStore;
import com.medresource.services.strategy.TrainedModel;
import com.medresource.services.strategy.TrainingSettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del programa que entrena y guarda las redes.
 */
class TrainModelsTest {

    private static final TrainingSettings TINY = new TrainingSettings(1, 1, 10, 2, 0.01, 4, 3, 7);

    private static void deleteAll(Path folder) throws IOException {
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void savesOneNetworkPerObjectiveAndSummarizesThem() throws IOException {
        Path folder = Files.createTempDirectory("modelos-");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

            TrainModels.run(folder, TINY, out);

            String summary = buffer.toString(StandardCharsets.UTF_8);
            ModelStore store = new ModelStore(folder);
            for (Objective objective : Objective.values()) {
                assertTrue(store.contains(objective));
                assertTrue(summary.contains(objective.name() + ": "), summary);
            }
            assertTrue(summary.contains(folder.toAbsolutePath().toString()), summary);
            assertEquals(Objective.values().length + 2, summary.lines().count());
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void summarizesTheErrorsOfEachNetwork() throws IOException {
        Path folder = Files.createTempDirectory("modelos-");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            TrainModels.run(folder, TINY, new PrintStream(buffer, true, StandardCharsets.UTF_8));

            TrainedModel model = new ModelTrainingService(HospitalConfig.standard(), TINY).train(Objective.EQUITY);
            String expected = String.format(Locale.ROOT,
                    "EQUITY: %d ejemplos, error de entrenamiento %.4f -> %.4f, error de evaluación %.4f",
                    model.report().examples(), model.report().firstLoss(), model.trainingLoss(), model.evaluationLoss());
            assertTrue(buffer.toString(StandardCharsets.UTF_8).contains(expected), expected);
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void rejectsMissingArguments() {
        PrintStream out = new PrintStream(new ByteArrayOutputStream());

        assertThrows(IllegalArgumentException.class, () -> TrainModels.run(null, TINY, out));
        assertThrows(IllegalArgumentException.class, () -> TrainModels.run(Path.of("x"), null, out));
        assertThrows(IllegalArgumentException.class, () -> TrainModels.run(Path.of("x"), TINY, null));
    }
}