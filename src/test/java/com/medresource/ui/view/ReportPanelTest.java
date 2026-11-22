package com.medresource.ui.view;

import com.medresource.model.ExtraResource;
import com.medresource.model.GeneratedReport;
import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.ResourceChange;
import com.medresource.model.Statistic;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;
import com.medresource.services.management.ReportNarrator;
import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del contenido de la ventana del reporte, sin ventana.
 */
class ReportPanelTest {

    private static final Statistic BASE = Statistic.of(0.10, 0.12, 0.11);

    /** Alternativa que baja la mortalidad y la espera de forma clara. */
    private static WhatIfResult improvement(double mortalityChange, double waitChange) {
        ResourceChange change = ResourceChange.of(ExtraResource.OPERATING_ROOMS);
        return new WhatIfResult(change.describe(), change, "Prioridad clínica (triage)", BASE,
                Statistic.of(mortalityChange - 0.001, mortalityChange, mortalityChange + 0.001),
                Statistic.of(waitChange - 0.1, waitChange, waitChange + 0.1));
    }

    /** El reporte de ejemplo con otro análisis y otro texto. */
    private static GeneratedReport with(List<WhatIfResult> resources, ReportNarrative narrative) {
        GeneratedReport sample = ReportViewFixtures.generated();
        return new GeneratedReport(sample.report(), new WhatIfReport(3, "Prioridad clínica (triage)", BASE,
                Statistic.of(40, 45, 50), resources, List.of()), narrative);
    }

    @Test
    void showsTheCaseAndWhoWroteTheText() {
        ReportPanel panel = new ReportPanel(ReportViewFixtures.generated());

        assertEquals("Catástrofe · Prioridad clínica (triage) · Semilla 7 · 40 pacientes · llegadas durante 2 h",
                ReportPanel.caseText(ReportViewFixtures.generated().report()));
        assertEquals(NarrativeSource.TEMPLATE.getLabel() + " · " + ReportNarrator.NO_KEY_NOTE,
                panel.sourceLabel());
    }

    @Test
    void theCardsGoFromTheSummaryToTheAssumptions() {
        ReportPanel panel = new ReportPanel(ReportViewFixtures.generated());
        List<String> titles = panel.cardTitles();

        assertEquals(ReportPanel.SUMMARY, titles.getFirst());
        assertEquals(List.of(ReportPanel.RECOMMENDATIONS, ReportPanel.CONCLUSION, ReportPanel.ASSUMPTIONS),
                titles.subList(titles.size() - 3, titles.size()));
        List<String> sections = new ArrayList<>();
        for (ReportSection section : ReportSection.values()) {
            sections.add(section.getTitle());
        }
        assertEquals(sections, titles.subList(1, titles.size() - 3));
    }

    @Test
    void eachCardHasItsTextAndItsChart() {
        GeneratedReport generated = ReportViewFixtures.generated();
        ReportPanel panel = new ReportPanel(generated);

        assertEquals(generated.narrative().summary(), panel.textOf(ReportPanel.SUMMARY).orElseThrow());
        assertEquals(generated.narrative().conclusion(), panel.textOf(ReportPanel.CONCLUSION).orElseThrow());
        assertInstanceOf(BarChart.class, panel.chartOf(ReportSection.TRIAGE.getTitle()).orElseThrow());
        assertInstanceOf(TimelineStrip.class, panel.chartOf(ReportSection.TIMELINE.getTitle()).orElseThrow());
        assertTrue(panel.chartOf(ReportSection.CASES.getTitle()).isEmpty());
        assertTrue(panel.textOf(ReportPanel.ASSUMPTIONS).orElseThrow()
                .startsWith("• " + generated.report().assumptions().getFirst()));
    }

    @Test
    void showsThePolicyAverageClarificationBelowTheChart() {
        GeneratedReport generated = ReportViewFixtures.generated();
        ReportPanel panel = new ReportPanel(generated);

        assertTrue(textAreasIn(panel).stream().map(JTextArea::getText)
                .anyMatch(text -> text.equals(ReportCharts.policyExplanation(generated.whatIf().cases()))));
    }

    @Test
    void eachCaseToReviewHasItsButtonToSeeItOnThePlan() {
        GeneratedReport generated = ReportViewFixtures.generated();
        ReportPanel panel = new ReportPanel(generated);
        List<String> shown = new ArrayList<>();
        panel.onShowCase(shown::add);

        assertEquals(generated.report().cases().size(), panel.caseButtons().size());
        assertTrue(panel.caseButtons().size() > 1);

        panel.caseButtons().get(1).doClick();

        assertEquals(List.of(generated.report().cases().get(1).patientCode()), shown);
        assertEquals(ReportPanel.SHOW_CASE, panel.caseButtons().getFirst().getText());
    }

    @Test
    void eachRecommendationHasWhatItImprovesAndItsExplanation() {
        ReportNarrative narrative = new ReportNarrative(NarrativeSource.AI, "Resumen.", Map.of(),
                List.of("Hacía falta otro quirófano."), "Conclusión.", null);

        ReportPanel panel = new ReportPanel(with(List.of(improvement(-0.03, -20)), narrative));

        assertEquals(1, panel.recommendationCount());
        assertEquals("Hacía falta otro quirófano.", panel.textOf(ReportPanel.recommendationTitle(1)).orElseThrow());
        assertEquals(NarrativeSource.AI.getLabel(), panel.sourceLabel());
    }

    @Test
    void withoutRecommendationsItSaysSo() {
        ReportNarrative narrative = new ReportNarrative(NarrativeSource.AI, "Resumen.", Map.of(), List.of(),
                "Conclusión.", null);

        ReportPanel panel = new ReportPanel(with(List.of(), narrative));

        assertEquals(0, panel.recommendationCount());
        assertEquals(ReportPanel.NO_RECOMMENDATIONS, panel.textOf(ReportPanel.RECOMMENDATIONS).orElseThrow());
    }

    @Test
    void saysWhatARecommendationImproves() {
        assertEquals("evita 3 muertes por cada 100 pacientes · 20 min menos de espera",
                ReportPanel.effectText(improvement(-0.03, -20)));
        assertEquals("evita 1 muerte por cada 100 pacientes", ReportPanel.effectText(improvement(-0.01, 0.5)));
        assertEquals("12,5 min menos de espera", ReportPanel.effectText(improvement(0.0, -12.5)));
    }

    @Test
    void theTextsAreSplitIntoLinesAtTheWidthOfTheWindow() {
        ReportPanel panel = new ReportPanel(ReportViewFixtures.generated());

        panel.setSize(1400, 100);
        int wide = panel.getPreferredSize().height;
        panel.setSize(600, 100);
        int narrow = panel.getPreferredSize().height;

        assertTrue(narrow > wide, narrow + " > " + wide);
        assertTrue(panel.getScrollableTracksViewportWidth());
    }

    @Test
    void theTextsDoNotMoveTheScrollSoTheReportOpensFromTheTop() {
        List<JTextArea> paragraphs = textAreasIn(new ReportPanel(ReportViewFixtures.generated()));

        assertTrue(paragraphs.size() > 1);
        for (JTextArea paragraph : paragraphs) {
            assertEquals(0, paragraph.getCaretPosition());
            assertEquals(DefaultCaret.NEVER_UPDATE, ((DefaultCaret) paragraph.getCaret()).getUpdatePolicy());
        }
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReportPanel(null));
        ReportPanel panel = new ReportPanel(ReportViewFixtures.generated());
        assertThrows(IllegalArgumentException.class, () -> panel.onShowCase(null));
    }

    private static List<JTextArea> textAreasIn(Container container) {
        List<JTextArea> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JTextArea area) {
                found.add(area);
            } else if (child instanceof Container inner) {
                found.addAll(textAreasIn(inner));
            }
        }
        return found;
    }
}
