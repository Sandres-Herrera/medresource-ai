package com.medresource.services.management;

import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.SimulationReport;
import com.medresource.model.WhatIfReport;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la redacción con plantillas.
 */
class TemplateNarratorTest {

    @Test
    void writesEverySectionAndSaysWhy() {
        ReportNarrative narrative = TemplateNarrator.write(ReportFactsTest.report(), null, "No hay clave de la IA.");

        assertEquals(NarrativeSource.TEMPLATE, narrative.source());
        assertTrue(narrative.isComplete());
        assertEquals("No hay clave de la IA.", narrative.note());
    }

    @Test
    void theSummaryCarriesTheNumbersOfTheReport() {
        SimulationReport report = ReportFactsTest.report();
        ReportNarrative narrative = TemplateNarrator.write(report, null, "Sin IA.");

        assertTrue(narrative.summary().contains("fallecieron " + report.metrics().deaths()));
        assertTrue(narrative.summary().contains("llegaron " + report.config().patientCount() + " pacientes"));
    }

    @Test
    void withoutTheWhatIfAnalysisThereAreNoRecommendations() {
        ReportNarrative narrative = TemplateNarrator.write(ReportFactsTest.report(), null, "Sin IA.");

        assertTrue(narrative.recommendations().isEmpty());
        assertEquals("No se hizo el análisis «¿y si…?».",
                narrative.sectionText(ReportSection.MISSING_RESOURCES).orElseThrow());
    }

    @Test
    void explainsEachRecommendationOfTheWhatIfAnalysis() {
        WhatIfReport whatIf = ReportFactsTest.whatIf();
        ReportNarrative narrative = TemplateNarrator.write(ReportFactsTest.report(), whatIf, "Sin IA.");

        assertEquals(whatIf.recommendations().size(), narrative.recommendations().size());
        narrative.recommendations().forEach(text -> assertTrue(text.startsWith("evita ")));
    }

    @Test
    void theTemplatesNeverWriteNumbersThatAreNotInTheFacts() {
        SimulationReport report = ReportFactsTest.report();
        WhatIfReport whatIf = ReportFactsTest.whatIf();
        ReportNarrative narrative = TemplateNarrator.write(report, whatIf, "Sin IA.");
        Set<String> allowed = ReportFacts.allowedNumbers(ReportFacts.of(report, whatIf));

        assertTrue(NarrativeValidator.isVerified(narrative.summary(), allowed), narrative.summary());
        narrative.sections().forEach((section, text) ->
                assertTrue(NarrativeValidator.isVerified(text, allowed), section + ": " + text));
    }

    @Test
    void needsTheReportAndTheNote() {
        assertThrows(IllegalArgumentException.class, () -> TemplateNarrator.write(null, null, "Sin IA."));
        assertThrows(IllegalArgumentException.class,
                () -> TemplateNarrator.write(ReportFactsTest.report(), null, " "));
    }
}
