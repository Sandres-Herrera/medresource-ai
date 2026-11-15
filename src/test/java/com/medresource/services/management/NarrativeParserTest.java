package com.medresource.services.management;

import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la lectura de la respuesta de la IA.
 */
class NarrativeParserTest {

    private static final String ANSWER = """
            {"resumen": "Murieron 13 pacientes.",
             "secciones": {"sangre": "No faltó sangre.", "cuellos_de_botella": "Faltó un cirujano.",
                           "inventada": "No existe.", "personal": "  "},
             "recomendaciones": ["Un quirófano más ayuda.", "", "Un cirujano más ayuda."],
             "conclusion": "El hospital necesita cirujanos."}
            """;

    @Test
    void readsTheSummaryTheSectionsTheRecommendationsAndTheConclusion() {
        ReportNarrative narrative = NarrativeParser.parse(ANSWER).orElseThrow();

        assertEquals(NarrativeSource.AI, narrative.source());
        assertEquals("Murieron 13 pacientes.", narrative.summary());
        assertEquals("No faltó sangre.", narrative.sectionText(ReportSection.BLOOD).orElseThrow());
        assertEquals("Faltó un cirujano.", narrative.sectionText(ReportSection.BOTTLENECKS).orElseThrow());
        assertEquals(List.of("Un quirófano más ayuda.", "Un cirujano más ayuda."), narrative.recommendations());
        assertEquals("El hospital necesita cirujanos.", narrative.conclusion());
    }

    @Test
    void ignoresUnknownAndEmptySections() {
        ReportNarrative narrative = NarrativeParser.parse(ANSWER).orElseThrow();

        assertEquals(2, narrative.sections().size());
        assertTrue(narrative.sectionText(ReportSection.STAFF).isEmpty());
    }

    @Test
    void acceptsTheAnswerInsideACodeBlock() {
        assertTrue(NarrativeParser.parse("```json\n" + ANSWER + "\n```").isPresent());
        assertEquals("{\"a\":1}", NarrativeParser.withoutCodeFence("```\n{\"a\":1}\n```"));
    }

    @Test
    void anAnswerWithoutTheRequiredTextsDoesNotServe() {
        assertTrue(NarrativeParser.parse("{\"resumen\": \"Solo resumen.\"}").isEmpty());
        assertTrue(NarrativeParser.parse("No es JSON").isEmpty());
        assertTrue(NarrativeParser.parse("[1, 2]").isEmpty());
        assertTrue(NarrativeParser.parse(" ").isEmpty());
        assertTrue(NarrativeParser.parse(null).isEmpty());
    }
}
