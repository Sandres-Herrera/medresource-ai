package com.medresource.services.management;

import com.medresource.model.ComparisonType;
import com.medresource.model.HospitalConfig;
import com.medresource.model.IndicatorComparison;
import com.medresource.model.RunComparison;
import com.medresource.model.RunIndicators;
import com.medresource.model.RunRecord;
import com.medresource.model.SettingChange;
import com.medresource.model.Trend;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compara dos ejecuciones guardadas del mismo caso.
 *
 * <p>Reglas: las dos ejecuciones deben existir, ser distintas, ser del mismo caso (los mismos pacientes) y no
 * tener exactamente la misma configuración, porque entonces darían los mismos resultados. El tipo de comparación
 * se decide con lo que cambió de verdad entre ellas (véase {@link ComparisonType}).</p>
 *
 * <p>La conclusión solo lee los números: si la segunda ejecución mejoró en todo, lo dice; si mejoró en unos
 * indicadores y empeoró en otros, no declara un ganador y deja claro el balance. Ver la comparación no guarda
 * nada.</p>
 */
public class RunComparator {

    /** Nombre del indicador de pacientes que empezaron a recibir atención. */
    public static final String ATTENDED = "Pacientes atendidos";

    /** Nombre del indicador de fallecidos. */
    public static final String DEATHS = "Fallecidos";

    /** Nombre del indicador de mortalidad. */
    public static final String MORTALITY = "Mortalidad";

    /** Nombre del indicador de pacientes sin atención. */
    public static final String UNATTENDED = "Sin atención";

    /** Nombre del indicador de espera promedio. */
    public static final String AVERAGE_WAIT = "Espera promedio";

    /** Nombre del indicador de espera más larga. */
    public static final String MAX_WAIT = "Espera máxima";

    private final SimulationService runs;

    /**
     * Crea el comparador.
     *
     * @param runs acceso a las ejecuciones guardadas
     * @throws IllegalArgumentException si es nulo
     */
    public RunComparator(SimulationService runs) {
        if (runs == null) {
            throw new IllegalArgumentException("El servicio de ejecuciones es obligatorio.");
        }
        this.runs = runs;
    }

    /**
     * Compara dos ejecuciones guardadas.
     *
     * @param runA ejecución de referencia, normalmente la original
     * @param runB ejecución que se compara con ella
     * @return la comparación
     * @throws IllegalArgumentException si son la misma ejecución, si alguna no existe, si son de casos distintos o
     *                                  si tienen la misma estrategia y los mismos recursos; el mensaje explica cuál
     *                                  regla no se cumple
     */
    public RunComparison compare(int runA, int runB) {
        if (runA == runB) {
            throw new IllegalArgumentException("Elige dos ejecuciones distintas: una ejecución no se compara "
                    + "consigo misma.");
        }
        RunRecord first = find(runA);
        RunRecord second = find(runB);
        if (first.caseId() != second.caseId()) {
            throw new IllegalArgumentException("Las ejecuciones #" + runA + " y #" + runB + " son de casos distintos (#"
                    + first.caseId() + " y #" + second.caseId() + "): solo se comparan ejecuciones del mismo caso, "
                    + "con los mismos pacientes.");
        }
        String strategyA = first.result().strategyName();
        String strategyB = second.result().strategyName();
        boolean strategyChanged = !strategyA.equals(strategyB);
        boolean resourcesChanged = !first.hospital().equals(second.hospital());
        ComparisonType type = ComparisonType.of(strategyChanged, resourcesChanged);
        List<SettingChange> changes = new ArrayList<>();
        if (strategyChanged) {
            changes.add(new SettingChange("Estrategia", strategyA, strategyB));
        }
        changes.addAll(hospitalChanges(first.hospital(), second.hospital()));
        List<IndicatorComparison> indicators = indicators(RunIndicators.of(first.result()),
                RunIndicators.of(second.result()));
        return new RunComparison(first.caseId(), runA, runB, strategyA, strategyB, type, changes, indicators,
                conclusion(runA, runB, indicators), limitations(type, first));
    }

    private RunRecord find(int id) {
        return runs.find(id).orElseThrow(() -> new IllegalArgumentException("No existe la ejecución #" + id + "."));
    }

    private static List<SettingChange> hospitalChanges(HospitalConfig a, HospitalConfig b) {
        List<SettingChange> changes = new ArrayList<>();
        addIfChanged(changes, "Camas", a.hospitalizationBeds(), b.hospitalizationBeds());
        addIfChanged(changes, "Camas UCI", a.icuBeds(), b.icuBeds());
        addIfChanged(changes, "Quirófanos", a.operatingRooms(), b.operatingRooms());
        addIfChanged(changes, "Médicos", a.doctors(), b.doctors());
        addIfChanged(changes, "Enfermeros", a.nurses(), b.nurses());
        addIfChanged(changes, "Sangre por tipo", a.bloodUnitsPerType(), b.bloodUnitsPerType());
        addIfChanged(changes, "Cupos de la morgue", a.morgueCapacity(), b.morgueCapacity());
        if (!a.extraDoctors().equals(b.extraDoctors())) {
            changes.add(new SettingChange("Médicos extra", a.describeExtraDoctors(), b.describeExtraDoctors()));
        }
        return changes;
    }

    private static void addIfChanged(List<SettingChange> changes, String setting, int before, int after) {
        if (before != after) {
            changes.add(new SettingChange(setting, String.valueOf(before), String.valueOf(after)));
        }
    }

    private static List<IndicatorComparison> indicators(RunIndicators a, RunIndicators b) {
        return List.of(
                new IndicatorComparison(ATTENDED, "", a.attended(), b.attended(), false),
                new IndicatorComparison(DEATHS, "", a.deaths(), b.deaths(), true),
                new IndicatorComparison(MORTALITY, "%", a.mortalityPercent(), b.mortalityPercent(), true),
                new IndicatorComparison(UNATTENDED, "", a.unattended(), b.unattended(), true),
                new IndicatorComparison(AVERAGE_WAIT, "min", a.averageWait(), b.averageWait(), true),
                new IndicatorComparison(MAX_WAIT, "min", a.maxWait(), b.maxWait(), true));
    }

    private static String conclusion(int runA, int runB, List<IndicatorComparison> indicators) {
        List<String> better = describe(indicators, Trend.IMPROVED);
        List<String> worse = describe(indicators, Trend.WORSENED);
        String second = "La ejecución #" + runB;
        String first = "la #" + runA;
        if (better.isEmpty() && worse.isEmpty()) {
            return "Las ejecuciones #" + runA + " y #" + runB + " dieron los mismos resultados en todos los "
                    + "indicadores.";
        }
        if (worse.isEmpty()) {
            return second + " mejoró frente a " + first + " sin empeorar ningún indicador: "
                    + String.join(", ", better) + ".";
        }
        if (better.isEmpty()) {
            return second + " empeoró frente a " + first + " sin mejorar ningún indicador: "
                    + String.join(", ", worse) + ".";
        }
        return "No hay un ganador claro: " + second.toLowerCase(Locale.ROOT) + " mejoró en "
                + String.join(", ", better) + ", pero empeoró en " + String.join(", ", worse)
                + ". Hay que decidir qué indicador pesa más en este escenario.";
    }

    private static List<String> describe(List<IndicatorComparison> indicators, Trend trend) {
        return indicators.stream().filter(indicator -> indicator.trend() == trend)
                .map(indicator -> indicator.name().toLowerCase(Locale.ROOT) + " (" + signed(indicator) + ")")
                .toList();
    }

    /** Diferencia con su signo y su unidad, por ejemplo «−4», «+12» o «−13,5 min». */
    static String signed(IndicatorComparison indicator) {
        double difference = indicator.difference();
        String sign = difference < 0 ? "−" : "+";
        String unit = switch (indicator.unit()) {
            case "" -> "";
            case "%" -> " puntos";
            default -> " " + indicator.unit();
        };
        return sign + number(Math.abs(difference)) + unit;
    }

    /** Un número sin decimales si es entero, o con uno y coma decimal. */
    static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded).replace('.', ',');
    }

    private static List<String> limitations(ComparisonType type, RunRecord run) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Se compara un solo caso (semilla " + run.result().config().seed() + ", "
                + run.result().config().scenario().getDisplayName() + "): con otros pacientes la diferencia puede "
                + "ser otra. Las recomendaciones del reporte salen del promedio de varios casos.");
        if (type == ComparisonType.MIXED) {
            limitations.add(type.getDescription());
        }
        limitations.add("Es una simulación con supuestos (sin turnos ni diagnósticos erróneos): no reemplaza las "
                + "decisiones médicas reales.");
        return limitations;
    }
}
