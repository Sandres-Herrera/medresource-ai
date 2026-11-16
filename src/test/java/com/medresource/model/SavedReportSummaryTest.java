package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del resumen de un reporte guardado.
 */
class SavedReportSummaryTest {

    private static final LocalDateTime SAVED_AT = LocalDateTime.of(2026, 11, 15, 14, 5);
    private static final CaseConfig CASE = CaseConfig.of(7L, Scenario.CATASTROPHE);

    @Test
    void keepsTheDataOfTheListAndCalculatesTheMortality() {
        SavedReportSummary summary = new SavedReportSummary(3, 8, SAVED_AT, CASE, "Prioridad clínica (triage)", 13,
                NarrativeSource.TEMPLATE, 2, null);

        assertEquals(CASE, summary.config());
        assertEquals(13, summary.deaths());
        assertEquals(13.0 / CASE.patientCount(), summary.mortalityRate(), 1e-9);
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(0, 8, SAVED_AT, CASE, "FIFO", 1, NarrativeSource.AI, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 0, SAVED_AT, CASE, "FIFO", 1, NarrativeSource.AI, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, null, CASE, "FIFO", 1, NarrativeSource.AI, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, SAVED_AT, CASE, " ", 1, NarrativeSource.AI, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, SAVED_AT, CASE, "FIFO", -1, NarrativeSource.AI, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, SAVED_AT, CASE, "FIFO", CASE.patientCount() + 1,
                        NarrativeSource.AI, 2, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, SAVED_AT, CASE, "FIFO", 1, null, 2, null));
    }

    @Test
    void remembersTheCaseAndTheRunItIsBasedOn() {
        SavedReportSummary fresh = new SavedReportSummary(3, 8, SAVED_AT, CASE, "FIFO", 1, NarrativeSource.AI, 2,
                null);
        SavedReportSummary rerun = new SavedReportSummary(4, 9, SAVED_AT, CASE, "FIFO", 1, NarrativeSource.AI, 2, 8);

        assertEquals(2, fresh.caseId());
        assertEquals(Optional.empty(), fresh.basedOn());
        assertEquals(Optional.of(8), rerun.basedOn());
    }

    @Test
    void rejectsAMissingCaseOrABaseRunThatIsNotAnotherRun() {
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, SAVED_AT, CASE, "FIFO", 1, NarrativeSource.AI, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, SAVED_AT, CASE, "FIFO", 1, NarrativeSource.AI, 2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SavedReportSummary(3, 8, SAVED_AT, CASE, "FIFO", 1, NarrativeSource.AI, 2, 8));
    }
}
