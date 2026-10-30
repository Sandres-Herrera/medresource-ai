package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.ComparisonReport;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Metric;
import com.medresource.model.Objective;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.Statistic;
import com.medresource.model.StrategySummary;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.ModelStore;
import com.medresource.services.strategy.OptimizationStrategy;
import com.medresource.services.strategy.RandomStrategy;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Programa que compara las estrategias en los cuatro escenarios y en tres tamaños de hospital, y escribe
 * las tablas con la mortalidad, las esperas y los años de vida, con su intervalo de confianza.
 *
 * <p>La referencia es la prioridad clínica. Cada fila dice si la estrategia es mejor, peor o no se
 * distingue de la referencia en la mortalidad, con confianza del 95 %. Las semillas de los casos
 * empiezan en {@value #FIRST_SEED}, que no se usan para entrenar las redes. Argumentos opcionales: la
 * cantidad de casos por escenario (por defecto {@value #DEFAULT_CASES}) y la carpeta con las redes
 * entrenadas (por defecto {@code models}).</p>
 */
public final class CompareStrategies {

    /** Primera semilla de los casos de la comparación. */
    public static final int FIRST_SEED = 5_000_000;

    /** Casos por escenario cuando no se indica otra cantidad. */
    public static final int DEFAULT_CASES = 100;

    /** Un tamaño de hospital con su nombre. */
    private record Size(String name, HospitalConfig config) {
    }

    private static final List<Size> SIZES = List.of(
            new Size("pequeño", new HospitalConfig(15, 3, 2, 8, 12, 5, 3)),
            new Size("estándar", HospitalConfig.standard()),
            new Size("grande", new HospitalConfig(60, 12, 8, 32, 48, 20, 10)));

    private static final String ROW_FORMAT = "%-58s %-15s %-9s %-12s %-12s %-12s %s";

    private CompareStrategies() {
    }

    /**
     * Compara las estrategias y escribe las tablas en la salida estándar.
     *
     * @param args opcionales: casos por escenario y carpeta de las redes
     */
    public static void main(String[] args) {
        int cases = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_CASES;
        Path directory = args.length > 1 ? Path.of(args[1]) : ModelStore.DEFAULT_DIRECTORY;
        run(directory, cases, System.out);
    }

    /**
     * Compara las estrategias en cada tamaño de hospital y escenario, y escribe una tabla por cada uno.
     *
     * @param modelsDirectory   carpeta con las redes entrenadas; las que falten se avisan y no se comparan
     * @param casesPerScenario  casos de cada escenario
     * @param out               donde se escriben las tablas
     * @throws IllegalArgumentException si falta la carpeta o la salida, o si los casos son menos de uno
     */
    static void run(Path modelsDirectory, int casesPerScenario, PrintStream out) {
        if (modelsDirectory == null || out == null) {
            throw new IllegalArgumentException("La carpeta de las redes y la salida son obligatorias.");
        }
        if (casesPerScenario < 1) {
            throw new IllegalArgumentException("Debe haber al menos un caso por escenario.");
        }
        List<AllocationStrategy> strategies = strategies(new ModelStore(modelsDirectory), out);
        for (Scenario scenario : Scenario.values()) {
            List<SimulationCase> cases = casesOf(scenario, casesPerScenario);
            for (Size size : SIZES) {
                ComparisonReport report = new StrategyComparator(size.config()).compare(strategies, cases);
                print(report, size, scenario, out);
            }
        }
    }

    /** Genera los casos de un escenario, con semillas consecutivas desde {@link #FIRST_SEED}. */
    static List<SimulationCase> casesOf(Scenario scenario, int count) {
        CaseGenerator generator = new CaseGenerator();
        List<SimulationCase> cases = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cases.add(generator.generate(CaseConfig.of(FIRST_SEED + i, scenario)));
        }
        return cases;
    }

    private static List<AllocationStrategy> strategies(ModelStore store, PrintStream out) {
        List<AllocationStrategy> strategies = new ArrayList<>();
        strategies.add(new ClinicalPriorityStrategy());
        strategies.add(new FifoStrategy());
        strategies.add(new RandomStrategy(1));
        List<String> missing = new ArrayList<>();
        for (Objective objective : Objective.values()) {
            if (store.contains(objective)) {
                strategies.add(new OptimizationStrategy(store.load(objective).orElseThrow(), objective));
            } else {
                missing.add(objective.name());
            }
        }
        if (!missing.isEmpty()) {
            out.println("Sin red guardada para: " + String.join(", ", missing) + " (ejecute TrainModels).");
        }
        return strategies;
    }

    private static void print(ComparisonReport report, Size size, Scenario scenario, PrintStream out) {
        HospitalConfig hospital = report.hospital();
        out.println();
        out.println(String.format(Locale.ROOT, "== Hospital %s (%d camas, %d UCI, %d quirófanos, %d médicos, %d enfermeros)"
                        + " | %s | %d casos ==", size.name(), hospital.hospitalizationBeds(), hospital.icuBeds(),
                hospital.operatingRooms(), hospital.doctors(), hospital.nurses(), scenario.getDisplayName(),
                report.cases()));
        out.println(String.format(Locale.ROOT, ROW_FORMAT, "Estrategia", "Mortalidad (%)", "Evitables",
                "Espera (min)", "Urgentes", "Años de vida", "Frente a la referencia (mortalidad)"));
        for (StrategySummary summary : report.summaries()) {
            out.println(row(summary, summary == report.baseline()));
        }
    }

    /** Escribe la fila de una estrategia: su mortalidad con el intervalo, sus promedios y el veredicto. */
    static String row(StrategySummary summary, boolean baseline) {
        Statistic mortality = summary.values().get(Metric.MORTALITY);
        String verdict = baseline ? "referencia" : verdict(summary);
        return String.format(Locale.ROOT, ROW_FORMAT, summary.strategyName(),
                String.format(Locale.ROOT, "%.1f +/- %.1f", 100 * mortality.mean(), 100 * mortality.confidenceHalfWidth()),
                String.format(Locale.ROOT, "%.1f", mean(summary, Metric.AVOIDABLE_DEATHS)),
                String.format(Locale.ROOT, "%.0f", mean(summary, Metric.AVERAGE_WAIT)),
                String.format(Locale.ROOT, "%.0f", mean(summary, Metric.URGENT_WAIT)),
                String.format(Locale.ROOT, "%.0f", mean(summary, Metric.LIFE_YEARS_SAVED)), verdict);
    }

    private static double mean(StrategySummary summary, Metric metric) {
        return summary.values().get(metric).mean();
    }

    /** Dice si la estrategia es mejor, peor o no se distingue de la referencia en la mortalidad. */
    static String verdict(StrategySummary summary) {
        Statistic difference = summary.differences().get(Metric.MORTALITY);
        double points100 = Math.round(1000 * difference.mean()) / 10.0;
        String points = String.format(Locale.ROOT, "%+.1f puntos", points100);
        if (summary.isBetterThanBaseline(Metric.MORTALITY)) {
            return "mejor (" + points + ")";
        }
        if (summary.isWorseThanBaseline(Metric.MORTALITY)) {
            return "peor (" + points + ")";
        }
        return "sin diferencia clara (" + points + ")";
    }
}