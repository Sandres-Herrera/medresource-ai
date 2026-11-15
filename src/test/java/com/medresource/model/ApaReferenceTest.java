package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las referencias fijas del reporte.
 */
class ApaReferenceTest {

    @Test
    void withTheTemplatesGeminiIsNotCited() {
        assertEquals(List.of(ApaReference.APA_MANUAL, ApaReference.SIMULATOR, ApaReference.TRIAGE_RESOLUTION),
                ApaReference.forReport(NarrativeSource.TEMPLATE));
    }

    @Test
    void withTheAiGeminiIsCitedInAlphabeticalOrder() {
        assertEquals(List.of(ApaReference.APA_MANUAL, ApaReference.GEMINI, ApaReference.SIMULATOR,
                ApaReference.TRIAGE_RESOLUTION), ApaReference.forReport(NarrativeSource.AI));
    }

    @Test
    void theReferenceIsWrittenInItsParts() {
        assertEquals("(Herrera y Gámez, 2026)", ApaReference.SIMULATOR.parenthetical());
        assertEquals("Herrera, S. y Gámez, O. (2026). MedResource AI: simulador de recursos hospitalarios [Software]. "
                + "GitHub. https://github.com/Sandres-Herrera/medresource-ai", ApaReference.SIMULATOR.text());
        assertTrue(ApaReference.TRIAGE_RESOLUTION.title().startsWith("Resolución 5596 de 2015"));
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ApaReference.forReport(null));
        assertThrows(IllegalArgumentException.class, () -> new ApaReference(" ", "Autor. (2020). ", "Título", ""));
        assertThrows(IllegalArgumentException.class, () -> new ApaReference("Autor, 2020", "Autor. (2020). ", "",
                ""));
        assertThrows(IllegalArgumentException.class, () -> new ApaReference("Autor, 2020", "Autor. (2020). ",
                "Título", null));
    }
}
