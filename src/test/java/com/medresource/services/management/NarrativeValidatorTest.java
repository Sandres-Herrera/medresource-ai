package com.medresource.services.management;

import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la revisión de las cifras del texto de la IA.
 */
class NarrativeValidatorTest {

    /** Cifras de unos hechos de ejemplo. */
    private static final Set<String> ALLOWED = Set.of("13", "45.9", "4008", "00:16", "06:39", "0.7");

    private static ReportNarrative narrative(String summary, Map<ReportSection, String> sections,
                                             List<String> recommendations, String conclusion) {
        return new ReportNarrative(NarrativeSource.AI, summary, sections, recommendations, conclusion, null);
    }

    @Test
    void acceptsTheNumbersAndTimesOfTheFactsWrittenWithCommaOrDot() {
        assertTrue(NarrativeValidator.isVerified("Murieron 13 pacientes (13,0 %).", ALLOWED));
        assertTrue(NarrativeValidator.isVerified("El médico causó el 45,9 % de la espera.", ALLOWED));
        assertTrue(NarrativeValidator.isVerified("Colapsó de 00:16 a 06:39.", ALLOWED));
        assertTrue(NarrativeValidator.isVerified("Evita 0.7 muertes por cada 100 pacientes.", ALLOWED));
    }

    @Test
    void acceptsCodesAndSmallCounts() {
        assertTrue(NarrativeValidator.isVerified("El paciente P024 esperó; D03 y 2 enfermeros lo atendieron.",
                ALLOWED));
    }

    @Test
    void rejectsInventedNumbersAndTimes() {
        assertFalse(NarrativeValidator.isVerified("Murieron 14 pacientes.", ALLOWED));
        assertFalse(NarrativeValidator.isVerified("El médico causó el 46 % de la espera.", ALLOWED));
        assertFalse(NarrativeValidator.isVerified("Colapsó a las 02:00.", ALLOWED));
    }

    @Test
    void removesOnlyTheSentencesWithInventedNumbers() {
        ReportNarrative cleaned = NarrativeValidator.clean(narrative(
                "Murieron 13 pacientes. Eso es un 99 % más. Faltaron cirujanos.",
                Map.of(ReportSection.BLOOD, "Se usaron 77 unidades.", ReportSection.TIMELINE, "Colapsó a las 00:16."),
                List.of("Un quirófano más."), "Hacen falta cirujanos."), ALLOWED).orElseThrow();

        assertEquals("Murieron 13 pacientes. Faltaron cirujanos.", cleaned.summary());
        assertTrue(cleaned.sectionText(ReportSection.BLOOD).isEmpty());
        assertEquals("Colapsó a las 00:16.", cleaned.sectionText(ReportSection.TIMELINE).orElseThrow());
        assertEquals(List.of("Un quirófano más."), cleaned.recommendations());
    }

    @Test
    void aRecommendationWithInventedNumbersDropsThemAllToKeepTheOrder() {
        ReportNarrative cleaned = NarrativeValidator.clean(narrative("Resumen.", Map.of(),
                List.of("Un quirófano más.", "Ahorra 50 vidas."), "Conclusión."), ALLOWED).orElseThrow();

        assertTrue(cleaned.recommendations().isEmpty());
    }

    @Test
    void withoutASummaryOrAConclusionTheTextDoesNotServe() {
        assertTrue(NarrativeValidator.clean(narrative("Murieron 99.", Map.of(), List.of(), "Conclusión."), ALLOWED)
                .isEmpty());
        assertTrue(NarrativeValidator.clean(narrative("Resumen.", Map.of(), List.of(), "Son 99."), ALLOWED)
                .isEmpty());
    }

    @Test
    void needsTheTextAndTheNumbers() {
        assertThrows(IllegalArgumentException.class, () -> NarrativeValidator.clean(null, ALLOWED));
    }
}
