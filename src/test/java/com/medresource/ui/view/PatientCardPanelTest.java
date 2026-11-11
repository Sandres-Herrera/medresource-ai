package com.medresource.ui.view;

import com.medresource.model.BloodType;
import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import com.medresource.ui.controller.PatientCard;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.PatientMarker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la ficha del paciente de la ventana de la simulación, sin ventana.
 */
class PatientCardPanelTest {

    private final PatientCardPanel panel = new PatientCardPanel();

    private static PatientCard treated() {
        return new PatientCard("P007", 34, Sex.MALE, BloodType.O_NEGATIVE, "Dengue", List.of("Fiebre alta", "Fatiga"),
                TriageLevel.III, "En tratamiento", "Hospitalización · H1-C2", false, 64.4, 0.5, 75, 30,
                List.of("D03 · Medicina interna", "E05 · Enfermería"));
    }

    private static PatientCard walking() {
        return new PatientCard("P008", 8, Sex.FEMALE, BloodType.A_POSITIVE, "Gripa fuerte", List.of(), null,
                "Haciendo fila en el triage", "Triage", true, 95, PatientMarker.NO_PROGRESS, 80, 0, List.of());
    }

    @Test
    void startsEmptyAndSaysHowToChooseAPatient() {
        assertFalse(panel.isShowingCard());
        assertEquals(PatientCardPanel.HINT, panel.hintText());
        assertFalse(panel.followButton().isEnabled());
        assertFalse(panel.closeButton().isEnabled());
    }

    @Test
    void showsTheDataOfThePatient() {
        panel.showCard(treated());

        assertTrue(panel.isShowingCard());
        assertEquals("P007", panel.valueOf("Paciente"));
        assertEquals("34 años · Masculino", panel.valueOf("Edad y sexo"));
        assertEquals("O-", panel.valueOf("Sangre"));
        assertEquals("Dengue", panel.valueOf("Enfermedad"));
        assertEquals("Fiebre alta, Fatiga", panel.valueOf("Síntomas"));
        assertEquals(TriageLevel.III.getDisplayName(), panel.valueOf("Triage"));
        assertEquals("En tratamiento", panel.valueOf("Ahora"));
        assertEquals("Hospitalización · H1-C2", panel.valueOf("Lugar"));
        assertEquals("01:15:00 · hace 30 min", panel.valueOf("Llegó"));
        assertEquals("D03 · Medicina interna\nE05 · Enfermería", panel.valueOf("Lo atienden"));
        assertTrue(panel.followButton().isEnabled());
        assertTrue(panel.closeButton().isEnabled());
    }

    @Test
    void theBarsShowTheHealthWithItsColorAndTheProgress() {
        panel.showCard(treated());

        assertEquals(64, panel.healthBar().getValue());
        assertEquals("64 %", panel.healthBar().getString());
        assertEquals(Palette.health(64.4), panel.healthBar().getForeground());
        assertEquals(50, panel.progressBar().getValue());
        assertEquals("50 %", panel.progressBar().getString());
    }

    @Test
    void aPatientWithoutTriageWhoIsWalkingSaysSo() {
        panel.showCard(walking());

        assertEquals("Sin clasificar", panel.valueOf("Triage"));
        assertEquals("Ninguno", panel.valueOf("Síntomas"));
        assertEquals("Triage (va en camino)", panel.valueOf("Lugar"));
        assertEquals("Nadie en este momento", panel.valueOf("Lo atienden"));
        assertEquals("No está en tratamiento", panel.progressBar().getString());
    }

    @Test
    void saysWhenThePatientAlreadyLeft() {
        panel.showCard(treated());

        panel.showGone("P007");

        assertFalse(panel.isShowingCard());
        assertTrue(panel.hintText().startsWith("P007 ya salió del hospital."));
        assertFalse(panel.followButton().isEnabled());
        assertTrue(panel.closeButton().isEnabled());
    }

    @Test
    void clearingGoesBackToTheEmptyCardAndStopsFollowing() {
        panel.showCard(treated());
        panel.followButton().setSelected(true);

        panel.clear();

        assertFalse(panel.isShowingCard());
        assertFalse(panel.followButton().isSelected());
        assertEquals(PatientCardPanel.HINT, panel.hintText());
    }

    @Test
    void keepsItsWidthWithAnyPatient() {
        panel.showCard(treated());
        int width = panel.getPreferredSize().width;

        panel.showCard(walking());

        assertEquals(PatientCardPanel.WIDTH, width);
        assertEquals(PatientCardPanel.WIDTH, panel.getPreferredSize().width);
    }

    @Test
    void rejectsAMissingCard() {
        assertThrows(IllegalArgumentException.class, () -> panel.showCard(null));
    }

    @Test
    void anUnknownFieldHasNoText() {
        assertEquals("", panel.valueOf("Color favorito"));
    }
}
