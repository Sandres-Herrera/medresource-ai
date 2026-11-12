package com.medresource.ui.view;

import com.medresource.model.BloodType;
import com.medresource.model.EventType;
import com.medresource.model.Sex;
import com.medresource.model.SimulationEvent;
import com.medresource.model.Symptom;
import com.medresource.model.TriageLevel;
import com.medresource.ui.controller.EventLog;
import com.medresource.ui.controller.PatientCard;
import com.medresource.ui.render.PatientMarker;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la ficha del paciente de la ventana de la simulación, sin ventana.
 */
class PatientCardPanelTest {

    private final PatientCardPanel panel = new PatientCardPanel();

    private static PatientCard treated() {
        return new PatientCard("P003", 53, Sex.MALE, BloodType.A_POSITIVE, "Síndrome febril (dengue)",
                List.of(Symptom.FATIGUE, Symptom.HIGH_FEVER, Symptom.FEVER), TriageLevel.III, "En tratamiento",
                "Hospitalización · H1-C3", false, 94.2, 0.34, 20, 25,
                List.of("D10 · Medicina interna", "E03 · Enfermería"));
    }

    private static PatientCard arriving() {
        return new PatientCard("P008", 8, Sex.FEMALE, BloodType.O_NEGATIVE, "Desmayo",
                List.of(Symptom.UNCONSCIOUS), null, "Haciendo fila en el triage", "Triage", true, 45,
                PatientMarker.NO_PROGRESS, 80, 0, List.of());
    }

    private static List<EventLog.Entry> trail(int size) {
        List<EventLog.Entry> entries = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            entries.add(new EventLog.Entry("00:2" + index + ":00",
                    new SimulationEvent(20 + index, EventType.ARRIVAL, "P003", null, "Llega")));
        }
        return entries;
    }

    @Test
    void startsEmptyAndSaysHowToChooseAPatient() {
        assertFalse(panel.isShowingCard());
        assertFalse(panel.isHighlighted());
        assertEquals("Ficha del paciente", panel.titleText());
        assertEquals(PatientCardPanel.HINT, panel.hintText());
        assertNull(panel.followButton());
    }

    @Test
    void showsWhoThePatientIsAndWhatHeHas() {
        panel.showCard(treated(), trail(2));

        assertTrue(panel.isShowingCard());
        assertTrue(panel.isHighlighted());
        assertEquals("Paciente P003", panel.titleText());
        assertEquals("Síndrome febril (dengue)", panel.valueOf("Diagnóstico"));
        assertEquals("Hospitalización · H1-C3", panel.valueOf("Ubicación"));
        assertEquals("D10 · Medicina interna\nE03 · Enfermería", panel.valueOf("Atienden"));
        assertEquals("25 min", panel.valueOf("En el hospital"));
        assertEquals("94 / 100", panel.valueOf("Salud"));
        assertEquals("34 %", panel.valueOf("Tratamiento"));
    }

    @Test
    void theChipsShowTheTriageTheActivityAndTheSymptomsWithTheWorstStarred() {
        panel.showCard(treated(), trail(0));

        List<String> chips = panel.chipTexts();

        assertEquals("Triage III · Urgente", chips.get(0));
        assertEquals("En tratamiento", chips.get(1));
        assertEquals("★ Fatiga · III", chips.get(2));
        assertEquals("★ Fiebre alta · III", chips.get(3));
        assertEquals("Fiebre · IV", chips.get(4));
    }

    @Test
    void withoutTriageTheDiagnosisIsPendingAndNothingIsStarred() {
        panel.showCard(arriving(), trail(0));

        assertEquals("Sin clasificar", panel.chipTexts().get(0));
        assertEquals("Pendiente", panel.valueOf("Diagnóstico"));
        assertEquals("Inconsciente · I", panel.chipTexts().get(2));
        assertEquals("Triage (va en camino)", panel.valueOf("Ubicación"));
        assertEquals("Nadie por ahora", panel.valueOf("Atienden"));
        assertEquals("", panel.valueOf("Tratamiento"));
    }

    @Test
    void warnsWhenThePatientCannotWalk() {
        panel.showCard(arriving(), trail(0));
        assertTrue(panel.hasWalkingWarning());

        panel.showCard(treated(), trail(0));
        assertFalse(panel.hasWalkingWarning());
    }

    @Test
    void theTrailShowsTheLastEventsOnly() {
        panel.showCard(treated(), trail(7));

        List<String> lines = panel.trailTexts();

        assertEquals(PatientCardPanel.TRAIL_SIZE, lines.size());
        assertEquals("00:22:00 Llegada de paciente", lines.get(0));
        assertEquals("00:26:00 Llegada de paciente", lines.get(lines.size() - 1));
    }

    @Test
    void theFollowButtonTogglesAndTellsTheListener() {
        List<Boolean> heard = new ArrayList<>();
        panel.onFollow(heard::add);
        panel.showCard(treated(), trail(0));

        assertEquals("Seguir con la cámara", panel.followButton().getAccessibleContext().getAccessibleName());

        panel.followButton().doClick();
        panel.showCard(treated(), trail(0));

        assertTrue(panel.isFollowing());
        assertEquals("Dejar de seguir", panel.followButton().getAccessibleContext().getAccessibleName());

        panel.followButton().doClick();

        assertEquals(List.of(true, false), heard);
    }

    private static PatientCard later() {
        return new PatientCard("P003", 53, Sex.MALE, BloodType.A_POSITIVE, "Síndrome febril (dengue)",
                List.of(Symptom.FATIGUE, Symptom.HIGH_FEVER, Symptom.FEVER), TriageLevel.III, "En tratamiento",
                "Hospitalización · H1-C3", false, 93.1, 0.41, 20, 26,
                List.of("D10 · Medicina interna", "E03 · Enfermería"));
    }

    private static void mouse(Clickable button, int id) {
        button.setSize(120, 34);
        button.dispatchEvent(new MouseEvent(button, id, 0L, 0, 10, 10, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void theFollowButtonWorksWhileTheCardChangesBetweenPressAndRelease() {
        List<Boolean> heard = new ArrayList<>();
        panel.onFollow(heard::add);
        panel.showCard(treated(), trail(1));
        HomeButton pressed = panel.followButton();

        mouse(pressed, MouseEvent.MOUSE_PRESSED);
        panel.showCard(later(), trail(2));
        assertSame(pressed, panel.followButton());
        mouse(pressed, MouseEvent.MOUSE_RELEASED);

        assertEquals(List.of(true), heard);
        assertTrue(panel.isFollowing());
        assertEquals("Dejar de seguir", panel.followButton().getAccessibleContext().getAccessibleName());
    }

    @Test
    void theButtonsAreTheSameOnesWhileTheSamePatientIsShown() {
        panel.showCard(treated(), trail(1));
        HomeButton follow = panel.followButton();

        panel.showCard(later(), trail(3));
        panel.setFollowing(true);
        panel.setFollowing(false);
        panel.showCard(treated(), trail(4));

        assertSame(follow, panel.followButton());
        assertEquals("Paciente P003", panel.titleText());
        assertEquals("25 min", panel.valueOf("En el hospital"));
    }

    @Test
    void saysWhenThePatientAlreadyLeftAndClearingStopsFollowing() {
        panel.showCard(treated(), trail(0));
        panel.setFollowing(true);

        panel.showGone("P003");

        assertFalse(panel.isShowingCard());
        assertFalse(panel.isFollowing());
        assertEquals("Ya salió del hospital.", panel.hintText());

        panel.clear();

        assertEquals(PatientCardPanel.HINT, panel.hintText());
        assertFalse(panel.isHighlighted());
    }

    @Test
    void keepsItsWidth() {
        panel.showCard(treated(), trail(3));

        assertEquals(PatientCardPanel.WIDTH, panel.getPreferredSize().width);
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> panel.showCard(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> panel.showCard(treated(), null));
        assertThrows(IllegalArgumentException.class, () -> panel.onFollow(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onClose(null));
    }
}
