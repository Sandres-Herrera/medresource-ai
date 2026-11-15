package com.medresource.services.management;

import com.medresource.model.BottleneckStats;
import com.medresource.model.CollapsePeriod;
import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.ResourceUsage;
import com.medresource.model.ReviewCase;
import com.medresource.model.SimulationReport;
import com.medresource.model.SpecialtyLoad;
import com.medresource.model.TriageLevelStats;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Redacta el texto del reporte con plantillas, sin IA: frases fijas con los números del reporte.
 *
 * <p>Se usa cuando no hay clave de la IA, cuando la IA no responde o cuando su respuesta no sirve, y también para
 * completar las secciones que la IA dejó sin texto. El texto es más simple que el de la IA, pero siempre está y
 * nunca tiene cifras inventadas.</p>
 */
public final class TemplateNarrator {

    /** Cuellos de botella, recursos que faltaron y casos que se nombran como máximo. */
    public static final int MAX_ITEMS = 3;

    private static final Locale SPANISH = Locale.of("es", "CO");

    private TemplateNarrator() {
    }

    /**
     * Redacta el texto completo del reporte.
     *
     * @param report reporte con los números
     * @param whatIf análisis «¿y si…?»; {@code null} si no se hizo
     * @param note   por qué se usan las plantillas
     * @return texto con todas las secciones y una explicación por recomendación
     * @throws IllegalArgumentException si falta el reporte o la nota
     */
    public static ReportNarrative write(SimulationReport report, WhatIfReport whatIf, String note) {
        if (report == null || note == null || note.isBlank()) {
            throw new IllegalArgumentException("El reporte y la nota son obligatorios.");
        }
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        sections.put(ReportSection.TRIAGE, triage(report));
        sections.put(ReportSection.DEATHS, deaths(report));
        sections.put(ReportSection.BOTTLENECKS, bottlenecks(report));
        sections.put(ReportSection.TIMELINE, timeline(report));
        sections.put(ReportSection.RESOURCES, resources(report));
        sections.put(ReportSection.STAFF, staff(report));
        sections.put(ReportSection.BLOOD, blood(report));
        sections.put(ReportSection.MISSING_RESOURCES, missingResources(whatIf));
        sections.put(ReportSection.POLICY, policy(report, whatIf));
        sections.put(ReportSection.CASES, cases(report));
        return new ReportNarrative(NarrativeSource.TEMPLATE, summary(report), sections, recommendations(whatIf),
                conclusion(report, whatIf), note);
    }

    /**
     * Redacta la explicación de cada recomendación.
     *
     * @param whatIf análisis «¿y si…?»; {@code null} si no se hizo
     * @return una explicación por recomendación, en su orden; vacío si no hay
     */
    public static List<String> recommendations(WhatIfReport whatIf) {
        List<String> texts = new ArrayList<>();
        if (whatIf != null) {
            whatIf.recommendations().forEach(result -> texts.add(effect(result)));
        }
        return texts;
    }

    private static String summary(SimulationReport report) {
        String text = format("En el escenario %s llegaron %d pacientes hasta las %s. Se atendió a %d y"
                        + " fallecieron %d (%s %%). La espera promedio fue de %s min y la más larga, de %d min.",
                report.config().scenario().getDisplayName(), report.config().patientCount(),
                ReportFacts.clock(report.config().durationMinutes()), report.treated(), report.metrics().deaths(),
                decimal(100 * report.metrics().mortalityRate()), decimal(report.metrics().averageWaitMinutes()),
                report.maxWaitMinutes());
        return report.mainBottleneck()
                .map(main -> text + format(" El principal cuello de botella fue %s, con el %s %% de la espera.",
                        lowerFirst(main.resource().getDisplayName()), decimal(100 * main.share())))
                .orElse(text);
    }

    private static String triage(SimulationReport report) {
        List<String> late = new ArrayList<>();
        for (TriageLevelStats stats : report.triage()) {
            if (stats.patients() > 0 && stats.withinTargetRate() < 1) {
                late.add(format("en %s se atendió dentro de la meta de %d min el %s %%, con una espera promedio de"
                                + " %s min", stats.level().getDisplayName(), stats.level().getTargetMinutes(),
                        decimal(100 * stats.withinTargetRate()), decimal(stats.averageWaitMinutes())));
            }
        }
        return late.isEmpty() ? "Todos los niveles de triage se atendieron dentro de su meta de espera."
                : capitalize(String.join("; ", late)) + ".";
    }

    private static String deaths(SimulationReport report) {
        String failures = format(" %d pacientes esperaron más que la meta de su nivel y %d críticos esperaron sin"
                        + " primeros auxilios a tiempo.", report.failures().overTarget(),
                report.failures().criticalWithoutTimelyAid());
        if (report.deaths().total() == 0) {
            return "No falleció ningún paciente." + failures;
        }
        return format("Fallecieron %d pacientes: %d muertes evitables (%d murieron esperando y %d después de una"
                        + " atención tardía) y %d aunque se les atendió a tiempo.", report.deaths().total(),
                report.deaths().avoidable(), report.deaths().whileWaiting(), report.deaths().afterLateCare(),
                report.deaths().unavoidable()) + failures;
    }

    private static String bottlenecks(SimulationReport report) {
        if (report.bottlenecks().isEmpty()) {
            return "Ningún paciente esperó por falta de recursos.";
        }
        return report.bottlenecks().stream().limit(MAX_ITEMS)
                .map(TemplateNarrator::bottleneck)
                .collect(Collectors.joining(" "));
    }

    private static String bottleneck(BottleneckStats stats) {
        return format("%s causó el %s %% de la espera (%d minutos-paciente) entre las %s y las %s.",
                stats.resource().getDisplayName(), decimal(100 * stats.share()), stats.waitingMinutes(),
                ReportFacts.clock(stats.firstMinute()), ReportFacts.clock(stats.lastMinute()));
    }

    private static String timeline(SimulationReport report) {
        if (report.collapses().isEmpty()) {
            return "El servicio no colapsó: ningún paciente esperó más que la meta de su nivel.";
        }
        return report.collapses().stream().map(TemplateNarrator::collapse).collect(Collectors.joining(" "));
    }

    private static String collapse(CollapsePeriod collapse) {
        return format("El servicio colapsó de %s a %s (%d min); el peor momento fue a las %s, con %d pacientes fuera"
                        + " de su meta.", ReportFacts.clock(collapse.startMinute()),
                ReportFacts.clock(collapse.endMinute()), collapse.durationMinutes(),
                ReportFacts.clock(collapse.peakMinute()), collapse.peakOverTarget());
    }

    private static String resources(SimulationReport report) {
        List<String> full = report.resources().stream().filter(ResourceUsage::wasFull)
                .map(usage -> format("%s (%d min)", lowerFirst(usage.group().getDisplayName()),
                        usage.fullMinutes()))
                .toList();
        List<String> underused = report.underusedResources().stream()
                .map(usage -> format("%s (promedio %s %%)", lowerFirst(usage.group().getDisplayName()),
                        decimal(100 * usage.averageOccupancy())))
                .toList();
        String text = full.isEmpty() ? "Ningún grupo de recursos se llenó por completo."
                : "Se llenaron por completo: " + String.join(", ", full) + ".";
        return underused.isEmpty() ? text : text + " Estuvieron subutilizados: " + String.join(", ", underused) + ".";
    }

    private static String staff(SimulationReport report) {
        String text = format("Personas saturadas: %d; libres la mayor parte del tiempo: %d.",
                report.saturatedStaff().size(), report.idleStaff().size());
        return report.specialties().stream()
                .filter(load -> load.doctorWaitMinutes() > 0)
                .max(Comparator.comparingInt(SpecialtyLoad::doctorWaitMinutes))
                .map(load -> format("La especialidad más esperada fue %s: %d minutos-paciente de espera por un"
                        + " médico. ", load.specialty().getDisplayName(), load.doctorWaitMinutes()) + text)
                .orElse(text);
    }

    private static String blood(SimulationReport report) {
        String text = format("Se hicieron %d transfusiones con %d unidades; %d usaron O negativo.",
                report.blood().transfusions(), report.blood().unitsUsed(), report.blood().universalDonorUses());
        String ranOut = report.blood().ranOut().isEmpty() ? " No se agotó ningún tipo de sangre."
                : " Se agotaron: " + report.blood().ranOut().stream()
                        .map(usage -> usage.type().getLabel() + " a las " + ReportFacts.clock(usage.firstEmptyMinute()))
                        .collect(Collectors.joining(", ")) + ".";
        String deaths = report.blood().deathsForLackOfBlood() == 0 ? ""
                : format(" %d pacientes murieron por falta de sangre.", report.blood().deathsForLackOfBlood());
        return text + ranOut + deaths;
    }

    private static String missingResources(WhatIfReport whatIf) {
        if (whatIf == null) {
            return "No se hizo el análisis «¿y si…?».";
        }
        List<WhatIfResult> missing = whatIf.missingResources();
        if (missing.isEmpty()) {
            return "Ningún recurso extra evita muertes con claridad.";
        }
        return missing.stream().limit(MAX_ITEMS)
                .map(result -> format("Con %s se evitarían %s muertes por cada 100 pacientes.",
                        result.label(), decimal(result.deathsAvoidedPer100())))
                .collect(Collectors.joining(" "));
    }

    private static String policy(SimulationReport report, WhatIfReport whatIf) {
        if (whatIf == null) {
            return format("Se usó la estrategia %s. No se comparó con otras.", report.metrics().strategyName());
        }
        return whatIf.betterPolicy()
                .map(result -> format("Conviene cambiar a %s: %s", result.strategyName(), effect(result)))
                .orElse(format("La estrategia actual, %s, es la mejor de las probadas.", whatIf.strategyName()));
    }

    private static String cases(SimulationReport report) {
        if (report.cases().isEmpty()) {
            return "No hubo casos que revisar.";
        }
        return report.cases().stream().limit(MAX_ITEMS).map(TemplateNarrator::reviewCase)
                .collect(Collectors.joining(" "));
    }

    private static String reviewCase(ReviewCase review) {
        return format("%s: %s (esperó %d min).", review.patientCode(), lowerFirst(review.reason().getDisplayName()),
                review.waitMinutes());
    }

    private static String conclusion(SimulationReport report, WhatIfReport whatIf) {
        if (whatIf != null && !whatIf.recommendations().isEmpty()) {
            return format("La primera prioridad: %s. Con eso se evitarían %s muertes por cada 100 pacientes en"
                            + " este escenario.", whatIf.recommendations().get(0).label(),
                    decimal(whatIf.recommendations().get(0).deathsAvoidedPer100()));
        }
        return format("Con una mortalidad de %s %% y una espera promedio de %s min, el hospital no necesita cambios"
                        + " claros para este escenario.", decimal(100 * report.metrics().mortalityRate()),
                decimal(report.metrics().averageWaitMinutes()));
    }

    private static String effect(WhatIfResult result) {
        double wait = result.waitChange().mean();
        return format("evita %s muertes por cada 100 pacientes (± %s) y %s la espera %s min.",
                decimal(result.deathsAvoidedPer100()), decimal(100 * result.mortalityChange().confidenceHalfWidth()),
                wait <= 0 ? "acorta" : "alarga", decimal(Math.abs(wait)));
    }

    private static String format(String pattern, Object... values) {
        return String.format(SPANISH, pattern, values);
    }

    private static String decimal(double value) {
        return Double.isNaN(value) ? "—" : String.format(SPANISH, "%.1f", value);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }
}
