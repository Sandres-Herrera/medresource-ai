package com.medresource.ui.view;

import com.medresource.model.ComparisonFixtures;
import com.medresource.model.ComparisonType;
import com.medresource.model.IndicatorComparison;
import com.medresource.model.RunComparison;
import com.medresource.model.SettingChange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del panel que muestra la comparación de dos ejecuciones, sin ventana.
 */
class ComparisonPanelTest {

    private final ComparisonPanel panel = new ComparisonPanel(ComparisonFixtures.strategies(1, 2));

    @Test
    void showsEachIndicatorWithBothValuesTheDifferenceAndTheResult() {
        assertEquals(3, panel.indicatorCount());
        assertEquals(List.of("Pacientes atendidos", "80", "92", "+12 (+15 %)", "Mejoró"), panel.indicatorRow(0));
        assertEquals(List.of("Fallecidos", "8", "4", "−4 (−50 %)", "Mejoró"), panel.indicatorRow(1));
        assertEquals(List.of("Espera promedio", "35,5 min", "22,3 min", "−13,3 min (−37,3 %)", "Mejoró"),
                panel.indicatorRow(2));
    }

    @Test
    void colorsTheResultByWhetherItGotBetter() {
        assertEquals(HomeTheme.GREEN, panel.resultColor(0));
        assertEquals(HomeTheme.GREEN, panel.resultColor(1));
        ComparisonPanel mixed = new ComparisonPanel(new RunComparison(1, 1, 2, "FIFO", "FIFO",
                ComparisonType.RESOURCES, List.of(new SettingChange("Camas", "30", "20")),
                List.of(new IndicatorComparison("Fallecidos", "", 4, 6, true),
                        new IndicatorComparison("Pacientes atendidos", "", 90, 90, false)),
                "Empeoró.", List.of()));
        assertEquals("Empeoró", mixed.indicatorRow(0).get(4));
        assertEquals(HomeTheme.DANGER, mixed.resultColor(0));
        assertEquals("Igual", mixed.indicatorRow(1).get(4));
        assertEquals(HomeTheme.MUTED, mixed.resultColor(1));
    }

    @Test
    void aPercentageChangesInPointsAndAnUnchangedValueHasNoSign() {
        assertEquals("+3 pts", ComparisonPanel.difference(new IndicatorComparison("Mortalidad", "%", 5, 8, true)));
        assertEquals("0", ComparisonPanel.difference(new IndicatorComparison("Fallecidos", "", 4, 4, true)));
        assertEquals("4 %", ComparisonPanel.value(4, "%"));
    }

    @Test
    void theSaveButtonOnlyTellsWhoUsesIt() {
        AtomicInteger clicks = new AtomicInteger();
        panel.onSave(clicks::incrementAndGet);

        panel.saveButton().doClick();

        assertEquals(1, clicks.get());
        assertEquals(ComparisonPanel.SAVE, panel.saveButton().getText());
    }

    @Test
    void savingTurnsTheButtonOffAndAnErrorLetsTryAgain() {
        panel.showSaving();
        assertFalse(panel.saveButton().isEnabled());

        panel.showSaveError("No se pudo guardar.");
        assertTrue(panel.saveButton().isEnabled());
        assertEquals(HomeTheme.DANGER, panel.statusColor());

        panel.showSaved("Guardada como comparación #3");
        assertFalse(panel.saveButton().isEnabled());
        assertEquals("Guardada como comparación #3", panel.statusText());
    }

    @Test
    void aSavedComparisonHasNoSaveButton() {
        panel.showAlreadySaved("Guardada el 16/11/2026 11:55");

        assertFalse(panel.saveButton().isVisible());
        assertEquals("Guardada el 16/11/2026 11:55", panel.statusText());
    }

    @Test
    void needsAComparisonAndASaveAction() {
        assertThrows(IllegalArgumentException.class, () -> new ComparisonPanel(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onSave(null));
    }
}
