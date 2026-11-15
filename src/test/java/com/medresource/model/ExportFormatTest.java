package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los formatos para descargar el reporte.
 */
class ExportFormatTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

    @Test
    void thereAreWordAndPdf() {
        assertEquals("docx", ExportFormat.WORD.getExtension());
        assertEquals("pdf", ExportFormat.PDF.getExtension());
        for (ExportFormat format : ExportFormat.values()) {
            assertFalse(format.getLabel().isBlank(), format.name());
            assertFalse(format.getDetail().isBlank(), format.name());
        }
    }

    @Test
    void theFileNameHasTheScenarioTheSeedAndTheDate() {
        assertEquals("Reporte_catastrofe_semilla7_2026-11-15.docx",
                ExportFormat.WORD.fileName(Scenario.CATASTROPHE, 7, DATE));
        assertEquals("Reporte_emergencias_inesperadas_semilla42_2026-11-15.pdf",
                ExportFormat.PDF.fileName(Scenario.UNEXPECTED_EMERGENCIES, 42, DATE));
    }

    @Test
    void theExtensionIsAddedOnlyIfItIsMissing() {
        assertEquals("reporte.pdf", ExportFormat.PDF.withExtension("reporte"));
        assertEquals("reporte.PDF", ExportFormat.PDF.withExtension("reporte.PDF"));
        assertEquals("reporte.pdf.docx", ExportFormat.WORD.withExtension("reporte.pdf"));
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ExportFormat.PDF.fileName(null, 7, DATE));
        assertThrows(IllegalArgumentException.class, () -> ExportFormat.PDF.fileName(Scenario.NORMAL, 7, null));
        assertThrows(IllegalArgumentException.class, () -> ExportFormat.PDF.withExtension(" "));
    }
}
