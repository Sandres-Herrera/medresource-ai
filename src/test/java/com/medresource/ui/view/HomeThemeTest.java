package com.medresource.ui.view;

import com.medresource.model.EventType;
import com.medresource.model.Scenario;
import com.medresource.model.TriageLevel;
import com.medresource.ui.render.Palette;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del tema de la pantalla de inicio.
 */
class HomeThemeTest {

    /** Contraste entre dos colores según la WCAG: de 1 (iguales) a 21 (negro sobre blanco). */
    private static double contrast(Color first, Color second) {
        double one = luminance(first);
        double two = luminance(second);
        return (Math.max(one, two) + 0.05) / (Math.min(one, two) + 0.05);
    }

    private static double luminance(Color color) {
        double[] channels = {color.getRed(), color.getGreen(), color.getBlue()};
        for (int index = 0; index < channels.length; index++) {
            double value = channels[index] / 255.0;
            channels[index] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    @Test
    void theTextsCanBeReadOnTheBackgroundAndOnTheCards() {
        for (Color background : List.of(HomeTheme.BACKGROUND, HomeTheme.CARD, HomeTheme.CARD_HOVER)) {
            assertTrue(contrast(HomeTheme.TEXT, background) >= 7, "texto principal");
            assertTrue(contrast(HomeTheme.MUTED, background) >= 4.5, "texto secundario");
        }
        assertTrue(contrast(HomeTheme.ACCENT_TEXT, HomeTheme.ACCENT) >= 4.5, "texto del botón principal");
        assertTrue(contrast(HomeTheme.DANGER, HomeTheme.DANGER_TILE) >= 4.5, "etiqueta de la demostración");
    }

    @Test
    void eachOptionHasItsOwnColor() {
        Set<Color> accents = Set.of(HomeTheme.ACCENT, HomeTheme.QUICK, HomeTheme.DANGER);

        assertEquals(3, accents.size());
    }

    @Test
    void theCardsStandOutFromTheBackground() {
        assertTrue(luminance(HomeTheme.CARD) > luminance(HomeTheme.BACKGROUND));
        assertTrue(luminance(HomeTheme.CARD_HOVER) > luminance(HomeTheme.CARD));
    }

    @Test
    void givesTheFontWithTheRequestedStyleAndSize() {
        Font font = HomeTheme.font(Font.BOLD, 34f);

        assertEquals(34f, font.getSize2D(), 0.0);
        assertTrue(font.isBold());
    }

    @Test
    void usesAModernFamilyOrTheSansSerifOfJava() {
        assertTrue(List.of("Segoe UI", "Inter", "Helvetica Neue", Font.SANS_SERIF).contains(HomeTheme.family()));
    }

    @Test
    void rejectsAFontWithoutSize() {
        assertThrows(IllegalArgumentException.class, () -> HomeTheme.font(Font.PLAIN, 0f));
        assertThrows(IllegalArgumentException.class, () -> HomeTheme.font(Font.PLAIN, -3f));
    }

    @Test
    void eachScenarioHasItsOwnColorThatCanBeReadOnACard() {
        List<Color> colors = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            Color color = HomeTheme.scenarioColor(scenario);
            colors.add(color);
            assertTrue(contrast(color, HomeTheme.CARD) >= 4.5, scenario.name());
        }

        assertEquals(Scenario.values().length, new HashSet<>(colors).size());
        assertEquals(HomeTheme.ACCENT, HomeTheme.scenarioColor(Scenario.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> HomeTheme.scenarioColor(null));
    }

    @Test
    void theFieldsAndTheChosenOptionsStandOutFromTheCards() {
        assertTrue(luminance(HomeTheme.FIELD) < luminance(HomeTheme.CARD));
        assertTrue(contrast(HomeTheme.TEXT, HomeTheme.FIELD) >= 7);
        assertTrue(contrast(HomeTheme.TEXT, HomeTheme.SELECTED) >= 7);
    }

    @Test
    void eachTriageLevelHasItsBrightColorAndWithoutTriageItIsGray() {
        Set<Color> colors = new HashSet<>();
        for (TriageLevel level : TriageLevel.values()) {
            Color color = HomeTheme.triageColor(level);
            colors.add(color);
            assertTrue(contrast(color, HomeTheme.CARD) >= 4, level.name());
        }

        assertEquals(TriageLevel.values().length, colors.size());
        assertEquals(HomeTheme.RED, HomeTheme.triageColor(TriageLevel.I));
        assertEquals(HomeTheme.PENDING, HomeTheme.triageColor(null));
    }

    @Test
    void aPatientOnThePlanHasAboutTheSameColorAsItsCard() {
        for (TriageLevel level : TriageLevel.values()) {
            Color plan = Palette.triage(level);
            Color card = HomeTheme.triageColor(level);
            int red = plan.getRed() - card.getRed();
            int green = plan.getGreen() - card.getGreen();
            int blue = plan.getBlue() - card.getBlue();
            assertTrue(Math.sqrt(red * red + green * green + blue * blue) < 60, level.name());
        }
    }

    @Test
    void eachEventHasAColorThatCanBeReadOnTheLog() {
        for (EventType type : EventType.values()) {
            assertTrue(contrast(HomeTheme.eventColor(type), HomeTheme.CARD) >= 3, type.name());
        }
        assertEquals(HomeTheme.BLUE, HomeTheme.eventColor(EventType.ARRIVAL));
        assertEquals(HomeTheme.RED, HomeTheme.eventColor(EventType.DEATH));
        assertEquals(HomeTheme.GREEN, HomeTheme.eventColor(EventType.DISCHARGE));
        assertThrows(IllegalArgumentException.class, () -> HomeTheme.eventColor(null));
    }

    @Test
    void theHealthUsesTheCutsOfThePlan() {
        assertEquals(HomeTheme.GREEN, HomeTheme.healthColor(70));
        assertEquals(HomeTheme.YELLOW, HomeTheme.healthColor(69.9));
        assertEquals(HomeTheme.ORANGE, HomeTheme.healthColor(20));
        assertEquals(HomeTheme.RED, HomeTheme.healthColor(19.9));
        assertThrows(IllegalArgumentException.class, () -> HomeTheme.healthColor(101));
    }
}
