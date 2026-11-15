package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.ResourceChange;
import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationCase;
import com.medresource.model.Specialty;
import com.medresource.model.Statistic;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;
import com.medresource.services.strategy.AllocationStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Análisis «¿y si…?»: vuelve a simular el mismo escenario con cada cambio de recursos y con cada otra
 * estrategia de atención, y mide cuánto cambian la mortalidad y la espera frente al hospital tal como está.
 *
 * <p>Una sola corrida puede ser suerte, así que se simulan varios casos del mismo escenario, con la misma
 * cantidad de pacientes y la misma duración: el primero con la semilla de la corrida original y los demás con
 * las semillas siguientes. Todas las alternativas se simulan sobre esos mismos casos, para comparar cada caso
 * consigo mismo ({@link WhatIfResult}).</p>
 *
 * <p>Las simulaciones son independientes, así que se reparten entre los núcleos del procesador; el resultado
 * es el mismo en cualquier orden. El avance se avisa después de cada simulación, desde el hilo que la terminó,
 * y nunca dos avisos a la vez.</p>
 */
public class WhatIfAnalyzer {

    /** Casos que se simulan por alternativa cuando no se indica otra cantidad. */
    public static final int DEFAULT_CASES = 30;

    /** Máximo de casos por alternativa. */
    public static final int MAX_CASES = 100;

    private final CaseGenerator generator = new CaseGenerator();
    private final HospitalFactory factory = new HospitalFactory();
    private final MetricsCalculator metrics = new MetricsCalculator();

    /** Una forma de hacer funcionar el hospital: con qué recursos y con qué estrategia. */
    private record Alternative(String label, ResourceChange change, HospitalConfig config,
                               List<Specialty> extraDoctors, Supplier<AllocationStrategy> strategy,
                               String strategyName) {
    }

    /**
     * Calcula cuántas simulaciones hará un análisis, para avisar cuánto va a tardar antes de empezar.
     *
     * @param hospital      hospital que se analiza
     * @param otherPolicies cantidad de estrategias distintas de la actual que se prueban
     * @param cases         casos por alternativa
     * @return simulaciones en total: (1 + cambios posibles + estrategias) × casos
     * @throws IllegalArgumentException si falta el hospital o si algún número es negativo
     */
    public static int runsNeeded(HospitalConfig hospital, int otherPolicies, int cases) {
        if (hospital == null || otherPolicies < 0 || cases < 0) {
            throw new IllegalArgumentException("El hospital es obligatorio y los números no pueden ser negativos.");
        }
        return (1 + ResourceChange.allFor(hospital).size() + otherPolicies) * cases;
    }

    /**
     * Hace el análisis.
     *
     * @param config        caso de la corrida original (escenario, semilla, pacientes y duración)
     * @param hospital      recursos del hospital tal como está
     * @param current       crea la estrategia con la que funciona hoy el hospital
     * @param otherPolicies crean las otras estrategias que se prueban; se salta la que tenga el mismo nombre que
     *                      la actual
     * @param cases         casos por alternativa, de 2 a {@value #MAX_CASES}
     * @param progress      recibe el avance después de cada simulación
     * @return resultado de cada alternativa y las recomendaciones
     * @throws IllegalArgumentException si falta algún dato o si los casos están fuera de rango
     */
    public WhatIfReport analyze(CaseConfig config, HospitalConfig hospital, Supplier<AllocationStrategy> current,
                                List<Supplier<AllocationStrategy>> otherPolicies, int cases,
                                Consumer<WhatIfProgress> progress) {
        if (config == null || hospital == null || current == null || otherPolicies == null || progress == null) {
            throw new IllegalArgumentException("Faltan datos para el análisis.");
        }
        if (cases < 2 || cases > MAX_CASES) {
            throw new IllegalArgumentException("Los casos deben estar entre 2 y " + MAX_CASES + ".");
        }
        String currentName = current.get().getName();
        List<SimulationCase> simulationCases = casesOf(config, cases);
        List<Alternative> alternatives = new ArrayList<>();
        alternatives.add(new Alternative("Hospital tal como está", null, hospital, List.of(), current, currentName));
        for (ResourceChange change : ResourceChange.allFor(hospital)) {
            alternatives.add(new Alternative(change.describe(), change, change.applyTo(hospital),
                    change.extraDoctors(), current, currentName));
        }
        for (Supplier<AllocationStrategy> policy : otherPolicies) {
            String name = policy.get().getName();
            if (!name.equals(currentName)) {
                alternatives.add(new Alternative("Usar la estrategia " + name, null, hospital, List.of(), policy,
                        name));
            }
        }
        RunMetrics[][] runs = simulateAll(alternatives, simulationCases, progress);
        return reportOf(alternatives, runs, cases, currentName);
    }

    private List<SimulationCase> casesOf(CaseConfig config, int cases) {
        List<SimulationCase> list = new ArrayList<>();
        for (int i = 0; i < cases; i++) {
            list.add(generator.generate(new CaseConfig(config.seed() + i, config.scenario(), config.patientCount(),
                    config.durationMinutes())));
        }
        return list;
    }

    /** Simula cada alternativa sobre cada caso, en paralelo; la fila es la alternativa y la columna, el caso. */
    private RunMetrics[][] simulateAll(List<Alternative> alternatives, List<SimulationCase> cases,
                                       Consumer<WhatIfProgress> progress) {
        int total = alternatives.size() * cases.size();
        RunMetrics[][] runs = new RunMetrics[alternatives.size()][cases.size()];
        long start = System.nanoTime();
        Object lock = new Object();
        int[] done = {0};
        IntStream.range(0, total).parallel().forEach(task -> {
            int row = task / cases.size();
            int column = task % cases.size();
            runs[row][column] = simulate(alternatives.get(row), cases.get(column));
            synchronized (lock) {
                done[0]++;
                progress.accept(new WhatIfProgress(done[0], total, (System.nanoTime() - start) / 1_000_000));
            }
        });
        return runs;
    }

    private RunMetrics simulate(Alternative alternative, SimulationCase simulationCase) {
        SimulationEngine engine = new SimulationEngine(simulationCase,
                factory.create(alternative.config(), alternative.extraDoctors()), alternative.strategy().get());
        return metrics.calculate(engine.run());
    }

    private static WhatIfReport reportOf(List<Alternative> alternatives, RunMetrics[][] runs, int cases,
                                         String currentName) {
        double[] baseMortality = mortalityOf(runs[0]);
        double[] baseWait = waitOf(runs[0]);
        List<WhatIfResult> resources = new ArrayList<>();
        List<WhatIfResult> policies = new ArrayList<>();
        for (int row = 1; row < alternatives.size(); row++) {
            Alternative alternative = alternatives.get(row);
            double[] mortality = mortalityOf(runs[row]);
            double[] wait = waitOf(runs[row]);
            WhatIfResult result = new WhatIfResult(alternative.label(), alternative.change(),
                    alternative.strategyName(), Statistic.of(mortality), Statistic.of(minus(mortality, baseMortality)),
                    Statistic.of(minus(wait, baseWait)));
            (result.isPolicy() ? policies : resources).add(result);
        }
        return new WhatIfReport(cases, currentName, Statistic.of(baseMortality), Statistic.of(baseWait), resources,
                policies);
    }

    private static double[] mortalityOf(RunMetrics[] runs) {
        double[] values = new double[runs.length];
        for (int i = 0; i < runs.length; i++) {
            values[i] = runs[i].mortalityRate();
        }
        return values;
    }

    private static double[] waitOf(RunMetrics[] runs) {
        double[] values = new double[runs.length];
        for (int i = 0; i < runs.length; i++) {
            values[i] = runs[i].averageWaitMinutes();
        }
        return values;
    }

    private static double[] minus(double[] values, double[] base) {
        double[] differences = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            differences[i] = values[i] - base[i];
        }
        return differences;
    }
}
