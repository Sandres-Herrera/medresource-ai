package com.medresource.services.management;

import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportProgress;
import com.medresource.model.ReportStage;
import com.medresource.model.SimulationReport;
import com.medresource.model.WhatIfReport;
import com.medresource.services.simulation.SimulationReportBuilder;
import com.medresource.services.simulation.WhatIfAnalyzer;
import com.medresource.services.simulation.WhatIfProgress;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Genera el reporte de fin de simulación por etapas y avisa cómo va, para la barra de progreso.
 *
 * <p>Las etapas son las de {@link ReportStage}: primero las métricas de la simulación que se vio
 * ({@link SimulationReportBuilder}), después el análisis «¿y si…?» ({@link WhatIfAnalyzer}), que es lo que más
 * tarda, y al final el texto ({@link ReportNarrator}).</p>
 *
 * <p>El tiempo que falta se estima así: durante el «¿y si…?», lo que el propio análisis estima que le falta más lo
 * que suele tardar la IA; durante la redacción, lo que se estima para esa respuesta (0 si ya está en la caché o
 * si se usan las plantillas). La estimación mejora a medida que avanza.</p>
 *
 * <p>Se puede cancelar: se revisa entre etapas y después de cada simulación del «¿y si…?».</p>
 */
public class ReportGenerator {

    /** Estrategias que se comparan en el «¿y si…?»; se salta la que ya usa el hospital. */
    public static final List<Supplier<AllocationStrategy>> POLICIES =
            List.of(ClinicalPriorityStrategy::new, FifoStrategy::new);

    private final SimulationReportBuilder builder = new SimulationReportBuilder();
    private final WhatIfAnalyzer analyzer;
    private final ReportNarrator narrator;
    private final int cases;

    /**
     * Crea el generador.
     *
     * @param analyzer analizador de las alternativas
     * @param narrator redactor del texto
     * @param cases    casos por alternativa en el «¿y si…?», de 2 a {@value WhatIfAnalyzer#MAX_CASES}
     * @throws IllegalArgumentException si falta el analizador o el redactor, o si los casos están fuera de rango
     */
    public ReportGenerator(WhatIfAnalyzer analyzer, ReportNarrator narrator, int cases) {
        if (analyzer == null || narrator == null) {
            throw new IllegalArgumentException("El analizador y el redactor son obligatorios.");
        }
        if (cases < 2 || cases > WhatIfAnalyzer.MAX_CASES) {
            throw new IllegalArgumentException("Los casos deben estar entre 2 y " + WhatIfAnalyzer.MAX_CASES + ".");
        }
        this.analyzer = analyzer;
        this.narrator = narrator;
        this.cases = cases;
    }

    /**
     * Crea el generador que usa la aplicación: la IA si hay clave ({@link ReportNarrator#fromConfiguration()}) y
     * {@value WhatIfAnalyzer#DEFAULT_CASES} casos por alternativa.
     *
     * @return generador listo para usar
     */
    public static ReportGenerator fromConfiguration() {
        return new ReportGenerator(new WhatIfAnalyzer(), ReportNarrator.fromConfiguration(),
                WhatIfAnalyzer.DEFAULT_CASES);
    }

    /**
     * Indica si el texto lo redactará la IA.
     *
     * @return {@code true} si hay IA; {@code false} si se usarán las plantillas
     */
    public boolean usesAi() {
        return narrator.usesAi();
    }

    /**
     * Genera el reporte sin poder cancelarlo.
     *
     * @param request  datos de la simulación que terminó
     * @param progress recibe el avance al empezar cada etapa y después de cada simulación del «¿y si…?»
     * @return el reporte terminado
     * @throws IllegalArgumentException si falta algún dato
     */
    public GeneratedReport generate(ReportRequest request, Consumer<ReportProgress> progress) {
        return generate(request, progress, () -> false);
    }

    /**
     * Genera el reporte.
     *
     * @param request   datos de la simulación que terminó
     * @param progress  recibe el avance al empezar cada etapa y después de cada simulación del «¿y si…?»; puede
     *                  llamarse desde varios hilos, pero nunca dos veces a la vez
     * @param cancelled dice si el usuario canceló
     * @return el reporte terminado
     * @throws IllegalArgumentException si falta algún dato
     * @throws CancellationException    si se canceló antes de terminar
     */
    public GeneratedReport generate(ReportRequest request, Consumer<ReportProgress> progress,
                                    BooleanSupplier cancelled) {
        if (request == null || progress == null || cancelled == null) {
            throw new IllegalArgumentException("Faltan datos para generar el reporte.");
        }
        long start = System.nanoTime();
        progress.accept(ReportProgress.start());
        SimulationReport report = builder.build(request.result(), request.trace(), request.hospital());
        stopIfCancelled(cancelled);

        long writingGuess = narrator.typicalMillis();
        progress.accept(new ReportProgress(ReportStage.WHAT_IF, millisSince(start), ReportProgress.UNKNOWN));
        WhatIfReport whatIf = analyzer.analyze(request.caseConfig(), request.hospitalConfig(), request.strategy(),
                POLICIES, cases, step -> {
                    stopIfCancelled(cancelled);
                    progress.accept(whatIfProgress(step, millisSince(start), writingGuess));
                });
        stopIfCancelled(cancelled);

        progress.accept(new ReportProgress(ReportStage.WRITING, millisSince(start),
                narrator.estimatedMillis(report, whatIf)));
        ReportNarrative narrative = narrator.narrate(report, whatIf);
        stopIfCancelled(cancelled);
        return new GeneratedReport(report, whatIf, narrative);
    }

    /**
     * Calcula el avance durante el «¿y si…?»: lo que le falta al análisis más lo que suele tardar la IA.
     *
     * @param step          avance del análisis
     * @param elapsedMillis milisegundos desde que empezó la generación
     * @param writingMillis lo que suele tardar la redacción
     * @return el avance de la generación; sin estimación si el análisis todavía no tiene con qué estimar
     */
    static ReportProgress whatIfProgress(WhatIfProgress step, long elapsedMillis, long writingMillis) {
        long remaining = step.estimatedRemainingMillis();
        return new ReportProgress(ReportStage.WHAT_IF, elapsedMillis,
                remaining < 0 ? ReportProgress.UNKNOWN : remaining + writingMillis);
    }

    private static void stopIfCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException("Se canceló la generación del reporte.");
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
