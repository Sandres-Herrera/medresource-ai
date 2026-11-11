package com.medresource.ui.controller;

import com.medresource.model.BloodType;
import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import com.medresource.ui.render.PatientMarker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la ficha del paciente.
 */
class PatientCardTest {

    private static PatientCard card(int age, double health, double progress) {
        return new PatientCard("P007", age, Sex.MALE, BloodType.O_POSITIVE, "Dengue", List.of("Fiebre alta"),
                TriageLevel.III, "En tratamiento", "Hospitalización · H1-C2", false, health, progress, 12, 30,
                List.of("D03 · Medicina interna"));
    }

    @Test
    void writesTheAgeAndTheSexInOneLine() {
        assertEquals("34 años · Masculino", card(34, 80, 0.5).ageAndSex());
        assertEquals("1 año · Masculino", card(1, 80, 0.5).ageAndSex());
    }

    @Test
    void roundsTheHealthAndTheProgress() {
        PatientCard card = card(34, 72.6, 0.254);

        assertEquals(73, card.healthPercent());
        assertTrue(card.inTreatment());
        assertEquals(25, card.progressPercent());
    }

    @Test
    void withoutTreatmentThereIsNoProgress() {
        PatientCard card = card(34, 80, PatientMarker.NO_PROGRESS);

        assertFalse(card.inTreatment());
        assertEquals(0, card.progressPercent());
    }

    @Test
    void theListsDoNotChangeWhenTheOriginalsChange() {
        List<String> caregivers = new ArrayList<>(List.of("E01 · Enfermería"));
        PatientCard card = new PatientCard("P001", 20, Sex.FEMALE, BloodType.A_NEGATIVE, "Gripa", List.of(), null,
                "Esperando su atención", "Sala de espera", true, 90, PatientMarker.NO_PROGRESS, 0, 3, caregivers);

        caregivers.clear();

        assertEquals(List.of("E01 · Enfermería"), card.caregivers());
        assertThrows(UnsupportedOperationException.class, () -> card.symptoms().add("Tos"));
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> card(-1, 80, 0.5));
        assertThrows(IllegalArgumentException.class, () -> card(34, 101, 0.5));
        assertThrows(IllegalArgumentException.class, () -> card(34, 80, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new PatientCard(" ", 20, Sex.FEMALE, BloodType.A_NEGATIVE,
                "Gripa", List.of(), null, "Esperando", "Sala de espera", false, 90, PatientMarker.NO_PROGRESS, 0, 0,
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PatientCard("P001", 20, Sex.FEMALE, null,
                "Gripa", List.of(), null, "Esperando", "Sala de espera", false, 90, PatientMarker.NO_PROGRESS, 0, 0,
                List.of()));
    }
}
