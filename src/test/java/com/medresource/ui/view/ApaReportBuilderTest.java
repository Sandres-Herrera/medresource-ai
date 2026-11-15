package com.medresource.ui.view;

import com.medresource.model.ApaDocument;
import com.medresource.model.ApaDocument.Block;
import com.medresource.model.ApaDocument.Figure;
import com.medresource.model.ApaDocument.Heading;
import com.medresource.model.ApaDocument.Paragraph;
import com.medresource.model.ApaDocument.ParagraphStyle;
import com.medresource.model.ApaDocument.Table;
import com.medresource.model.ApaReference;
import com.medresource.model.DocumentInfo;
import com.medresource.model.GeneratedReport;
import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.RunMetrics;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del armado del reporte como documento APA.
 */
class ApaReportBuilderTest {

    private static final DocumentInfo INFO = DocumentInfo.draft("Reporte de prueba", LocalDate.of(2026, 11, 15));

    @Test
    void thePartsGoInTheOrderOfTheWindow() {
        GeneratedReport generated = ReportViewFixtures.generated();
        ApaDocument document = ApaReportBuilder.build(generated, INFO);

        List<String> mainHeadings = new ArrayList<>();
        for (Block block : document.blocks()) {
            if (block instanceof Heading heading && heading.level() == 1) {
                mainHeadings.add(heading.text());
            }
        }
        List<String> expected = new ArrayList<>(List.of(ApaReportBuilder.SUMMARY, ApaReportBuilder.RESULTS));
        if (!generated.report().cases().isEmpty()) {
            expected.add(ReportSection.CASES.getTitle());
        }
        expected.addAll(List.of(ApaReportBuilder.RECOMMENDATIONS, ApaReportBuilder.CONCLUSION,
                ApaReportBuilder.ASSUMPTIONS, ApaReportBuilder.ORIGIN));

        assertEquals(INFO, document.info());
        assertEquals(expected, mainHeadings);
        assertTrue(document.blocks().getFirst() instanceof Paragraph intro
                && intro.text().contains(ApaReference.SIMULATOR.parenthetical()));
    }

    @Test
    void theFirstTableHasTheMainIndicatorsAsInTheWindow() {
        GeneratedReport generated = ReportViewFixtures.generated();
        RunMetrics metrics = generated.report().metrics();

        Table table = ApaReportBuilder.build(generated, INFO).tables().getFirst();

        assertEquals("Tabla 1", table.label());
        assertEquals(List.of("Indicador", "Valor"), table.headers());
        assertEquals(List.of("Pacientes", String.valueOf(metrics.patients())), table.rows().get(0));
        assertEquals(List.of("Fallecidos", metrics.deaths() + " (" + ReportFormat.percent(metrics.mortalityRate())
                + ")"), table.rows().get(2));
        assertEquals(List.of("Espera promedio", ReportFormat.decimal(metrics.averageWaitMinutes()) + " min"),
                table.rows().get(3));
        assertFalse(table.note().isBlank());
    }

    @Test
    void thereIsOneFigurePerChartAndEachOneIsMentionedInTheText() {
        GeneratedReport generated = ReportViewFixtures.generated();
        long charts = List.of(ReportSection.values()).stream()
                .filter(section -> ReportCharts.chartFor(section, generated).isPresent())
                .count();

        ApaDocument document = ApaReportBuilder.build(generated, INFO);
        String text = allText(document);

        assertEquals(charts, document.figures().size());
        for (Figure figure : document.figures()) {
            assertTrue(text.contains("Figura " + figure.number() + "."), figure.label());
            assertFalse(figure.note().isBlank(), figure.label());
        }
        for (Table table : document.tables()) {
            assertTrue(text.contains("Tabla " + table.number()), table.label());
        }
    }

    @Test
    void everyReferenceIsCitedAndGeminiOnlyWithTheAi() {
        GeneratedReport templates = ReportViewFixtures.generated();
        GeneratedReport ai = withAi(templates);

        for (GeneratedReport generated : List.of(templates, ai)) {
            ApaDocument document = ApaReportBuilder.build(generated, INFO);
            String text = allText(document);
            assertEquals(ApaReference.forReport(generated.narrative().source()), document.references());
            for (ApaReference reference : document.references()) {
                assertTrue(text.contains(reference.parenthetical()), reference.citation());
            }
        }
        assertFalse(allText(ApaReportBuilder.build(templates, INFO)).contains("Gemini"));
        assertTrue(allText(ApaReportBuilder.build(templates, INFO)).contains(templates.narrative().note().strip()));
    }

    @Test
    void theRecommendationsAndTheAssumptionsAreListItems() {
        GeneratedReport generated = ReportViewFixtures.generated();
        List<Paragraph> items = ApaReportBuilder.build(generated, INFO).blocks().stream()
                .filter(Paragraph.class::isInstance)
                .map(Paragraph.class::cast)
                .filter(paragraph -> paragraph.style() == ParagraphStyle.ITEM)
                .toList();

        int recommendations = generated.whatIf().recommendations().size();
        assertEquals(recommendations + generated.report().assumptions().size(), items.size());
        for (int i = 0; i < recommendations; i++) {
            assertTrue(items.get(i).text().startsWith((i + 1) + ". "
                    + generated.whatIf().recommendations().get(i).label() + ": "));
        }
        assertTrue(items.getLast().text().startsWith("• "));
    }

    @Test
    void theTitleNamesTheScenario() {
        GeneratedReport generated = ReportViewFixtures.generated();

        assertEquals("Reporte de la simulación de urgencias: escenario "
                + generated.report().config().scenario().getDisplayName(), ApaReportBuilder.defaultTitle(generated));
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ApaReportBuilder.build(null, INFO));
        assertThrows(IllegalArgumentException.class,
                () -> ApaReportBuilder.build(ReportViewFixtures.generated(), null));
        assertThrows(IllegalArgumentException.class, () -> ApaReportBuilder.defaultTitle(null));
    }

    private static GeneratedReport withAi(GeneratedReport generated) {
        ReportNarrative text = generated.narrative();
        return new GeneratedReport(generated.report(), generated.whatIf(), new ReportNarrative(NarrativeSource.AI,
                text.summary(), text.sections(), text.recommendations(), text.conclusion(), null));
    }

    private static String allText(ApaDocument document) {
        StringBuilder text = new StringBuilder();
        for (Block block : document.blocks()) {
            switch (block) {
                case Heading heading -> text.append(heading.text());
                case Paragraph paragraph -> text.append(paragraph.text());
                case Table table -> text.append(table.title()).append(table.note());
                case Figure figure -> text.append(figure.title()).append(figure.note());
            }
            text.append('\n');
        }
        return text.toString();
    }
}
