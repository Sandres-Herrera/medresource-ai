package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las secciones del reporte y de quién las redacta.
 */
class ReportSectionTest {

    @Test
    void everySectionHasItsOwnKeyWithoutAccentsOrSpaces() {
        Set<String> keys = new HashSet<>();
        for (ReportSection section : ReportSection.values()) {
            assertTrue(section.getKey().matches("[a-z_]+"), section.getKey());
            keys.add(section.getKey());
        }
        assertEquals(ReportSection.values().length, keys.size());
    }

    @Test
    void everySectionHasATitle() {
        assertEquals("Cuellos de botella", ReportSection.BOTTLENECKS.getTitle());
        assertEquals("recursos_que_faltaron", ReportSection.MISSING_RESOURCES.getKey());
    }

    @Test
    void theReportSaysWhoWroteIt() {
        assertEquals("Redactado por IA", NarrativeSource.AI.getLabel());
        assertEquals("Generado automáticamente", NarrativeSource.TEMPLATE.getLabel());
    }
}
