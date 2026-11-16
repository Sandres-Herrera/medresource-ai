package com.medresource.ui.view;

import com.medresource.model.ComparisonFixtures;
import com.medresource.model.ComparisonType;
import com.medresource.model.IndicatorComparison;
import com.medresource.model.RunComparison;
import com.medresource.model.SavedComparison;
import com.medresource.model.SettingChange;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la lista de comparaciones guardadas.
 */
class SavedComparisonsPanelTest {

    private static final SavedComparison STRATEGIES = new SavedComparison(3, LocalDateTime.of(2026, 11, 16, 11, 55),
            ComparisonFixtures.strategies(1, 2));
    private static final SavedComparison RESOURCES = new SavedComparison(1, LocalDateTime.of(2026, 11, 15, 8, 5),
            new RunComparison(2, 4, 6, "FIFO", "FIFO", ComparisonType.RESOURCES,
                    List.of(new SettingChange("Camas", "30", "40")),
                    List.of(new IndicatorComparison("Pacientes atendidos", "", 70, 75, false)),
                    "La ejecución #6 mejoró frente a la #4.", List.of("Se compara un solo caso.")));

    private final SavedComparisonsPanel panel = new SavedComparisonsPanel();

    @Test
    void startsSearchingForTheComparisons() {
        assertEquals(SavedComparisonsPanel.LOADING_TEXT, panel.messageText());
        assertEquals(0, panel.rowCount());
    }

    @Test
    void eachComparisonShowsItsRunsItsTypeAndHowTheDeathsChanged() {
        panel.showComparisons(List.of(STRATEGIES, RESOURCES));

        assertEquals("", panel.messageText());
        assertEquals(2, panel.rowCount());
        assertEquals(List.of("#3", "#1", "16/11/2026 11:55", "#1 vs #2", "Estrategias",
                "FIFO (orden de llegada) → Prioridad clínica (triage)", "8 → 4"), panel.rowTexts(0));
        assertEquals(List.of("#1", "#2", "15/11/2026 08:05", "#4 vs #6", "Recursos", "FIFO", "—"),
                panel.rowTexts(1));
        assertEquals(SavedComparisonsPanel.COLUMNS.size(), panel.rowTexts(0).size());
    }

    @Test
    void openingAComparisonGivesItsId() {
        List<Integer> opened = new ArrayList<>();
        panel.onOpen(opened::add);
        panel.showComparisons(List.of(STRATEGIES, RESOURCES));

        panel.openButton(1).doClick();
        panel.openButton(0).doClick();

        assertEquals(List.of(1, 3), opened);
    }

    @Test
    void withoutComparisonsItSaysHowToMakeOne() {
        panel.showComparisons(List.of());

        assertEquals(SavedComparisonsPanel.EMPTY_TEXT, panel.messageText());
        assertEquals(0, panel.rowCount());
    }

    @Test
    void showsNoticesAndErrorsInsteadOfTheList() {
        panel.showComparisons(List.of(STRATEGIES));
        panel.showError("No se pudo leer.");

        assertEquals("No se pudo leer.", panel.messageText());
        assertEquals(HomeTheme.DANGER, panel.messageColor());
        assertEquals(0, panel.rowCount());

        panel.showNotice("Sin base de datos.");
        assertEquals(HomeTheme.MUTED, panel.messageColor());
    }

    @Test
    void needsAListAndAnOpenAction() {
        assertThrows(IllegalArgumentException.class, () -> panel.showComparisons(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onOpen(null));
    }
}
