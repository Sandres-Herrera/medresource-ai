package com.medresource.services.simulation;

import com.medresource.model.ComparisonReport;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Metric;
import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationCase;
import com.medresource.model.Statistic;
import com.medresource.model.StrategySummary;
import com.medresource.services.strategy.AllocationStrategy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compara estrategias de asignación ejecutándolas todas sobre los mismos casos y el mismo hospital.
 *
 * <p>Cada ejecución usa un hospital nuevo con los recursos configurados. La primera estrategia de la
 * lista es la de referencia: de las demás se calcula, caso por caso, cuánto se diferencian de ella en
 * cada métrica. Con pocos casos las diferencias pueden ser ruido; el resumen incluye lo necesario
 * ({@link Statistic#confidenceHalfWidth()}) para saber si una diferencia es real.</p>
 */
public class StrategyComparator {

    private final HospitalConfig hospital;
    private final HospitalFactory hospitalFactory = new HospitalFactory();
    private final MetricsCalculator calculator = new MetricsCalculator();

    /**
     * Crea el comparador.
     *
     * @param hospital recursos del hospital con los que se ejecuta cada caso
     * @throws IllegalArgumentException si el hospital es nulo
     */
    public StrategyComparator(HospitalConfig hospital) {
        if (hospital == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        this.hospital = hospital;
    }

    /**
     * Ejecuta cada estrategia sobre cada caso y resume los resultados.
     *
     * @param strategies estrategias a comparar; la primera es la de referencia
     * @param cases      casos sobre los que se ejecuta cada estrategia
     * @return informe con un resumen por estrategia, en el mismo orden
     * @throws IllegalArgumentException si falta alguna lista, si alguna está vacía, si hay elementos nulos
     *                                  o si dos estrategias tienen el mismo nombre
     */
    public ComparisonReport compare(List<AllocationStrategy> strategies, List<SimulationCase> cases) {
        requireStrategies(strategies);
        if (cases == null || cases.isEmpty() || cases.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Se necesita al menos un caso, sin elementos nulos.");
        }
        List<List<RunMetrics>> runs = new ArrayList<>();
        for (AllocationStrategy strategy : strategies) {
            runs.add(cases.stream().map(simulationCase -> run(strategy, simulationCase)).toList());
        }
        List<StrategySummary> summaries = new ArrayList<>();
        for (int i = 0; i < strategies.size(); i++) {
            summaries.add(summarize(strategies.get(i).getName(), runs.get(i), runs.get(0)));
        }
        return new ComparisonReport(hospital, cases.size(), summaries);
    }

    private static void requireStrategies(List<AllocationStrategy> strategies) {
        if (strategies == null || strategies.isEmpty() || strategies.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Se necesita al menos una estrategia, sin elementos nulos.");
        }
        Set<String> names = new HashSet<>();
        for (AllocationStrategy strategy : strategies) {
            if (!names.add(strategy.getName())) {
                throw new IllegalArgumentException("Dos estrategias se llaman '" + strategy.getName() + "'.");
            }
        }
    }

    private RunMetrics run(AllocationStrategy strategy, SimulationCase simulationCase) {
        SimulationEngine engine = new SimulationEngine(simulationCase, hospitalFactory.create(hospital), strategy);
        return calculator.calculate(engine.run());
    }

    private static StrategySummary summarize(String name, List<RunMetrics> runs, List<RunMetrics> baseline) {
        Map<Metric, Statistic> values = new EnumMap<>(Metric.class);
        Map<Metric, Statistic> differences = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            double[] own = runs.stream().mapToDouble(metric::of).toArray();
            double[] reference = baseline.stream().mapToDouble(metric::of).toArray();
            double[] gap = new double[own.length];
            for (int i = 0; i < own.length; i++) {
                gap[i] = own[i] - reference[i];
            }
            values.put(metric, Statistic.of(own));
            differences.put(metric, Statistic.of(gap));
        }
        return new StrategySummary(name, runs.size(), values, differences);
    }
}