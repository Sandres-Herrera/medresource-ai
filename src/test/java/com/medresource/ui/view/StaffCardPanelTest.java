package com.medresource.ui.view;

import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.ui.controller.StaffCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la ficha de un médico o de un enfermero.
 */
class StaffCardPanelTest {

    private final StaffCardPanel panel = new StaffCardPanel();

    private static StaffCard doctorWith(int patients) {
        List<StaffCard.Attended> attended = new ArrayList<>();
        for (int index = 1; index <= patients; index++) {
            attended.add(new StaffCard.Attended(String.format("P%03d", index), TriageLevel.II, index,
                    "Dado de alta"));
        }
        return new StaffCard("D03", Specialty.SURGERY, Sex.FEMALE, "Libre", null, attended);
    }

    @Test
    void startsHidden() {
        assertFalse(panel.isVisible());
        assertNull(panel.getStaffCode());
    }

    @Test
    void showsTheDoctorHisSpecialtyAndThePatientsHeAttended() {
        panel.showCard(doctorWith(2));

        assertTrue(panel.isVisible());
        assertEquals("D03", panel.getStaffCode());
        assertEquals("Médico D03", panel.titleText());
        assertEquals("Cirugía", panel.subtitleText());
        assertEquals(List.of("P001 · min 1 · Dado de alta", "P002 · min 2 · Dado de alta"), panel.rowTexts());
    }

    @Test
    void aNurseShowsNursing() {
        panel.showCard(new StaffCard("E04", null, Sex.MALE, "Atiende a P009", "P009", List.of()));

        assertEquals("Enfermería · E04", panel.titleText());
        assertTrue(panel.rowTexts().isEmpty());
    }

    @Test
    void theListShowsOnlyTheMostRecentPatients() {
        panel.showCard(doctorWith(StaffCardPanel.MAX_ROWS + 3));

        assertEquals(StaffCardPanel.MAX_ROWS, panel.rowTexts().size());
        assertTrue(panel.rowTexts().get(0).startsWith("P004"));
    }

    @Test
    void aClickOnAPatientChoosesHimAndTheCrossClosesTheCard() {
        List<String> chosen = new ArrayList<>();
        List<Boolean> closed = new ArrayList<>();
        panel.onPatientClicked(chosen::add);
        panel.onClose(() -> closed.add(true));
        panel.showCard(doctorWith(1));

        panel.clickPatient("P001");
        panel.clickClose();

        assertEquals(List.of("P001"), chosen);
        assertEquals(List.of(true), closed);
        assertFalse(panel.isVisible());
        assertNull(panel.getStaffCode());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> panel.showCard(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onPatientClicked(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onClose(null));
    }
}
