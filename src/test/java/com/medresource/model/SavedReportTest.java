package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del reporte guardado.
 */
class SavedReportTest {

    private static final LocalDateTime SAVED_AT = LocalDateTime.of(2026, 11, 15, 14, 5);

    @Test
    void aReportToSaveHasNoIdentifierNorDate() {
        GeneratedReport report = GeneratedReportFixtures.small();

        SavedReport toSave = SavedReport.toSave(8, report);

        assertEquals(0, toSave.id());
        assertEquals(8, toSave.resultId());
        assertNull(toSave.savedAt());
        assertSame(report, toSave.report());
        assertFalse(toSave.isSaved());
    }

    @Test
    void aSavedReportHasItsIdentifierAndDate() {
        SavedReport saved = new SavedReport(3, 8, SAVED_AT, GeneratedReportFixtures.small());

        assertTrue(saved.isSaved());
        assertEquals(SAVED_AT, saved.savedAt());
    }

    @Test
    void wrongDataIsRejected() {
        GeneratedReport report = GeneratedReportFixtures.small();

        assertThrows(IllegalArgumentException.class, () -> SavedReport.toSave(0, report));
        assertThrows(IllegalArgumentException.class, () -> SavedReport.toSave(8, null));
        assertThrows(IllegalArgumentException.class, () -> new SavedReport(-1, 8, null, report));
        assertThrows(IllegalArgumentException.class, () -> new SavedReport(3, 8, null, report));
        assertThrows(IllegalArgumentException.class, () -> new SavedReport(0, 8, SAVED_AT, report));
    }
}
