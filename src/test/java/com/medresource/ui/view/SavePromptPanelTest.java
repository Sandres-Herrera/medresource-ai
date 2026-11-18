package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.GeneratedReportFixtures;
import com.medresource.model.SavedReport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la franja que pregunta si se guarda la simulación, sin ventana ni base de datos.
 */
class SavePromptPanelTest {

    private static final GeneratedReport REPORT = GeneratedReportFixtures.small();
    private static final SavedReport SAVED = new SavedReport(4, 12, LocalDateTime.of(2026, 11, 17, 17, 30), REPORT);

    private final AtomicInteger saves = new AtomicInteger();
    private final List<Runnable> pending = new ArrayList<>();
    private final ReportSaving saving = new ReportSaving(report -> {
        saves.incrementAndGet();
        return Optional.of(SAVED);
    }, (task, done, failed) -> pending.add(() -> done.accept(task.get())));
    private final SavePromptPanel panel = new SavePromptPanel(saving);

    @Test
    void staysHiddenWhileTheReportIsGenerated() {
        assertFalse(panel.isVisible());

        saving.reportReady(REPORT);

        assertTrue(panel.isVisible());
    }

    @Test
    void asksWhetherToSaveWithBothOptions() {
        saving.reportReady(REPORT);

        assertEquals(SavePromptPanel.QUESTION, panel.titleText());
        assertEquals(SavePromptPanel.QUESTION_DETAIL, panel.detailText());
        assertTrue(panel.saveButton().isVisible());
        assertTrue(panel.skipButton().isVisible());
        assertEquals("Guardar simulación", panel.saveButton().getText());
        assertEquals("Continuar sin guardar", panel.skipButton().getText());
    }

    @Test
    void continuingWithoutSavingLeavesTheSaveButtonForLater() {
        saving.reportReady(REPORT);

        panel.skipButton().doClick();

        assertEquals(SavePromptPanel.SKIPPED_TEXT, panel.titleText());
        assertFalse(panel.skipButton().isVisible());
        assertTrue(panel.saveButton().isVisible());
        assertEquals(0, saves.get());
    }

    @Test
    void whileSavingThereIsNoButtonToStartAnotherSave() {
        saving.reportReady(REPORT);

        panel.saveButton().doClick();

        assertEquals(SavePromptPanel.SAVING_TEXT, panel.titleText());
        assertFalse(panel.saveButton().isVisible());
        assertFalse(panel.skipButton().isVisible());

        pending.forEach(Runnable::run);

        assertEquals("Simulación guardada en el historial como la ejecución #12.", panel.titleText());
        assertEquals(HomeTheme.ACCENT, panel.titleColor());
        assertFalse(panel.saveButton().isVisible());
        assertEquals(1, saves.get());
    }

    @Test
    void aReopenedReportShowsTheDecisionAlreadyTaken() {
        saving.reportReady(REPORT);
        saving.save();
        panel.detach();

        SavePromptPanel reopened = new SavePromptPanel(saving);

        assertEquals(SavePromptPanel.SAVING_TEXT, reopened.titleText());
        assertFalse(reopened.saveButton().isVisible());
        pending.forEach(Runnable::run);
        assertTrue(reopened.titleText().startsWith("Simulación guardada"));
        assertEquals(SavePromptPanel.SAVING_TEXT, panel.titleText());
    }

    @Test
    void aFailedSaveSaysWhyAndLetsTryAgain() {
        ReportSaving failing = new ReportSaving(report -> {
            throw new IllegalStateException("Sin conexión con Oracle.");
        }, (task, done, failed) -> {
            try {
                done.accept(task.get());
            } catch (RuntimeException e) {
                failed.accept(e);
            }
        });
        SavePromptPanel failed = new SavePromptPanel(failing);
        failing.reportReady(REPORT);

        failed.saveButton().doClick();

        assertEquals(SavePromptPanel.ERROR_TEXT + "Sin conexión con Oracle.", failed.titleText());
        assertEquals(HomeTheme.DANGER, failed.titleColor());
        assertTrue(failed.saveButton().isVisible());
    }

    @Test
    void withoutADatabaseItSaysSoAndHasNoButtons() {
        ReportSaving noDatabase = new ReportSaving(report -> Optional.empty(),
                (task, done, failed) -> done.accept(task.get()));
        SavePromptPanel prompt = new SavePromptPanel(noDatabase);
        noDatabase.reportReady(REPORT);

        prompt.saveButton().doClick();

        assertEquals(SavePromptPanel.NO_DATABASE_TEXT, prompt.titleText());
        assertFalse(prompt.saveButton().isVisible());
        assertFalse(prompt.skipButton().isVisible());
    }

    @Test
    void needsADecision() {
        assertThrows(IllegalArgumentException.class, () -> new SavePromptPanel(null));
    }
}
