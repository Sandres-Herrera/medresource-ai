package com.medresource.ui.render;

import com.medresource.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los temas claro y oscuro de la vista 2.5D.
 */
class IsoThemeTest {

    /** Luminosidad relativa de un color, de 0 (negro) a 1 (blanco). */
    private static double luminance(Color color) {
        return (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
    }

    @Test
    void bothThemesColorEveryPartAndEveryFloor() {
        for (IsoTheme theme : IsoTheme.values()) {
            for (IsoTheme.Part part : IsoTheme.Part.values()) {
                assertNotNull(theme.color(part), theme + " " + part);
            }
            for (ZoneType zone : ZoneType.values()) {
                assertNotNull(theme.floor(zone), theme + " " + zone);
            }
        }
    }

    @Test
    void theLightThemeHasLightFloorsAndTheDarkOneDarkFloors() {
        for (ZoneType zone : ZoneType.values()) {
            assertTrue(luminance(IsoTheme.LIGHT.floor(zone)) > 0.85, zone.name());
            assertTrue(luminance(IsoTheme.DARK.floor(zone)) < 0.2, zone.name());
        }
        assertFalse(IsoTheme.LIGHT.isDark());
        assertTrue(IsoTheme.DARK.isDark());
    }

    @Test
    void thePeopleStandOutOnTheDarkFloorsAndTheirOutlineOnTheLightOnes() {
        for (Color person : new Color[] {Palette.DOCTOR_COAT, Palette.NURSE_UNIFORM, Palette.TRIAGE_NURSE,
                Palette.PORTER_UNIFORM}) {
            assertTrue(luminance(person) > 2 * luminance(IsoTheme.DARK.floor(ZoneType.CORRIDOR)), person.toString());
        }
        assertTrue(luminance(IsoPalette.OUTLINE) < 0.1);
    }

    @Test
    void theWallsDifferFromTheFloorsInBothThemes() {
        for (IsoTheme theme : IsoTheme.values()) {
            assertNotEquals(theme.color(IsoTheme.Part.WALL_LEFT), theme.floor(ZoneType.CORRIDOR));
            assertTrue(luminance(theme.color(IsoTheme.Part.WALL_RIGHT))
                    < luminance(theme.color(IsoTheme.Part.WALL_LEFT)), "la cara derecha es la de la sombra");
        }
    }

    @Test
    void namesEachThemeForTheSelector() {
        assertEquals("Claro", IsoTheme.LIGHT.getDisplayName());
        assertEquals("Oscuro", IsoTheme.DARK.getDisplayName());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> IsoTheme.LIGHT.color(null));
        assertThrows(IllegalArgumentException.class, () -> IsoTheme.DARK.floor(null));
    }
}
