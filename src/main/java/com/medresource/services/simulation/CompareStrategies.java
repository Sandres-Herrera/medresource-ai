package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.ComparisonReport;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Metric;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.Statistic;
import com.medresource.model.StrategySummary;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import com.medresource.services.strategy.RandomStrategy;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Programa que compara las estrategias en los cuatro escenarios y en tres tamaños de hospital, y escribe
 * las tablas con la mortalidad, las esperas y los años de vida, con su intervalo de confianza.
 *
 * <p>La referencia es la prioridad clínica. Cada fila dice si la estrategia es mejor, peor o no se
 * distingue de la referencia en la mortalidad, con confianza del 95 %. Las semillas de los casos
 * empiezan en {@value #FIRST_SEED}. Argumento opcional: la cantidad de casos por escenario (por defecto
 * {@value #DEFAULT_CASES}).</p>
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
            new Size("pequeño", HospitalConfig.small()),
            new Size("estándar", HospitalConfig.standard()),
            new Size("grande", HospitalConfig.large()));

    private static final String ROW_FORMAT = "%-58s %-15s %-9s %-12s %-12s %-12s %s";

    private CompareStrategies() {
    }

    /**
     * Compara las estrategias y escribe las tablas en la salida estándar.
     *
     * @param args opcional: casos por escenario
     */
    public static void main(String[] args) {
        int cases = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_CASES;
        run(cases, System.out);
    }

    /**
     * Compara las estrategias en cada tamaño de hospital y escenario, y escribe una tabla por cada uno.
     *
     * @param casesPerScenario casos de cada escenario
     * @param out              donde se escriben las tablas
     * @throws IllegalArgumentException si falta la salida o si los casos son menos de uno
     */
    static void run(int casesPerScenario, PrintStream out) {
        if (out == null) {
            throw new IllegalArgumentException("La salida es obligatoria.");
        }
        if (casesPerScenario < 1) {
            throw new IllegalArgumentException("Debe haber al menos un caso por escenario.");
        }
        List<AllocationStrategy> strategies = strategies();
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

    /** Estrategias que se comparan; la primera es la referencia. */
    private static List<AllocationStrategy> strategies() {
        return List.of(new ClinicalPriorityStrategy(), new FifoStrategy(), new RandomStrategy(1));
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