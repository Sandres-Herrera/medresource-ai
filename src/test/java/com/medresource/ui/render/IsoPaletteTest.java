package com.medresource.ui.render;

import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los colores de la vista 2.5D.
 */
class IsoPaletteTest {

    /** Luminosidad relativa de un color, de 0 (negro) a 1 (blanco). */
    private static double luminance(Color color) {
        return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
    }

    @Test
    void everyRoomHasItsOwnAccent() {
        Set<Color> accents = new HashSet<>();
        for (ZoneType zone : ZoneType.values()) {
            accents.add(IsoPalette.accent(zone));
        }

        assertEquals(ZoneType.values().length, accents.size());
    }

    @Test
    void theCorridorsAreALittleLighterAndTheMorgueALittleDarker() {
        assertEquals(IsoPalette.FLOOR, IsoPalette.floor(ZoneType.HOSPITALIZATION));
        assertTrue(luminance(IsoPalette.floor(ZoneType.CORRIDOR)) > luminance(IsoPalette.FLOOR));
        assertTrue(luminance(IsoPalette.floor(ZoneType.MORGUE)) < luminance(IsoPalette.FLOOR));
    }

    @Test
    void thePeopleStandOutOnTheDarkFloors() {
        for (ZoneType zone : ZoneType.values()) {
            assertTrue(luminance(IsoPalette.floor(zone)) < 0.15, zone.name());
        }
        for (TriageLevel level : TriageLevel.values()) {
            assertTrue(luminance(Palette.triage(level)) > 2 * luminance(IsoPalette.CORRIDOR_FLOOR), level.name());
        }
        for (Color staff : new Color[] {Palette.DOCTOR_COAT, Palette.NURSE_UNIFORM, Palette.TRIAGE_NURSE,
                Palette.PORTER_UNIFORM}) {
            assertTrue(luminance(staff) > 2 * luminance(IsoPalette.CORRIDOR_FLOOR), staff.toString());
        }
    }

    @Test
    void theCapIsTheUniformDarker() {
        Color cap = IsoPalette.cap(Palette.NURSE_UNIFORM);

        assertTrue(luminance(cap) < luminance(Palette.NURSE_UNIFORM));
        assertNotEquals(Palette.NURSE_UNIFORM, cap);
    }

    @Test
    void darkensAndLightensKeepingTheTransparency() {
        Color base = new Color(100, 200, 50, 120);

        assertEquals(new Color(50, 100, 25, 120), IsoPalette.darker(base, 0.5));
        assertEquals(new Color(178, 228, 153, 120), IsoPalette.lighter(base, 0.5));
        assertEquals(base, IsoPalette.darker(base, 1));
        assertEquals(base, IsoPalette.lighter(base, 0));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> IsoPalette.accent(null));
        assertThrows(IllegalArgumentException.class, () -> IsoPalette.floor(null));
        assertThrows(IllegalArgumentException.class, () -> IsoPalette.cap(null));
        assertThrows(IllegalArgumentException.class, () -> IsoPalette.darker(Color.RED, 1.5));
        assertThrows(IllegalArgumentException.class, () -> IsoPalette.lighter(null, 0.5));
    }
}
