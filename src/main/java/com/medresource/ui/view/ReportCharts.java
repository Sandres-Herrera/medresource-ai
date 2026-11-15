package com.medresource.ui.view;

import com.medresource.model.BloodUsage;
import com.medresource.model.BottleneckStats;
import com.medresource.model.DeathBreakdown;
import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportSection;
import com.medresource.model.ResourceUsage;
import com.medresource.model.SimulationReport;
import com.medresource.model.SpecialtyLoad;
import com.medresource.model.StaffLoad;
import com.medresource.model.TriageLevelStats;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;

import javax.swing.JComponent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Arma la gráfica de cada sección del reporte a partir de los números ya calculados.
 *
 * <p>No calcula nada nuevo: solo elige qué número va en cada barra, cómo se escribe y de qué color va. Una sección
 * sin datos que mostrar (por ejemplo, sin cuellos de botella) no lleva gráfica.</p>
 */
public final class ReportCharts {

    /** Lo que se agrega al nombre de la estrategia que usa hoy el hospital en la gráfica de la política. */
    public static final String CURRENT = " · actual";

    private ReportCharts() {
    }

    /**
     * Arma la gráfica de una sección.
     *
     * @param section   sección del reporte
     * @param generated reporte terminado
     * @return la gráfica; vacío si la sección no lleva gráfica o si no hay datos que mostrar
     * @throws IllegalArgumentException si falta la sección o el reporte
     */
    public static Optional<JComponent> chartFor(ReportSection section, GeneratedReport generated) {
        if (section == null || generated == null) {
            throw new IllegalArgumentException("La sección y el reporte son obligatorios.");
        }
        SimulationReport report = generated.report();
        WhatIfReport whatIf = generated.whatIf();
        return switch (section) {
            case TRIAGE -> triage(report).map(JComponent.class::cast);
            case DEATHS -> deaths(report.deaths()).map(JComponent.class::cast);
            case BOTTLENECKS -> bottlenecks(report.bottlenecks()).map(JComponent.class::cast);
            case TIMELINE -> Optional.of(new TimelineStrip(Math.max(1, report.totalMinutes()),
                    report.config().durationMinutes(), report.collapses()));
            case RESOURCES -> resources(report.resources()).map(JComponent.class::cast);
            case STAFF -> staff(report.specialties()).map(JComponent.class::cast);
            case BLOOD -> blood(report.blood().types()).map(JComponent.class::cast);
            case MISSING_RESOURCES -> missingResources(whatIf).map(JComponent.class::cast);
            case POLICY -> policy(whatIf).map(JComponent.class::cast);
            case CASES -> Optional.empty();
        };
    }

    /** Espera promedio de cada nivel de triage que tuvo pacientes, con la parte atendida dentro de la meta. */
    static Optional<BarChart> triage(SimulationReport report) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (TriageLevelStats level : report.triage()) {
            if (level.patients() > 0) {
                bars.add(new BarChart.Bar(level.level().getDisplayName(), level.averageWaitMinutes(),
                        ReportFormat.decimal(level.averageWaitMinutes()) + " min · "
                                + ReportFormat.percent(level.withinTargetRate()) + " en meta",
                        HomeTheme.triageColor(level.level())));
            }
        }
        return chart(bars, 0);
    }

    /** Fallecidos según cuándo murieron. */
    static Optional<BarChart> deaths(DeathBreakdown deaths) {
        if (deaths.total() == 0) {
            return Optional.empty();
        }
        return chart(List.of(
                new BarChart.Bar("Murieron esperando", deaths.whileWaiting(), patients(deaths.whileWaiting()),
                        HomeTheme.RED),
                new BarChart.Bar("Tras una atención tardía", deaths.afterLateCare(),
                        patients(deaths.afterLateCare()), HomeTheme.ORANGE),
                new BarChart.Bar("Aunque se atendieron a tiempo", deaths.despiteTimelyCare(),
                        patients(deaths.despiteTimelyCare()), HomeTheme.MUTED)), 0);
    }

    /** Recursos que hicieron esperar, del que más espera causó al que menos; el primero en rojo. */
    static Optional<BarChart> bottlenecks(List<BottleneckStats> bottlenecks) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (BottleneckStats bottleneck : bottlenecks) {
            bars.add(new BarChart.Bar(bottleneck.resource().getDisplayName(), bottleneck.waitingMinutes(),
                    ReportFormat.percent(bottleneck.share()) + " de la espera · "
                            + patients(bottleneck.patientsAffected()),
                    bars.isEmpty() ? HomeTheme.RED : HomeTheme.AMBER));
        }
        return chart(bars, 0);
    }

    /**
     * Ocupación promedio de cada grupo de recursos: rojo si estuvo casi siempre ocupado, gris si casi no se usó.
     * El texto dice cuántos minutos estuvo lleno o, si nunca se llenó, su pico.
     */
    static Optional<BarChart> resources(List<ResourceUsage> resources) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (ResourceUsage usage : resources) {
            String detail = usage.wasFull() ? "lleno " + usage.fullMinutes() + " min"
                    : "pico " + ReportFormat.percent(usage.peakOccupancy());
            bars.add(new BarChart.Bar(usage.group().getDisplayName(), usage.averageOccupancy(),
                    ReportFormat.percent(usage.averageOccupancy()) + " prom. · " + detail,
                    loadColor(100 * usage.averageOccupancy(), ResourceUsage.UNDERUSED_PERCENT)));
        }
        return chart(bars, 1.0);
    }

    /** Ocupación promedio de los médicos de cada especialidad: rojo si están saturados, gris si casi libres. */
    static Optional<BarChart> staff(List<SpecialtyLoad> specialties) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (SpecialtyLoad load : specialties) {
            bars.add(new BarChart.Bar(load.specialty().getDisplayName() + " (" + load.doctors() + ")",
                    load.averageOccupancy(), ReportFormat.percent(load.averageOccupancy()) + " · "
                    + patients(load.patients()), loadColor(100 * load.averageOccupancy(), StaffLoad.IDLE_PERCENT)));
        }
        return chart(bars, 1.0);
    }

    /** Unidades usadas de cada tipo de sangre que se usó; rojo si se acabó. Sin transfusiones no hay gráfica. */
    static Optional<BarChart> blood(List<BloodUsage> types) {
        List<BarChart.Bar> bars = new ArrayList<>();
        int most = 0;
        for (BloodUsage type : types) {
            if (type.usedUnits() > 0) {
                most = Math.max(most, type.initialUnits());
                bars.add(new BarChart.Bar(type.type().getLabel(), type.usedUnits(),
                        type.usedUnits() + " de " + type.initialUnits() + " usadas",
                        type.ranOut() ? HomeTheme.RED : HomeTheme.ACCENT));
            }
        }
        return chart(bars, most);
    }

    /** Muertes que evitaría cada recurso que faltó, por cada 100 pacientes. */
    static Optional<BarChart> missingResources(WhatIfReport whatIf) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (WhatIfResult result : whatIf.missingResources()) {
            bars.add(new BarChart.Bar(result.label(), Math.max(0, result.deathsAvoidedPer100()),
                    "evita " + deaths(result.deathsAvoidedPer100()) + " por cada 100", HomeTheme.GREEN));
        }
        return chart(bars, 0);
    }

    /** Mortalidad del hospital actual y con cada otra estrategia: verde si la baja, rojo si la sube. */
    static Optional<BarChart> policy(WhatIfReport whatIf) {
        if (whatIf.policies().isEmpty()) {
            return Optional.empty();
        }
        List<BarChart.Bar> bars = new ArrayList<>();
        bars.add(new BarChart.Bar(whatIf.strategyName() + CURRENT, whatIf.mortality().mean(),
                ReportFormat.percent(whatIf.mortality().mean()) + " de mortalidad", HomeTheme.ACCENT));
        for (WhatIfResult policy : whatIf.policies()) {
            Color color = policy.lowersMortality() ? HomeTheme.GREEN
                    : policy.raisesMortality() ? HomeTheme.RED : HomeTheme.MUTED;
            bars.add(new BarChart.Bar(policy.strategyName(), policy.mortality().mean(),
                    ReportFormat.percent(policy.mortality().mean()) + " de mortalidad", color));
        }
        return chart(bars, 0);
    }

    /**
     * Escribe una cantidad de muertes con su palabra en singular o plural, por ejemplo «1 muerte» o «0,8 muertes».
     *
     * @param count cantidad, puede tener decimales
     * @return la cantidad con su palabra
     */
    static String deaths(double count) {
        String number = ReportFormat.decimal(count);
        return number + ("1".equals(number) ? " muerte" : " muertes");
    }

    /** Rojo desde el umbral de saturación, gris por debajo del umbral de poco uso y acento en medio. */
    private static Color loadColor(double percent, int lowPercent) {
        return percent >= StaffLoad.SATURATED_PERCENT ? HomeTheme.RED
                : percent < lowPercent ? HomeTheme.MUTED : HomeTheme.ACCENT;
    }

    private static Optional<BarChart> chart(List<BarChart.Bar> bars, double scale) {
        return bars.isEmpty() ? Optional.empty() : Optional.of(new BarChart(bars, scale));
    }

    private static String patients(int count) {
        return count + (count == 1 ? " paciente" : " pacientes");
    }
}
