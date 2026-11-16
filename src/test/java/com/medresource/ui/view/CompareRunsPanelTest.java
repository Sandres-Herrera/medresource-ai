package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.NarrativeSource;
import com.medresource.model.SavedReportSummary;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del formulario para elegir las dos ejecuciones que se comparan.
 */
class CompareRunsPanelTest {

    private static final CaseConfig CATASTROPHE = new CaseConfig(7L, Scenario.CATASTROPHE, 80, 240);
    private static final CaseConfig NORMAL = CaseConfig.of(42L, Scenario.NORMAL);

    /** Ejecución #9 del caso 3, hecha a partir de la #5. */
    private static final SavedReportSummary RERUN = summary(4, 9, 3, CATASTROPHE, "Prioridad clínica (triage)", 1,
            5);
    /** Ejecución #6 del caso 1, que no tiene otra. */
    private static final SavedReportSummary ALONE = summary(3, 6, 1, NORMAL, "FIFO", 0, null);
    /** Ejecución #5 del caso 3. */
    private static final SavedReportSummary ORIGINAL = summary(2, 5, 3, CATASTROPHE, "FIFO (orden de llegada)", 13,
            null);
    /** Un segundo reporte de la ejecución #5. */
    private static final SavedReportSummary SECOND_REPORT = summary(1, 5, 3, CATASTROPHE, "FIFO (orden de llegada)",
            13, null);

    private final CompareRunsPanel panel = new CompareRunsPanel();

    private static SavedReportSummary summary(int id, int run, int caseId, CaseConfig config, String strategy,
                                              int deaths, Integer base) {
        return new SavedReportSummary(id, run, LocalDateTime.of(2026, 11, 16, 10, id), config, strategy, deaths,
                NarrativeSource.TEMPLATE, caseId, base);
    }

    @Test
    void startsSearchingAndCannotCompare() {
        assertEquals(CompareRunsPanel.LOADING_TEXT, panel.messageA());
        assertFalse(panel.compareButton().isEnabled());
    }

    @Test
    void listsEachRunOnceAndAsksForTheReferenceFirst() {
        panel.showRuns(List.of(RERUN, ALONE, ORIGINAL, SECOND_REPORT));

        assertEquals(List.of(9, 6, 5), panel.runsA());
        assertEquals(List.of(), panel.runsB());
        assertEquals(CompareRunsPanel.CHOOSE_A_TEXT, panel.messageB());
        assertEquals("Ejecución #9 · caso #3 · de #5", panel.tileA(9).getTitle());
        assertEquals(Scenario.CATASTROPHE.getDisplayName()
                        + " · Prioridad clínica (triage) · 1 fallecido · 16/11 10:04",
                panel.tileA(9).getDetail());
        assertEquals(Scenario.NORMAL.getDisplayName() + " · FIFO · 0 fallecidos · 16/11 10:03",
                panel.tileA(6).getDetail());
        assertFalse(panel.compareButton().isEnabled());
    }

    @Test
    void theSecondRunCanOnlyBeAnotherRunOfTheSameCase() {
        panel.showRuns(List.of(RERUN, ALONE, ORIGINAL));

        panel.tileA(5).doClick();

        assertEquals(Optional.of(5), panel.selectedA());
        assertTrue(panel.tileA(5).isSelected());
        assertEquals(List.of(9), panel.runsB());
        assertThrows(IllegalArgumentException.class, () -> panel.selectB(6));
        assertThrows(IllegalArgumentException.class, () -> panel.selectB(5));
    }

    @Test
    void aCaseWithoutAnotherRunSaysHowToGetOne() {
        panel.showRuns(List.of(RERUN, ALONE, ORIGINAL));

        panel.selectA(6);

        assertEquals(List.of(), panel.runsB());
        assertTrue(panel.messageB().contains("El caso #1 solo tiene esta ejecución guardada"));
        assertTrue(panel.messageB().contains("Reutilizar caso"));
        assertFalse(panel.compareButton().isEnabled());
    }

    @Test
    void choosingBothLetsCompareAndGivesTheRunsInOrder() {
        List<String> compared = new ArrayList<>();
        panel.onCompare((a, b) -> compared.add(a + " vs " + b));
        panel.showRuns(List.of(RERUN, ALONE, ORIGINAL));

        panel.selectA(5);
        panel.tileB(9).doClick();
        panel.compareButton().doClick();

        assertEquals(Optional.of(9), panel.selectedB());
        assertTrue(panel.tileB(9).isSelected());
        assertTrue(panel.compareButton().isEnabled());
        assertEquals("Se comparará la ejecución #5 con la #9.", panel.statusText());
        assertEquals(List.of("5 vs 9"), compared);
    }

    @Test
    void choosingAnotherReferenceForgetsTheSecondRun() {
        panel.showRuns(List.of(RERUN, ALONE, ORIGINAL));
        panel.selectA(5);
        panel.selectB(9);

        panel.selectA(9);

        assertEquals(Optional.empty(), panel.selectedB());
        assertEquals(List.of(5), panel.runsB());
        assertFalse(panel.compareButton().isEnabled());
        assertEquals(CompareRunsPanel.HINT_TEXT, panel.statusText());
    }

    @Test
    void comparingTurnsTheButtonOffAndAnErrorLetsTryAgain() {
        panel.showRuns(List.of(RERUN, ORIGINAL));
        panel.selectA(5);
        panel.selectB(9);

        panel.showComparing();
        assertFalse(panel.compareButton().isEnabled());

        panel.showCompareError("No se pudo comparar.");
        assertTrue(panel.compareButton().isEnabled());
        assertEquals(HomeTheme.DANGER, panel.statusColor());

        panel.showCompared();
        assertTrue(panel.compareButton().isEnabled());
        assertEquals(HomeTheme.MUTED, panel.statusColor());
    }

    @Test
    void showsNoticesErrorsAndAnEmptyList() {
        panel.showRuns(List.of());
        assertEquals(CompareRunsPanel.EMPTY_TEXT, panel.messageA());

        panel.showError("Sin conexión.");
        assertEquals("Sin conexión.", panel.messageA());
        assertEquals(HomeTheme.DANGER, panel.messageAColor());
        assertEquals(List.of(), panel.runsA());
    }

    @Test
    void needsAListARunInTheListAndACompareAction() {
        assertThrows(IllegalArgumentException.class, () -> panel.showRuns(null));
        assertThrows(IllegalArgumentException.class, () -> panel.selectA(1));
        assertThrows(IllegalArgumentException.class, () -> panel.onCompare(null));
    }
}
