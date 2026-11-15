package com.medresource.ui.view;

import com.medresource.model.ApaDocument;
import com.medresource.model.ApaDocument.Block;
import com.medresource.model.ApaDocument.Figure;
import com.medresource.model.ApaDocument.Heading;
import com.medresource.model.ApaDocument.Paragraph;
import com.medresource.model.ApaDocument.Table;
import com.medresource.model.ApaReference;
import com.medresource.model.CaseConfig;
import com.medresource.model.DocumentInfo;
import com.medresource.model.GeneratedReport;
import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.ReviewCase;
import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationReport;
import com.medresource.model.WhatIfResult;
import com.medresource.ui.view.ReportFigures.ChartImage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Arma el reporte de fin de simulación como un documento en normas APA 7.ª edición, listo para escribirlo en
 * Word o en PDF.
 *
 * <p>Sigue el mismo orden de la ventana del reporte: introducción, resumen con la tabla de indicadores, una parte
 * por sección con su texto y su figura, los casos para revisar en una tabla, las recomendaciones, la conclusión,
 * los supuestos y el origen del texto. Los números se escriben igual que en la ventana ({@link ReportFormat}) y las
 * figuras son las mismas gráficas, en colores de papel ({@link ReportFigures}). Las citas las pone el código; cada
 * referencia de la lista se cita al menos una vez.</p>
 */
public final class ApaReportBuilder {

    /** Título de la parte del resumen. */
    public static final String SUMMARY = "Resumen de la simulación";
    /** Título de la parte de los resultados. */
    public static final String RESULTS = "Resultados";
    /** Título de la parte de las recomendaciones. */
    public static final String RECOMMENDATIONS = "Recomendaciones";
    /** Título de la conclusión. */
    public static final String CONCLUSION = "Conclusión";
    /** Título de los supuestos y límites. */
    public static final String ASSUMPTIONS = "Supuestos y límites";
    /** Título de la parte que dice quién redactó el texto. */
    public static final String ORIGIN = "Origen del texto";

    private static final Map<ReportSection, String> FIGURE_TITLES = new EnumMap<>(Map.of(
            ReportSection.TRIAGE, "Espera promedio por nivel de triage y parte atendida dentro de la meta",
            ReportSection.DEATHS, "Fallecidos según el momento de su atención",
            ReportSection.BOTTLENECKS, "Minutos de espera que causó cada recurso",
            ReportSection.TIMELINE, "Periodos de colapso durante la simulación",
            ReportSection.RESOURCES, "Ocupación promedio de cada grupo de recursos",
            ReportSection.STAFF, "Ocupación promedio de los médicos por especialidad",
            ReportSection.BLOOD, "Unidades de sangre usadas por tipo",
            ReportSection.MISSING_RESOURCES, "Muertes evitadas por cada 100 pacientes con un recurso más",
            ReportSection.POLICY, "Mortalidad con cada estrategia de atención"));

    private final GeneratedReport generated;
    private final List<Block> blocks = new ArrayList<>();
    private int tables;
    private int figures;

    private ApaReportBuilder(GeneratedReport generated) {
        this.generated = generated;
    }

    /**
     * Propone el título del documento a partir del escenario.
     *
     * @param generated reporte terminado
     * @return por ejemplo «Reporte de la simulación de urgencias: escenario Catástrofe»
     * @throws IllegalArgumentException si falta el reporte
     */
    public static String defaultTitle(GeneratedReport generated) {
        if (generated == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        return "Reporte de la simulación de urgencias: escenario "
                + generated.report().config().scenario().getDisplayName();
    }

    /**
     * Arma el documento del reporte.
     *
     * @param generated reporte terminado
     * @param info      datos de la portada
     * @return documento en normas APA
     * @throws IllegalArgumentException si falta el reporte o la portada
     */
    public static ApaDocument build(GeneratedReport generated, DocumentInfo info) {
        if (generated == null || info == null) {
            throw new IllegalArgumentException("El reporte y los datos de la portada son obligatorios.");
        }
        ApaReportBuilder builder = new ApaReportBuilder(generated);
        builder.addIntroduction();
        builder.addSummary();
        builder.addResults();
        builder.addCases();
        builder.addRecommendations();
        builder.addConclusion();
        builder.addAssumptions();
        builder.addOrigin();
        return new ApaDocument(info, builder.blocks,
                ApaReference.forReport(generated.narrative().source()));
    }

    // ---------- partes ----------

    private void addIntroduction() {
        SimulationReport report = generated.report();
        CaseConfig config = report.config();
        blocks.add(Paragraph.body("Este reporte presenta los resultados de una simulación del servicio de urgencias "
                + "hecha con MedResource AI " + ApaReference.SIMULATOR.parenthetical() + ". El caso corresponde al "
                + "escenario " + config.scenario().getDisplayName() + ", atendido con la estrategia "
                + report.metrics().strategyName() + ", con la semilla " + config.seed() + " y "
                + config.patientCount() + " pacientes que llegaron durante "
                + ReportFormat.decimal(config.durationMinutes() / 60.0) + " h. Todas las cifras las calculó el "
                + "simulador; la Tabla 1 resume los indicadores principales."));
    }

    private void addSummary() {
        SimulationReport report = generated.report();
        RunMetrics metrics = report.metrics();
        blocks.add(new Heading(1, SUMMARY));
        blocks.add(Paragraph.body(generated.narrative().summary()));
        blocks.add(new Table(++tables, "Indicadores principales de la simulación", List.of("Indicador", "Valor"),
                List.of(List.of("Pacientes", String.valueOf(metrics.patients())),
                        List.of("Atendidos", String.valueOf(report.treated())),
                        List.of("Fallecidos", metrics.deaths() + " (" + ReportFormat.percent(metrics.mortalityRate())
                                + ")"),
                        List.of("Espera promedio", ReportFormat.decimal(metrics.averageWaitMinutes()) + " min"),
                        List.of("Espera máxima", report.maxWaitMinutes() + " min")),
                "Cifras calculadas por el simulador para el escenario "
                        + report.config().scenario().getDisplayName() + " con la semilla " + report.config().seed()
                        + "."));
    }

    private void addResults() {
        ReportNarrative narrative = generated.narrative();
        blocks.add(new Heading(1, RESULTS));
        for (ReportSection section : ReportSection.values()) {
            if (section == ReportSection.CASES) {
                continue;
            }
            Optional<String> text = narrative.sectionText(section);
            Optional<ChartImage> image = ReportFigures.imageFor(section, generated);
            if (text.isEmpty() && image.isEmpty()) {
                continue;
            }
            blocks.add(new Heading(2, section.getTitle()));
            String reference = image.isPresent() ? " Véase la Figura " + (figures + 1) + "." : "";
            text.ifPresentOrElse(paragraph -> blocks.add(Paragraph.body(paragraph + reference)),
                    () -> blocks.add(Paragraph.body("Los datos de esta sección se muestran en la Figura "
                            + (figures + 1) + ".")));
            image.ifPresent(chart -> blocks.add(new Figure(++figures, FIGURE_TITLES.get(section), chart.png(),
                    chart.width(), chart.height(), figureNote(section))));
        }
    }

    private void addCases() {
        List<ReviewCase> cases = generated.report().cases();
        if (cases.isEmpty()) {
            return;
        }
        blocks.add(new Heading(1, ReportSection.CASES.getTitle()));
        String intro = generated.narrative().sectionText(ReportSection.CASES)
                .orElse("Estos pacientes resumen lo que pasó en la simulación.");
        blocks.add(Paragraph.body(intro + " Véase la Tabla " + (tables + 1) + "."));
        List<List<String>> rows = new ArrayList<>();
        for (ReviewCase reviewCase : cases) {
            rows.add(List.of(reviewCase.patientCode(), reviewCase.triage().getDisplayName(),
                    reviewCase.status().getDisplayName(), reviewCase.reason().getDisplayName(),
                    String.valueOf(reviewCase.waitMinutes()), ReportFormat.clock(reviewCase.focusMinute())));
        }
        blocks.add(new Table(++tables, "Pacientes para revisar en el plano",
                List.of("Paciente", "Triage", "Estado", "Motivo", "Espera (min)", "Momento clave"), rows,
                "El momento clave es la hora del reloj de la simulación que conviene ver en el plano."));
    }

    private void addRecommendations() {
        List<WhatIfResult> results = generated.whatIf().recommendations();
        List<String> explanations = generated.narrative().recommendations();
        blocks.add(new Heading(1, RECOMMENDATIONS));
        if (results.isEmpty()) {
            blocks.add(Paragraph.body("Ninguna de las alternativas probadas mejoró el hospital de forma clara."));
            return;
        }
        blocks.add(Paragraph.body("Para ordenar las recomendaciones, el simulador repitió el mismo caso "
                + generated.whatIf().cases() + " veces con semillas consecutivas, con un recurso más cada vez o con "
                + "otra estrategia. Solo se recomiendan los cambios que mejoran el hospital de forma clara, del que "
                + "más mejora al que menos."));
        boolean templates = generated.narrative().source() == NarrativeSource.TEMPLATE;
        for (int i = 0; i < results.size(); i++) {
            WhatIfResult result = results.get(i);
            String effect = ReportPanel.effectText(result).replace(" · ", "; ");
            String text;
            if (i >= explanations.size()) {
                text = result.label() + ": " + effect + ".";
            } else if (templates) {
                text = result.label() + ": " + withPeriod(explanations.get(i));
            } else {
                text = result.label() + " (" + effect + "). " + capitalized(explanations.get(i));
            }
            blocks.add(Paragraph.item((i + 1) + ". " + text));
        }
    }

    private void addConclusion() {
        blocks.add(new Heading(1, CONCLUSION));
        blocks.add(Paragraph.body(generated.narrative().conclusion()));
    }

    private void addAssumptions() {
        blocks.add(new Heading(1, ASSUMPTIONS));
        blocks.add(Paragraph.body("Los resultados dependen de los supuestos del simulador. Las metas de espera de "
                + "los niveles I y II se toman de la Resolución 5596 de 2015 "
                + ApaReference.TRIAGE_RESOLUTION.parenthetical() + "."));
        for (String assumption : generated.report().assumptions()) {
            blocks.add(Paragraph.item("• " + assumption));
        }
    }

    private void addOrigin() {
        ReportNarrative narrative = generated.narrative();
        blocks.add(new Heading(1, ORIGIN));
        String text = narrative.source() == NarrativeSource.AI
                ? "El texto explicativo lo redactó la IA Gemini " + ApaReference.GEMINI.parenthetical() + " a partir "
                + "de las cifras que calculó el simulador. La IA no calculó ninguna cifra, y las frases con números "
                + "que no estaban en esos datos se descartaron."
                : "El texto explicativo se generó automáticamente con plantillas a partir de las cifras que calculó "
                + "el simulador. Motivo: " + withPeriod(narrative.note());
        blocks.add(Paragraph.body(text + " El documento sigue las normas APA, 7.ª edición, en su versión para "
                + "estudiantes " + ApaReference.APA_MANUAL.parenthetical() + "."));
    }

    // ---------- textos ----------

    private String figureNote(ReportSection section) {
        long seed = generated.report().config().seed();
        return switch (section) {
            case TIMELINE -> "Las franjas rojas son los periodos de colapso y la línea punteada marca el final de las "
                    + "llegadas. Hora del reloj de la simulación.";
            case MISSING_RESOURCES, POLICY -> "Promedio de " + generated.whatIf().cases() + " simulaciones del mismo "
                    + "caso con semillas consecutivas desde la " + seed + ".";
            default -> "Elaboración propia con los datos de la simulación (semilla " + seed + ").";
        };
    }

    /** Pone en mayúscula la primera letra. */
    private static String capitalized(String text) {
        String clean = text.strip();
        return clean.isEmpty() ? clean : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static String withPeriod(String text) {
        String clean = text.strip();
        return clean.endsWith(".") ? clean : clean + ".";
    }
}
