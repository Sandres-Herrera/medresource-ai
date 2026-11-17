package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.NarrativeSource;
import com.medresource.model.SavedReportSummary;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la lista de reportes guardados.
 */
class SavedReportsPanelTest {

    private static final SavedReportSummary CATASTROPHE = new SavedReportSummary(4, 9,
            LocalDateTime.of(2026, 11, 15, 14, 5), new CaseConfig(7L, Scenario.CATASTROPHE, 100, 480),
            "Prioridad clínica (triage)", 13, NarrativeSource.TEMPLATE, 3, null);
    private static final SavedReportSummary NORMAL = new SavedReportSummary(2, 5,
            LocalDateTime.of(2026, 11, 14, 9, 30), CaseConfig.of(42L, Scenario.NORMAL), "FIFO", 0, NarrativeSource.AI,
            1, null);

    @Test
    void startsSearchingForTheReports() {
        SavedReportsPanel panel = new SavedReportsPanel();

        assertEquals(SavedReportsPanel.LOADING_TEXT, panel.messageText());
        assertEquals(0, panel.rowCount());
    }

    @Test
    void eachReportShowsItsCaseItsResultAndWhoWroteIt() {
        SavedReportsPanel panel = new SavedReportsPanel();

        panel.showReports(List.of(CATASTROPHE, NORMAL));

        assertEquals("", panel.messageText());
        assertEquals(2, panel.rowCount());
        assertEquals(List.of("#9", "#3", "15/11/2026 14:05", Scenario.CATASTROPHE.getDisplayName(), "7", "100",
                "13 (13 %)", "Prioridad clínica (triage)", NarrativeSource.TEMPLATE.getLabel()), panel.rowTexts(0));
        assertEquals(List.of("#5", "#1", "14/11/2026 09:30", Scenario.NORMAL.getDisplayName(), "42", "100",
                "0 (0 %)", "FIFO", NarrativeSource.AI.getLabel()), panel.rowTexts(1));
        assertEquals(SavedReportsPanel.COLUMNS.size(), panel.rowTexts(0).size());
    }

    @Test
    void openingAReportGivesItsId() {
        SavedReportsPanel panel = new SavedReportsPanel();
        List<Integer> opened = new ArrayList<>();
        panel.onOpen(opened::add);
        panel.showReports(List.of(CATASTROPHE, NORMAL));

        panel.openButton(1).doClick();
        panel.openButton(0).doClick();

        assertEquals(List.of(2, 4), opened);
        assertEquals(SavedReportsPanel.OPEN, panel.openButton(0).getText());
    }

    @Test
    void deletingAReportGivesTheWholeRowAndDoesNotDeleteItByItself() {
        SavedReportsPanel panel = new SavedReportsPanel();
        List<SavedReportSummary> deleted = new ArrayList<>();
        panel.onDelete(deleted::add);
        panel.showReports(List.of(CATASTROPHE, NORMAL));

        panel.deleteButton(1).doClick();

        assertEquals(List.of(NORMAL), deleted);
        assertEquals(2, panel.rowCount());
        assertEquals(SavedReportsPanel.DELETE, panel.deleteButton(0).getText());
    }

    @Test
    void openingAndDeletingAreDifferentButtons() {
        SavedReportsPanel panel = new SavedReportsPanel();
        List<Integer> opened = new ArrayList<>();
        List<SavedReportSummary> deleted = new ArrayList<>();
        panel.onOpen(opened::add);
        panel.onDelete(deleted::add);
        panel.showReports(List.of(CATASTROPHE));

        panel.openButton(0).doClick();

        assertEquals(List.of(4), opened);
        assertEquals(List.of(), deleted);
    }

    @Test
    void theQuestionBeforeDeletingSaysWhatElseIsDeleted() {
        String question = SavedReportsPanel.deleteQuestion(CATASTROPHE);

        assertTrue(question.startsWith("¿Eliminar el reporte de la ejecución #9 ("
                + Scenario.CATASTROPHE.getDisplayName() + ", semilla 7)?"), question);
        assertTrue(question.contains("resultado de cada paciente"), question);
        assertTrue(question.contains("comparaciones"), question);
        assertTrue(question.contains("se conservan"), question);
        assertTrue(question.endsWith("Esto no se puede deshacer."), question);
    }

    @Test
    void whileDeletingAMessageIsShownInsteadOfTheList() {
        SavedReportsPanel panel = new SavedReportsPanel();
        panel.showReports(List.of(CATASTROPHE, NORMAL));

        panel.showDeleting(CATASTROPHE);

        assertEquals("Eliminando el reporte de la ejecución #9…", panel.messageText());
        assertEquals(HomeTheme.MUTED, panel.messageColor());
        assertEquals(0, panel.rowCount());
    }

    @Test
    void withoutReportsOrDatabaseOrWithAnErrorAMessageIsShown() {
        SavedReportsPanel panel = new SavedReportsPanel();
        panel.showReports(List.of(CATASTROPHE));

        panel.showReports(List.of());
        assertEquals(SavedReportsPanel.EMPTY_TEXT, panel.messageText());
        assertEquals(0, panel.rowCount());

        panel.showNotice("Este equipo no tiene base de datos.");
        assertEquals("Este equipo no tiene base de datos.", panel.messageText());
        assertEquals(HomeTheme.MUTED, panel.messageColor());

        panel.showError("No se pudo conectar con Oracle.");
        assertEquals("No se pudo conectar con Oracle.", panel.messageText());
        assertEquals(HomeTheme.DANGER, panel.messageColor());
    }

    @Test
    void wrongDataIsRejected() {
        SavedReportsPanel panel = new SavedReportsPanel();

        assertThrows(IllegalArgumentException.class, () -> panel.showReports(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onOpen(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onDelete(null));
        assertThrows(IllegalArgumentException.class, () -> panel.showDeleting(null));
        assertThrows(IllegalArgumentException.class, () -> SavedReportsPanel.deleteQuestion(null));
    }

    @Test
    void aRunBasedOnAnotherSaysWhichOne() {
        SavedReportSummary rerun = new SavedReportSummary(6, 12, LocalDateTime.of(2026, 11, 16, 10, 0),
                new CaseConfig(7L, Scenario.CATASTROPHE, 100, 480), "FIFO (orden de llegada)", 9,
                NarrativeSource.AI, 3, 9);

        SavedReportsPanel panel = new SavedReportsPanel();
        panel.showReports(List.of(rerun, CATASTROPHE));

        assertEquals("#12 · de #9", panel.rowTexts(0).get(0));
        assertEquals("#9", panel.rowTexts(1).get(0));
        assertEquals("#3", panel.rowTexts(0).get(1));
    }
}
