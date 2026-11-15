package com.medresource.services.management;

import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.SimulationReport;
import com.medresource.model.WhatIfReport;
import com.medresource.persistence.AiClient;
import com.medresource.persistence.AiClientException;
import com.medresource.persistence.NarrativeCache;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del redactor del reporte con una IA falsa: sin internet ni clave.
 */
class ReportNarratorTest {

    private static final SimulationReport REPORT = ReportFactsTest.report();
    private static final WhatIfReport WHAT_IF = ReportFactsTest.whatIf();

    /** IA falsa que cuenta sus llamadas y responde lo que se le indique. */
    private static final class FakeAi implements AiClient {
        private final Supplier<String> answer;
        private int calls;

        FakeAi(Supplier<String> answer) {
            this.answer = answer;
        }

        @Override
        public String generate(String prompt) {
            calls++;
            return answer.get();
        }

        @Override
        public String modelName() {
            return "modelo-falso";
        }
    }

    private static NarrativeCache newCache() throws IOException {
        return new NarrativeCache(Files.createTempDirectory("narrator-test").resolve("cache"));
    }

    /** Respuesta válida: solo usa cifras del reporte y explica cada recomendación. */
    private static String goodAnswer(int recommendations) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < recommendations; i++) {
            texts.add("\"Ayuda con la espera.\"");
        }
        return """
                {"resumen": "Fallecieron %d pacientes.",
                 "secciones": {"cuellos_de_botella": "Faltaron médicos compatibles."},
                 "recomendaciones": [%s],
                 "conclusion": "Hay que reforzar el personal."}
                """.formatted(REPORT.metrics().deaths(), String.join(",", texts));
    }

    @Test
    void withoutAiItUsesTheTemplatesAndSaysWhy() {
        ReportNarrator narrator = new ReportNarrator(null, null);
        ReportNarrative narrative = narrator.narrate(REPORT, WHAT_IF);

        assertFalse(narrator.usesAi());
        assertEquals(NarrativeSource.TEMPLATE, narrative.source());
        assertEquals(ReportNarrator.NO_KEY_NOTE, narrative.note());
        assertEquals(0, narrator.estimatedMillis(REPORT, WHAT_IF));
    }

    @Test
    void usesTheTextOfTheAiAndCompletesTheMissingSections() throws IOException {
        int recommendations = WHAT_IF.recommendations().size();
        ReportNarrative narrative = new ReportNarrator(new FakeAi(() -> goodAnswer(recommendations)), newCache())
                .narrate(REPORT, WHAT_IF);

        assertEquals(NarrativeSource.AI, narrative.source());
        assertEquals("Fallecieron " + REPORT.metrics().deaths() + " pacientes.", narrative.summary());
        assertEquals("Faltaron médicos compatibles.",
                narrative.sectionText(ReportSection.BOTTLENECKS).orElseThrow());
        assertTrue(narrative.isComplete());
        assertEquals(recommendations, narrative.recommendations().size());
    }

    @Test
    void theSecondTimeTheAnswerComesFromTheCache() throws IOException {
        FakeAi ai = new FakeAi(() -> goodAnswer(WHAT_IF.recommendations().size()));
        NarrativeCache cache = newCache();
        ReportNarrator narrator = new ReportNarrator(ai, cache);

        assertEquals(NarrativeCache.DEFAULT_CALL_MILLIS, narrator.estimatedMillis(REPORT, WHAT_IF));
        narrator.narrate(REPORT, WHAT_IF);
        ReportNarrative again = narrator.narrate(REPORT, WHAT_IF);

        assertEquals(1, ai.calls);
        assertEquals(NarrativeSource.AI, again.source());
        assertEquals(0, narrator.estimatedMillis(REPORT, WHAT_IF));
    }

    @Test
    void tellsHowLongTheAiUsuallyTakes() throws IOException {
        NarrativeCache cache = newCache();
        ReportNarrator narrator = new ReportNarrator(new FakeAi(() -> goodAnswer(0)), cache);

        assertEquals(0, new ReportNarrator(null, null).typicalMillis());
        assertEquals(NarrativeCache.DEFAULT_CALL_MILLIS, narrator.typicalMillis());
        assertEquals(NarrativeCache.DEFAULT_CALL_MILLIS,
                new ReportNarrator(new FakeAi(() -> goodAnswer(0)), null).typicalMillis());

        cache.recordCallMillis(3000);
        cache.recordCallMillis(5000);

        assertEquals(4000, narrator.typicalMillis());
    }

    @Test
    void anErrorOfTheAiUsesTheTemplatesWithTheReason() throws IOException {
        FakeAi ai = new FakeAi(() -> {
            throw new AiClientException(AiClientException.Reason.QUOTA, null);
        });
        ReportNarrative narrative = new ReportNarrator(ai, newCache()).narrate(REPORT, WHAT_IF);

        assertEquals(NarrativeSource.TEMPLATE, narrative.source());
        assertTrue(narrative.note().contains("cuota"));
    }

    @Test
    void anAnswerWithoutTheFormatOrWithInventedNumbersUsesTheTemplatesAndIsNotKept() throws IOException {
        NarrativeCache cache = newCache();
        FakeAi badFormat = new FakeAi(() -> "Aquí está su reporte.");
        FakeAi invented = new FakeAi(() -> "{\"resumen\": \"Fallecieron 987 pacientes.\", \"conclusion\": \"Mal.\"}");

        assertEquals(ReportNarrator.BAD_FORMAT_NOTE, new ReportNarrator(badFormat, cache).narrate(REPORT, null).note());
        ReportNarrator withInvented = new ReportNarrator(invented, cache);
        assertEquals(ReportNarrator.INVENTED_NUMBERS_NOTE, withInvented.narrate(REPORT, null).note());
        withInvented.narrate(REPORT, null);
        assertEquals(2, invented.calls);
    }

    @Test
    void ifTheExplanationsDoNotMatchTheRecommendationsItUsesTheTemplateOnes() throws IOException {
        ReportNarrative narrative = new ReportNarrator(new FakeAi(() -> goodAnswer(WHAT_IF.recommendations().size() + 1)),
                newCache()).narrate(REPORT, WHAT_IF);

        assertEquals(TemplateNarrator.recommendations(WHAT_IF), narrative.recommendations());
    }

    @Test
    void needsTheReport() {
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrator(null, null).narrate(null, null));
    }
}
