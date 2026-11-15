package com.medresource.ui.view;

import com.medresource.model.ReportProgress;
import com.medresource.model.ReportStage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la pantalla de espera del reporte, sin ventana.
 */
class ReportProgressPanelTest {

    @Test
    void startsInTheFirstStageWithoutProgress() {
        ReportProgressPanel panel = new ReportProgressPanel(true);

        assertEquals(ReportProgressPanel.StageState.CURRENT, panel.stateOf(ReportStage.METRICS));
        assertEquals(ReportProgressPanel.StageState.PENDING, panel.stateOf(ReportStage.WHAT_IF));
        assertEquals(ReportProgressPanel.StageState.PENDING, panel.stateOf(ReportStage.WRITING));
        assertEquals("0 %", panel.percentText());
        assertEquals(ReportFormat.CALCULATING, panel.remainingText());
        assertEquals(ReportProgressPanel.CANCEL, panel.cancelButton().getText());
    }

    @Test
    void saysWhoWillWriteTheText() {
        assertEquals(ReportProgressPanel.AI_TEXT, new ReportProgressPanel(true).subtitleText());
        assertEquals(ReportProgressPanel.TEMPLATE_TEXT, new ReportProgressPanel(false).subtitleText());
    }

    @Test
    void showsTheStagesThatEndedTheBarAndTheTimeLeft() {
        ReportProgressPanel panel = new ReportProgressPanel(true);

        panel.show(new ReportProgress(ReportStage.WHAT_IF, 6000, 18000));

        assertEquals(ReportProgressPanel.StageState.DONE, panel.stateOf(ReportStage.METRICS));
        assertEquals(ReportProgressPanel.StageState.CURRENT, panel.stateOf(ReportStage.WHAT_IF));
        assertEquals(ReportProgressPanel.StageState.PENDING, panel.stateOf(ReportStage.WRITING));
        assertEquals(0.25, panel.getFraction(), 1e-9);
        assertEquals("25 %", panel.percentText());
        assertEquals("Faltan unos 18 s", panel.remainingText());
    }

    @Test
    void theBarNeverGoesBack() {
        ReportProgressPanel panel = new ReportProgressPanel(true);
        panel.show(new ReportProgress(ReportStage.WHAT_IF, 6000, 6000));

        panel.show(new ReportProgress(ReportStage.WHAT_IF, 7000, 21000));

        assertEquals(0.5, panel.getFraction(), 1e-9);
        assertEquals("Faltan unos 21 s", panel.remainingText());
    }

    @Test
    void anErrorIsShownInRedAndTheButtonClosesTheWindow() {
        ReportProgressPanel panel = new ReportProgressPanel(false);

        panel.showError("No se pudo generar el reporte.");
        panel.show(new ReportProgress(ReportStage.WRITING, 9000, 0));

        assertTrue(panel.hasFailed());
        assertEquals("No se pudo generar el reporte.", panel.remainingText());
        assertEquals(HomeTheme.DANGER, panel.remainingColor());
        assertEquals(ReportProgressPanel.CLOSE, panel.cancelButton().getText());
        assertEquals(ReportProgressPanel.StageState.CURRENT, panel.stateOf(ReportStage.METRICS));
    }

    @Test
    void theButtonRunsItsAction() {
        ReportProgressPanel panel = new ReportProgressPanel(true);
        AtomicInteger clicks = new AtomicInteger();
        panel.onCancel(clicks::incrementAndGet);

        panel.cancelButton().doClick();

        assertEquals(1, clicks.get());
        assertFalse(panel.hasFailed());
    }

    @Test
    void wrongDataIsRejected() {
        ReportProgressPanel panel = new ReportProgressPanel(true);

        assertThrows(IllegalArgumentException.class, () -> panel.show(null));
        assertThrows(IllegalArgumentException.class, () -> panel.showError(" "));
        assertThrows(IllegalArgumentException.class, () -> panel.onCancel(null));
    }
}
