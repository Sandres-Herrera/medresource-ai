package com.medresource.services.management;

import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.WhatIfReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la vista previa del texto del reporte en consola.
 */
class NarrativePreviewTest {

    @Test
    void saysWhoWroteItAndWhy() {
        ReportNarrative narrative = TemplateNarrator.write(ReportFactsTest.report(), null, "Sin clave");

        String text = NarrativePreview.render(narrative, null);

        assertTrue(text.startsWith("Generado automáticamente (Sin clave)"));
    }

    @Test
    void writesTheSummaryEverySectionAndTheConclusion() {
        ReportNarrative narrative = TemplateNarrator.write(ReportFactsTest.report(), null, "Sin clave");

        String text = NarrativePreview.render(narrative, null);

        assertTrue(text.contains("== Resumen =="));
        for (ReportSection section : ReportSection.values()) {
            assertTrue(text.contains("== " + section.getTitle() + " =="), section.getTitle());
        }
        assertTrue(text.contains("== Conclusión =="));
    }

    @Test
    void namesEachRecommendationWithItsAlternative() {
        WhatIfReport whatIf = ReportFactsTest.whatIf();
        ReportNarrative narrative = TemplateNarrator.write(ReportFactsTest.report(), whatIf, "Sin clave");

        String text = NarrativePreview.render(narrative, whatIf);

        assertFalse(whatIf.recommendations().isEmpty());
        assertTrue(text.contains("1. " + whatIf.recommendations().get(0).label() + ": evita"));
    }

    @Test
    void needsTheText() {
        assertThrows(IllegalArgumentException.class, () -> NarrativePreview.render(null, null));
    }
}
