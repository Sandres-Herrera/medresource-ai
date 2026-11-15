package com.medresource.services.simulation;

import com.medresource.model.BloodUsage;
import com.medresource.model.BottleneckStats;
import com.medresource.model.CaseConfig;
import com.medresource.model.CollapsePeriod;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.ResourceUsage;
import com.medresource.model.ReviewCase;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationReport;
import com.medresource.model.SpecialtyLoad;
import com.medresource.model.StaffLoad;
import com.medresource.model.TriageLevelStats;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;

import java.util.List;
import java.util.Locale;

/**
 * Programa de consola que simula un caso en el hospital estándar con la prioridad clínica y escribe su reporte
 * de fin de simulación, con el análisis «¿y si…?», para revisar los números antes de verlos en la interfaz.
 *
 * <p>Argumentos opcionales: el escenario ({@code NORMAL}, {@code HIGH_DEMAND}, {@code CATASTROPHE} o
 * {@code UNEXPECTED_EMERGENCIES}; por defecto {@code CATASTROPHE}), la semilla (por defecto
 * {@value #DEFAULT_SEED}) y los casos del análisis «¿y si…?» (por defecto
 * {@value WhatIfAnalyzer#DEFAULT_CASES}). Mientras analiza escribe el avance en la salida de errores.</p>
 */
public final class ReportPreview {

    /** Semilla del caso cuando no se indica otra. */
    public static final long DEFAULT_SEED = 7L;

    private static final Locale SPANISH = Locale.of("es", "CO");

    private ReportPreview() {
    }

    /**
     * Simula el caso y escribe su reporte en la salida estándar.
     *
     * @param args opcionales: escenario y semilla
     */
    public static void main(String[] args) {
        Scenario scenario = args.length > 0 ? Scenario.valueOf(args[0]) : Scenario.CATASTROPHE;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : DEFAULT_SEED;
        int cases = args.length > 2 ? Integer.parseInt(args[2]) : WhatIfAnalyzer.DEFAULT_CASES;
        HospitalConfig hospital = HospitalConfig.standard();
        System.out.println(render(simulate(scenario, seed, hospital)));
        WhatIfReport whatIf = new WhatIfAnalyzer().analyze(CaseConfig.of(seed, scenario), hospital,
                ClinicalPriorityStrategy::new, List.of(FifoStrategy::new), cases, ReportPreview::printProgress);
        System.out.println(renderWhatIf(whatIf));
    }

    /** Escribe el avance cada vez que se completa otro 10 % de las simulaciones. */
    private static void printProgress(WhatIfProgress progress) {
        int step = Math.max(1, progress.total() / 10);
        if (progress.done() % step == 0 || progress.isFinished()) {
            long seconds = Math.max(0, progress.estimatedRemainingMillis()) / 1000;
            System.err.println(String.format(SPANISH, "Analizando alternativas: %d de %d (%.0f %%), faltan unos %d s",
                    progress.done(), progress.total(), 100 * progress.fraction(), seconds));
        }
    }

    /**
     * Simula un caso con la prioridad clínica y arma su reporte.
     *
     * @param scenario escenario del caso
     * @param seed     semilla del caso
     * @param config   recursos del hospital
     * @return reporte de la simulación
     */
    static SimulationReport simulate(Scenario scenario, long seed, HospitalConfig config) {
        Hospital hospital = new HospitalFactory().create(config);
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(CaseConfig.of(seed, scenario)),
                hospital, new ClinicalPriorityStrategy());
        return new SimulationReportBuilder().build(engine.run(), engine.getTrace(), hospital);
    }

    /**
     * Escribe el reporte como texto, una sección tras otra.
     *
     * @param report reporte de la simulación
     * @return texto del reporte
     * @throws IllegalArgumentException si falta el reporte
     */
    static String render(SimulationReport report) {
        if (report == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        StringBuilder text = new StringBuilder();
        summary(report, text);
        triage(report, text);
        deaths(report, text);
        bottlenecks(report, text);
        resources(report, text);
        staff(report, text);
        blood(report, text);
        cases(report, text);
        title("Supuestos y límites", text);
        report.assumptions().forEach(assumption -> line(text, "- %s", assumption));
        return text.toString();
    }

    /**
     * Escribe el análisis «¿y si…?»: los recursos que faltaron, la comparación de estrategias y las
     * recomendaciones.
     *
     * @param whatIf resultado del análisis
     * @return texto de las tres secciones
     * @throws IllegalArgumentException si falta el análisis
     */
    static String renderWhatIf(WhatIfReport whatIf) {
        if (whatIf == null) {
            throw new IllegalArgumentException("El análisis «¿y si…?» es obligatorio.");
        }
        StringBuilder text = new StringBuilder();
        title("Recursos que faltaron", text);
        line(text, "Se simularon %d casos de este escenario con cada alternativa. Hospital tal como está: mortalidad"
                        + " %s (± %s) y espera promedio %s min.", whatIf.cases(), percent(whatIf.mortality().mean()),
                percent(whatIf.mortality().confidenceHalfWidth()), decimal(whatIf.waitMinutes().mean()));
        if (whatIf.missingResources().isEmpty()) {
            line(text, "Ningún recurso extra evita muertes con claridad.");
        }
        whatIf.missingResources().forEach(result -> line(text, "- %s: %s", result.label(), effect(result)));
        line(text, "Todas las alternativas probadas:");
        whatIf.resources().forEach(result -> line(text, "  · %s: %s", result.label(), effect(result)));
        title("Política de atención", text);
        line(text, "Estrategia actual: %s.", whatIf.strategyName());
        whatIf.policies().forEach(result -> line(text, "- %s: %s", result.strategyName(), effect(result)));
        line(text, "%s", whatIf.betterPolicy()
                .map(result -> "Conviene cambiar a " + result.strategyName() + ".")
                .orElse("La estrategia actual es la mejor de las probadas."));
        title("Recomendaciones", text);
        List<WhatIfResult> recommendations = whatIf.recommendations();
        if (recommendations.isEmpty()) {
            line(text, "El hospital no necesita cambios: ninguna alternativa lo mejora con claridad.");
        }
        for (int i = 0; i < recommendations.size(); i++) {
            line(text, "%d. %s: %s", i + 1, recommendations.get(i).label(), effect(recommendations.get(i)));
        }
        return text.toString();
    }

    /** Efecto de una alternativa en la mortalidad y la espera, con el margen de la mortalidad. */
    private static String effect(WhatIfResult result) {
        double margin = 100 * result.mortalityChange().confidenceHalfWidth();
        String deaths = String.format(SPANISH, "%s %s muertes por cada 100 pacientes (± %s)",
                result.deathsAvoidedPer100() >= 0 ? "evita" : "suma", decimal(Math.abs(result.deathsAvoidedPer100())),
                decimal(margin));
        double wait = result.waitChange().mean();
        String waitText = String.format(SPANISH, "%s la espera %s min", wait <= 0 ? "acorta" : "alarga",
                decimal(Math.abs(wait)));
        String verdict = result.isImprovement() ? " · mejora" : "";
        return deaths + ", " + waitText + verdict;
    }

    private static void summary(SimulationReport report, StringBuilder text) {
        CaseConfig config = report.config();
        title("Resumen", text);
        line(text, "%s · semilla %d · %d pacientes en %s · %s", config.scenario().getDisplayName(), config.seed(),
                config.patientCount(), clock(config.durationMinutes()), report.metrics().strategyName());
        line(text, "Atendidos: %d · Fallecidos: %d (%s) · Remitidos: %d · Sin atender: %d",
                report.treated(), report.metrics().deaths(), percent(report.metrics().mortalityRate()),
                report.metrics().referred(), report.metrics().unattended());
        line(text, "Espera promedio: %s min · Espera máxima: %d min",
                decimal(report.metrics().averageWaitMinutes()), report.maxWaitMinutes());
        line(text, "Al cerrar las llegadas seguían %d esperando y %d en tratamiento; el hospital necesitó %d min más.",
                report.unfinished().stillWaiting(), report.unfinished().inTreatment(),
                report.unfinished().extraMinutes());
    }

    private static void triage(SimulationReport report, StringBuilder text) {
        title("Por nivel de triage", text);
        line(text, "%-28s %9s %10s %12s %11s %12s %13s", "Nivel", "Pacientes", "Fallecidos", "Espera prom.",
                "Espera máx.", "En la meta", "Salud perdida");
        for (TriageLevelStats stats : report.triage()) {
            line(text, "%-28s %9d %10d %12s %11d %12s %13s", stats.level().getDisplayName(), stats.patients(),
                    stats.deaths(), decimal(stats.averageWaitMinutes()), stats.maxWaitMinutes(),
                    percent(stats.withinTargetRate()), decimal(stats.averageHealthLost()));
        }
    }

    private static void deaths(SimulationReport report, StringBuilder text) {
        title("Fallecidos y fallas", text);
        line(text, "Evitables: %d (murieron esperando: %d · atendidos tarde: %d) · Inevitables (atendidos a tiempo): %d",
                report.deaths().avoidable(), report.deaths().whileWaiting(), report.deaths().afterLateCare(),
                report.deaths().unavoidable());
        report.deaths().byCause().forEach((cause, count) -> line(text, "- %s: %d", cause.getDisplayName(), count));
        line(text, "Pacientes fuera de la meta de espera: %d · Críticos sin primeros auxilios a tiempo: %d"
                        + " (la espera más larga sin ellos: %d min)", report.failures().overTarget(),
                report.failures().criticalWithoutTimelyAid(), report.failures().longestCriticalWithoutAidMinutes());
    }

    private static void bottlenecks(SimulationReport report, StringBuilder text) {
        title("Cuellos de botella", text);
        if (report.bottlenecks().isEmpty()) {
            line(text, "Nadie esperó por falta de recursos.");
        }
        for (BottleneckStats stats : report.bottlenecks()) {
            line(text, "- %s: %s de la espera (%d min-paciente, %d pacientes); faltó de %s a %s; máximo %d"
                            + " esperando a las %s", stats.resource().getDisplayName(), percent(stats.share()),
                    stats.waitingMinutes(), stats.patientsAffected(), clock(stats.firstMinute()),
                    clock(stats.lastMinute()), stats.peakPatients(), clock(stats.peakMinute()));
        }
        title("Línea de tiempo", text);
        if (report.collapses().isEmpty()) {
            line(text, "El servicio nunca colapsó: todos se atendieron dentro de su meta.");
        }
        for (CollapsePeriod collapse : report.collapses()) {
            line(text, "- Colapso de %s a %s (%d min); peor momento a las %s con %d pacientes fuera de su meta",
                    clock(collapse.startMinute()), clock(collapse.endMinute()), collapse.durationMinutes(),
                    clock(collapse.peakMinute()), collapse.peakOverTarget());
        }
    }

    private static void resources(SimulationReport report, StringBuilder text) {
        title("Ocupación de los recursos", text);
        for (ResourceUsage usage : report.resources()) {
            String note = usage.isUnderused() ? " · subutilizado" : "";
            line(text, "- %s (%d): promedio %s, máximo %s, %d min llenos%s", usage.group().getDisplayName(),
                    usage.capacity(), percent(usage.averageOccupancy()), percent(usage.peakOccupancy()),
                    usage.fullMinutes(), note);
        }
    }

    private static void staff(SimulationReport report, StringBuilder text) {
        title("Carga del personal", text);
        for (SpecialtyLoad load : report.specialties()) {
            line(text, "- %s · médicos: %d · pacientes: %d · ocupación: %s · espera por médico: %d min-paciente",
                    load.specialty().getDisplayName(), load.doctors(), load.patients(),
                    percent(load.averageOccupancy()), load.doctorWaitMinutes());
        }
        line(text, "Saturados: %s", codes(report.saturatedStaff()));
        line(text, "Libres la mayor parte del tiempo: %s", codes(report.idleStaff()));
    }

    private static void blood(SimulationReport report, StringBuilder text) {
        title("Banco de sangre", text);
        line(text, "Transfusiones: %d · Unidades usadas: %d · Con O negativo: %d (%d en pacientes de otro tipo)",
                report.blood().transfusions(), report.blood().unitsUsed(), report.blood().universalDonorUses(),
                report.blood().universalDonorForOthers());
        line(text, "Pacientes que esperaron sangre: %d · Fallecidos por falta de sangre: %d",
                report.blood().patientsWaitingForBlood(), report.blood().deathsForLackOfBlood());
        for (BloodUsage usage : report.blood().ranOut()) {
            line(text, "- %s se agotó a las %s", usage.type().getLabel(), clock(usage.firstEmptyMinute()));
        }
    }

    private static void cases(SimulationReport report, StringBuilder text) {
        title("Casos para revisar", text);
        if (report.cases().isEmpty()) {
            line(text, "No hubo casos que revisar.");
        }
        for (ReviewCase review : report.cases()) {
            line(text, "- %s (%s): %s; esperó %d min, perdió %s de salud, terminó %s. Ver a las %s.",
                    review.patientCode(), review.triage().getDisplayName(), review.reason().getDisplayName(),
                    review.waitMinutes(), decimal(review.healthLost()),
                    review.status().getDisplayName().toLowerCase(SPANISH), clock(review.focusMinute()));
        }
    }

    private static String codes(List<StaffLoad> loads) {
        return loads.isEmpty() ? "ninguno" : String.join(", ", loads.stream().map(StaffLoad::code).toList());
    }

    private static void title(String title, StringBuilder text) {
        text.append(System.lineSeparator()).append("== ").append(title).append(" ==").append(System.lineSeparator());
    }

    private static void line(StringBuilder text, String format, Object... values) {
        text.append(String.format(SPANISH, format, values)).append(System.lineSeparator());
    }

    /** Minuto de la simulación como hora del reloj, por ejemplo 70 → {@code 01:10}. */
    static String clock(int minute) {
        return String.format(SPANISH, "%02d:%02d", minute / 60, minute % 60);
    }

    private static String percent(double fraction) {
        return Double.isNaN(fraction) ? "—" : String.format(SPANISH, "%.1f %%", 100 * fraction);
    }

    private static String decimal(double value) {
        return Double.isNaN(value) ? "—" : String.format(SPANISH, "%.1f", value);
    }
}
