package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.Metric;
import com.medresource.model.Objective;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.Statistic;
import com.medresource.model.StrategySummary;
import com.medresource.services.strategy.FeatureExtractor;
import com.medresource.services.strategy.ModelStore;
import com.medresource.services.strategy.NeuralNetwork;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del programa que compara las estrategias.
 */
class CompareStrategiesTest {

    private static void deleteAll(Path folder) throws IOException {
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private static String run(Path folder, int cases) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CompareStrategies.run(folder, cases, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static StrategySummary summaryWithMortalityDifference(Statistic difference) {
        Map<Metric, Statistic> values = new EnumMap<>(Metric.class);
        Map<Metric, Statistic> differences = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            values.put(metric, Statistic.of(1.0, 2.0));
            differences.put(metric, Statistic.of(0.0));
        }
        differences.put(Metric.MORTALITY, difference);
        return new StrategySummary("IA", 100, values, differences);
    }

    @Test
    void writesOneTablePerHospitalSizeAndScenario() throws IOException {
        Path folder = Files.createTempDirectory("modelos-");
        try {
            String output = run(folder, 1);

            assertEquals(12, output.lines().filter(line -> line.startsWith("== Hospital ")).count());
            assertTrue(output.contains("== Hospital pequeño (15 camas, 3 UCI, 2 quirófanos, 8 médicos, 12 enfermeros)"
                    + " | Hospital normal | 1 casos =="), output);
            assertTrue(output.contains("== Hospital estándar (30 camas"), output);
            assertTrue(output.contains("== Hospital grande (60 camas"), output);
            assertTrue(output.contains("Catástrofe"), output);
            assertEquals(12, output.lines().filter(line -> line.endsWith("referencia")).count());
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void comparesTheThreeSimpleStrategiesWhenThereAreNoNetworks() throws IOException {
        Path folder = Files.createTempDirectory("modelos-");
        try {
            String output = run(folder, 1);

            assertTrue(output.contains("Sin red guardada para: MAXIMIZE_LIVES, MAXIMIZE_LIFE_YEARS, EQUITY, "
                    + "MINIMIZE_WAIT (ejecute TrainModels)."), output);
            assertEquals(12, output.lines().filter(line -> line.startsWith("Prioridad clínica")).count());
            assertEquals(12, output.lines().filter(line -> line.startsWith("FIFO")).count());
            assertEquals(12, output.lines().filter(line -> line.startsWith("Aleatoria")).count());
            assertFalse(output.contains("Inteligencia artificial"), output);
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void addsTheStrategyOfEachSavedNetwork() throws IOException {
        Path folder = Files.createTempDirectory("modelos-");
        try {
            new ModelStore(folder).save(Objective.MAXIMIZE_LIVES,
                    new NeuralNetwork(1, FeatureExtractor.FEATURE_COUNT, 3, 1));

            String output = run(folder, 1);

            assertEquals(12, output.lines().filter(line -> line.startsWith("Inteligencia artificial "
                    + "(Maximizar vidas salvadas)")).count());
            assertTrue(output.contains("Sin red guardada para: MAXIMIZE_LIFE_YEARS, EQUITY, MINIMIZE_WAIT"), output);
            assertFalse(output.contains("Sin red guardada para: MAXIMIZE_LIVES"), output);
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void withASingleCaseNoDifferenceIsClaimed() throws IOException {
        Path folder = Files.createTempDirectory("modelos-");
        try {
            String output = run(folder, 1);

            assertEquals(24, output.lines().filter(line -> line.contains("sin diferencia clara")).count());
            assertFalse(output.contains("mejor ("), output);
            assertFalse(output.contains("peor ("), output);
        } finally {
            deleteAll(folder);
        }
    }

    @Test
    void doesNotRunWithoutTheFolderTheOutputOrAnyCase() {
        PrintStream out = new PrintStream(new ByteArrayOutputStream());

        assertThrows(IllegalArgumentException.class, () -> CompareStrategies.run(null, 1, out));
        assertThrows(IllegalArgumentException.class, () -> CompareStrategies.run(Path.of("models"), 1, null));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CompareStrategies.run(Path.of("models"), 0, out));
        assertTrue(error.getMessage().contains("al menos un caso por escenario"));
    }

    @Test
    void theCasesOfAScenarioHaveConsecutiveSeedsFromTheFirstOne() {
        List<SimulationCase> cases = CompareStrategies.casesOf(Scenario.CATASTROPHE, 3);

        assertEquals(3, cases.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(new CaseConfig(CompareStrategies.FIRST_SEED + i, Scenario.CATASTROPHE,
                    CaseConfig.DEFAULT_PATIENTS, CaseConfig.DEFAULT_DURATION_MINUTES), cases.get(i).config());
        }
    }

    @Test
    void theRowShowsTheMortalityAsAPercentageWithItsInterval() {
        Map<Metric, Statistic> values = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            values.put(metric, new Statistic(100, 3.0, 1.0, 0.0, 6.0));
        }
        values.put(Metric.MORTALITY, new Statistic(100, 0.2, 0.1, 0.0, 0.5));
        values.put(Metric.AVERAGE_WAIT, new Statistic(100, 45.4, 5.0, 0.0, 90.0));
        values.put(Metric.LIFE_YEARS_SAVED, new Statistic(100, 4949.6, 50.0, 4000.0, 5000.0));
        StrategySummary summary = new StrategySummary("IA", 100, values, values);

        String row = CompareStrategies.row(summary, true);

        assertTrue(row.startsWith("IA "), row);
        assertTrue(row.contains("20.0 +/- 2.0"), row);
        assertTrue(row.contains(" 3.0 "), row);
        assertTrue(row.contains(" 45 "), row);
        assertTrue(row.contains(" 4950 "), row);
        assertTrue(row.endsWith("referencia"), row);
    }

    @Test
    void theVerdictSaysBetterWorseOrUnclearWithTheDifferenceInPoints() {
        Statistic lower = new Statistic(100, -0.0123, 0.01, -0.05, 0.02);
        Statistic higher = new Statistic(100, 0.0456, 0.01, -0.02, 0.09);
        Statistic unclear = new Statistic(100, -0.0004, 0.05, -0.1, 0.1);

        assertEquals("mejor (-1.2 puntos)", CompareStrategies.verdict(summaryWithMortalityDifference(lower)));
        assertEquals("peor (+4.6 puntos)", CompareStrategies.verdict(summaryWithMortalityDifference(higher)));
        assertEquals("sin diferencia clara (+0.0 puntos)",
                CompareStrategies.verdict(summaryWithMortalityDifference(unclear)));
    }
}