package com.medresource.ui.view;

import com.medresource.model.EventType;
import com.medresource.model.SimulationEvent;
import com.medresource.ui.controller.EventCategory;
import com.medresource.ui.controller.EventFilter;
import com.medresource.ui.controller.EventLog;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del registro de eventos de la ventana de la simulación, sin ventana.
 */
class EventLogPanelTest {

    private final EventLogPanel panel = new EventLogPanel();

    private static EventLog.Entry entry(String time, EventType type, String patient) {
        return new EventLog.Entry(time, new SimulationEvent(0, type, patient, null, patient + " " + type.name()));
    }

    @Test
    void startsEmptyShowingEverything() {
        assertEquals(0, panel.rowCount());
        assertEquals(EventLogPanel.EMPTY, panel.emptyText());
        assertEquals(EventCategory.ALL, panel.getCategory());
        assertTrue(panel.pill(EventCategory.ALL).isSelected());
        assertFalse(panel.onlySelectedBox().isEnabled());
    }

    @Test
    void showsAndAppendsRowsWithTimeTypeAndDescription() {
        panel.showEntries(List.of(entry("00:20:16", EventType.ARRIVAL, "P003")));
        panel.appendEntries(List.of(entry("00:20:53", EventType.TRIAGE_STARTED, "P003")));

        assertEquals(2, panel.rowCount());
        assertEquals("00:20:16 Llegada de paciente P003 ARRIVAL", panel.rowText(0));
        assertTrue(panel.rowText(1).startsWith("00:20:53 Inicio de triage"));
    }

    @Test
    void keepsAtMostTheLastRows() {
        List<EventLog.Entry> many = new ArrayList<>();
        for (int index = 0; index < EventLogPanel.MAX_ROWS + 10; index++) {
            many.add(entry("00:00:00", EventType.ARRIVAL, "P" + index));
        }

        panel.appendEntries(many);

        assertEquals(EventLogPanel.MAX_ROWS, panel.rowCount());
        assertTrue(panel.rowText(0).contains("P10 "));
    }

    @Test
    void aPillChoosesTheGroupAndTellsTheListener() {
        List<String> heard = new ArrayList<>();
        panel.onFilterChanged(() -> heard.add("filtro"));

        panel.pill(EventCategory.DEATHS).doClick();

        assertEquals(EventCategory.DEATHS, panel.getCategory());
        assertTrue(panel.pill(EventCategory.DEATHS).isSelected());
        assertFalse(panel.pill(EventCategory.ALL).isSelected());
        assertEquals(EventLogPanel.NO_DEATHS, panel.emptyText());
        assertEquals(List.of("filtro"), heard);
        assertEquals(new EventFilter(EventCategory.DEATHS, null), panel.filter("P003"));
    }

    @Test
    void onlyTheSelectedPatientWhenTheBoxIsChecked() {
        panel.setOnlySelectedEnabled(true);
        panel.onlySelectedBox().setSelected(true);

        assertEquals(new EventFilter(EventCategory.ALL, "P003"), panel.filter("P003"));
        assertEquals(new EventFilter(EventCategory.ALL, null), panel.filter(null));

        panel.setOnlySelectedEnabled(false);

        assertFalse(panel.onlySelectedBox().isSelected());
    }

    @Test
    void theRowsOfTheSelectedPatientAreHighlightedAndAClickSelectsHim() {
        List<String> clicked = new ArrayList<>();
        panel.onPatientClicked(clicked::add);
        panel.showEntries(List.of(entry("00:20:16", EventType.ARRIVAL, "P003"),
                entry("00:21:00", EventType.ARRIVAL, "P004")));
        panel.setSelectedPatient("P003");

        assertTrue(panel.isHighlighted(0));
        assertFalse(panel.isHighlighted(1));

        panel.clickRow(1);

        assertEquals(List.of("P004"), clicked);
    }

    @Test
    void paintsTheRowsWithoutErrors() {
        panel.showEntries(List.of(entry("00:20:16", EventType.DEATH, "P003")));
        panel.setSize(900, 200);
        panel.doLayout();
        BufferedImage image = new BufferedImage(900, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        panel.paint(graphics);
        graphics.dispose();

        assertEquals(HomeTheme.CARD.getRGB(), image.getRGB(450, 4));
    }

    @Test
    void namesEachGroupShortly() {
        assertEquals("Todos", EventLogPanel.shortName(EventCategory.ALL));
        assertEquals("Altas y remisiones", EventLogPanel.shortName(EventCategory.OUTCOMES));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> panel.showEntries(null));
        assertThrows(IllegalArgumentException.class, () -> panel.appendEntries(null));
        assertThrows(IllegalArgumentException.class, () -> panel.choose(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onFilterChanged(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onPatientClicked(null));
        assertThrows(IllegalArgumentException.class, () -> EventLogPanel.shortName(null));
    }
}
