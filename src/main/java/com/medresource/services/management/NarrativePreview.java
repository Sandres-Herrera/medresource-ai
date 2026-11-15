package com.medresource.services.management;

import com.medresource.model.CaseConfig;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationReport;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.simulation.SimulationReportBuilder;
import com.medresource.services.simulation.WhatIfAnalyzer;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;

import java.util.List;

/**
 * Programa de consola que simula un caso en el hospital estándar, hace el análisis «¿y si…?» y escribe el texto
 * del reporte redactado por la IA (o por las plantillas, si no hay clave), para probar la IA antes de tener la
 * ventana del reporte.
 *
 * <p>Argumentos opcionales: el escenario (por defecto {@code CATASTROPHE}), la semilla (por defecto
 * {@value #DEFAULT_SEED}) y los casos del análisis (por defecto {@value #DEFAULT_CASES}, menos que en el reporte
 * completo para que la prueba sea rápida).</p>
 */
public final class NarrativePreview {

    /** Semilla del caso cuando no se indica otra. */
    public static final long DEFAULT_SEED = 7L;

    /** Casos del análisis «¿y si…?» cuando no se indican otros. */
    public static final int DEFAULT_CASES = 10;

    private NarrativePreview() {
    }

    /**
     * Simula, analiza y escribe el texto del reporte en la salida estándar.
     *
     * @param args opcionales: escenario, semilla y casos del análisis
     */
    public static void main(String[] args) {
        Scenario scenario = args.length > 0 ? Scenario.valueOf(args[0]) : Scenario.CATASTROPHE;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : DEFAULT_SEED;
        int cases = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_CASES;
        CaseConfig config = CaseConfig.of(seed, scenario);
        HospitalConfig hospitalConfig = HospitalConfig.standard();
        Hospital hospital = new HospitalFactory().create(hospitalConfig);
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(config), hospital,
                new ClinicalPriorityStrategy());
        SimulationReport report = new SimulationReportBuilder().build(engine.run(), engine.getTrace(), hospital);
        System.out.println("Analizando alternativas…");
        WhatIfReport whatIf = new WhatIfAnalyzer().analyze(config, hospitalConfig, ClinicalPriorityStrategy::new,
                List.of(FifoStrategy::new), cases, progress -> { });
        ReportNarrator narrator = ReportNarrator.fromConfiguration();
        long millis = narrator.estimatedMillis(report, whatIf);
        System.out.println(narrator.usesAi()
                ? "Redactando con la IA… unos " + Math.max(1, millis / 1000) + " s"
                : "Sin clave de la IA: se redacta con plantillas.");
        System.out.println(render(narrator.narrate(report, whatIf), whatIf));
    }

    /**
     * Escribe el texto del reporte: quién lo redactó, el resumen, cada sección, las recomendaciones con su
     * alternativa y la conclusión.
     *
     * @param narrative texto del reporte
     * @param whatIf    análisis «¿y si…?», para nombrar cada recomendación; {@code null} si no se hizo
     * @return texto para la consola
     * @throws IllegalArgumentException si falta el texto
     */
    static String render(ReportNarrative narrative, WhatIfReport whatIf) {
        if (narrative == null) {
            throw new IllegalArgumentException("El texto del reporte es obligatorio.");
        }
        StringBuilder text = new StringBuilder();
        String origin = narrative.note() == null ? narrative.source().getLabel()
                : narrative.source().getLabel() + " (" + narrative.note() + ")";
        text.append(origin).append(System.lineSeparator());
        section("Resumen", narrative.summary(), text);
        for (ReportSection section : ReportSection.values()) {
            narrative.sectionText(section).ifPresent(body -> section(section.getTitle(), body, text));
        }
        List<WhatIfResult> recommendations = whatIf == null ? List.of() : whatIf.recommendations();
        if (!narrative.recommendations().isEmpty()) {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < narrative.recommendations().size(); i++) {
                String label = i < recommendations.size() ? recommendations.get(i).label() + ": " : "";
                body.append(i + 1).append(". ").append(label).append(narrative.recommendations().get(i))
                        .append(System.lineSeparator());
            }
            section("Recomendaciones", body.toString().strip(), text);
        }
        section("Conclusión", narrative.conclusion(), text);
        return text.toString();
    }

    private static void section(String title, String body, StringBuilder text) {
        text.append(System.lineSeparator()).append("== ").append(title).append(" ==").append(System.lineSeparator())
                .append(body).append(System.lineSeparator());
    }
}
