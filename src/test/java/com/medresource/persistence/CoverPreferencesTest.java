package com.medresource.persistence;

import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del recordatorio de la portada, en un nodo de preferencias propio que se borra al terminar.
 */
class CoverPreferencesTest {

    private static final DocumentInfo DRAFT = DocumentInfo.draft("Reporte nuevo", LocalDate.of(2026, 11, 15));

    /** Prueba que recibe un nodo propio, que se borra al terminar aunque la prueba falle. */
    private interface WithNode {
        void run(Preferences node, CoverPreferences memory);
    }

    private static void withNode(WithNode test) throws BackingStoreException {
        Preferences node = Preferences.userRoot().node("medresource-ai-prueba-" + UUID.randomUUID());
        try {
            test.run(node, new CoverPreferences(node));
        } finally {
            node.removeNode();
        }
    }

    @Test
    void withNothingSavedTheDraftStaysTheSame() throws BackingStoreException {
        withNode((node, memory) -> {
            assertEquals(DRAFT, memory.apply(DRAFT));
            assertEquals(ExportFormat.PDF, memory.format(ExportFormat.PDF));
        });
    }

    @Test
    void theNextReportUsesWhatWasSavedButKeepsItsTitleAndDate() throws BackingStoreException {
        DocumentInfo written = new DocumentInfo("Reporte anterior", List.of("Ana Pérez", "Luis Díaz"), "Universidad",
                "Sistemas", "Programación III", "Docente Uno", LocalDate.of(2026, 11, 1));

        withNode((node, memory) -> {
            memory.save(written, ExportFormat.WORD);
            DocumentInfo next = memory.apply(DRAFT);

            assertEquals("Reporte nuevo", next.title());
            assertEquals(DRAFT.date(), next.date());
            assertEquals(List.of("Ana Pérez", "Luis Díaz"), next.authors());
            assertEquals("Universidad", next.institution());
            assertEquals("Sistemas", next.program());
            assertEquals("Programación III", next.course());
            assertEquals("Docente Uno", next.teacher());
            assertEquals(ExportFormat.WORD, memory.format(ExportFormat.PDF));
        });
    }

    @Test
    void anUnknownSavedFormatUsesTheFallback() throws BackingStoreException {
        withNode((node, memory) -> {
            node.put("formato", "EXCEL");

            assertEquals(ExportFormat.PDF, memory.format(ExportFormat.PDF));
        });
    }

    @Test
    void wrongDataIsRejected() throws BackingStoreException {
        assertThrows(IllegalArgumentException.class, () -> new CoverPreferences(null));
        withNode((node, memory) -> {
            assertThrows(IllegalArgumentException.class, () -> memory.apply(null));
            assertThrows(IllegalArgumentException.class, () -> memory.save(null, ExportFormat.PDF));
            assertThrows(IllegalArgumentException.class, () -> memory.save(DRAFT, null));
        });
    }
}
