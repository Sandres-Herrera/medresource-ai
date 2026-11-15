package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del texto del reporte.
 */
class ReportNarrativeTest {

    private static Map<ReportSection, String> allSections() {
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        for (ReportSection section : ReportSection.values()) {
            sections.put(section, "Texto de " + section.getTitle() + ".");
        }
        return sections;
    }

    @Test
    void findsTheTextOfASection() {
        ReportNarrative narrative = new ReportNarrative(NarrativeSource.AI, "Resumen.",
                Map.of(ReportSection.BLOOD, "No faltó sangre."), List.of("Un quirófano más."), "Conclusión.", null);

        assertEquals("No faltó sangre.", narrative.sectionText(ReportSection.BLOOD).orElseThrow());
        assertTrue(narrative.sectionText(ReportSection.STAFF).isEmpty());
        assertFalse(narrative.isComplete());
    }

    @Test
    void withEverySectionItIsComplete() {
        ReportNarrative narrative = new ReportNarrative(NarrativeSource.TEMPLATE, "Resumen.", allSections(),
                List.of(), "Conclusión.", "No hay clave de la IA.");

        assertTrue(narrative.isComplete());
    }

    @Test
    void onlyATemplateTextSaysWhyItWasUsed() {
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrative(NarrativeSource.AI, "Resumen.",
                Map.of(), List.of(), "Conclusión.", "Nota."));
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrative(NarrativeSource.TEMPLATE,
                "Resumen.", Map.of(), List.of(), "Conclusión.", null));
    }

    @Test
    void rejectsEmptyTexts() {
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrative(NarrativeSource.AI, " ", Map.of(),
                List.of(), "Conclusión.", null));
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrative(NarrativeSource.AI, "Resumen.",
                Map.of(ReportSection.BLOOD, ""), List.of(), "Conclusión.", null));
        assertThrows(IllegalArgumentException.class, () -> new ReportNarrative(NarrativeSource.AI, "Resumen.",
                Map.of(), List.of(" "), "Conclusión.", null));
    }
}
