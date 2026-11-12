package com.medresource.ui.view;

import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la leyenda de triage y de su punto de color, sin ventana.
 */
class TriageLegendTest {

    @Test
    void namesEachLevelShortly() {
        assertEquals("I · Inmediato", TriageLegend.shortName(TriageLevel.I));
        assertEquals("IV · Menos urgente", TriageLegend.shortName(TriageLevel.IV));
        assertThrows(IllegalArgumentException.class, () -> TriageLegend.shortName(null));
    }

    @Test
    void listsTheFiveLevelsAndThePatientsWithoutTriage() {
        List<String> entries = new TriageLegend().entries();

        assertEquals(TriageLevel.values().length + 1, entries.size());
        assertEquals("I · Inmediato", entries.get(0));
        assertEquals(TriageLegend.PENDING, entries.get(entries.size() - 1));
    }

    @Test
    void theDotKeepsItsColor() {
        Dot dot = new Dot(HomeTheme.RED);

        dot.setColor(HomeTheme.BLUE);

        assertEquals(HomeTheme.BLUE, dot.getColor());
        assertEquals(Dot.SIZE, dot.getPreferredSize().width);
        assertThrows(IllegalArgumentException.class, () -> new Dot(null));
    }
}
