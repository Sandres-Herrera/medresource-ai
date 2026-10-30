package com.medresource.ui.render;

import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los colores de la interfaz.
 */
class PaletteTest {

    @Test
    void everyZoneHasItsOwnColor() {
        Set<Color> colors = new HashSet<>();
        for (ZoneType zone : ZoneType.values()) {
            colors.add(Palette.zone(zone));
        }

        assertEquals(ZoneType.values().length, colors.size());
    }

    @Test
    void everyTriageLevelHasItsOwnColor() {
        Set<Color> colors = new HashSet<>();
        for (TriageLevel level : TriageLevel.values()) {
            colors.add(Palette.triage(level));
        }

        assertEquals(TriageLevel.values().length, colors.size());
    }

    @Test
    void theMostUrgentTriageIsRedAndTheLeastUrgentIsBlue() {
        Color urgent = Palette.triage(TriageLevel.I);
        Color calm = Palette.triage(TriageLevel.V);

        assertTrue(urgent.getRed() > urgent.getBlue());
        assertTrue(calm.getBlue() > calm.getRed());
    }

    @Test
    void theMarksAreDifferentFromEachOtherSoTheyCanBeTellApart() {
        Set<Color> marks = Set.of(Palette.CARD, Palette.OPERATING_TABLE, Palette.MORGUE_SLAB, Palette.STAFF_SEAT,
                Palette.WAITING_SEAT, Palette.TRIAGE_STATION, Palette.BLOOD_UNIT, Palette.ENTRANCE);

        assertEquals(8, marks.size());
    }

    @Test
    void doesNotColorNothing() {
        assertThrows(IllegalArgumentException.class, () -> Palette.zone(null));
        assertThrows(IllegalArgumentException.class, () -> Palette.triage(null));
    }
}