package com.medresource.ui.controller;

import com.medresource.model.BloodType;
import com.medresource.model.Sex;
import com.medresource.model.Symptom;
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
        return new PatientCard("P007", age, Sex.MALE, BloodType.O_POSITIVE, "Dengue", List.of(Symptom.HIGH_FEVER),
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
        assertThrows(UnsupportedOperationException.class, () -> card.symptoms().add(Symptom.COUGH));
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

    @Test
    void theWorstSymptomDefinesTheTriage() {
        PatientCard card = new PatientCard("P002", 40, Sex.MALE, BloodType.O_POSITIVE, "Fractura expuesta",
                List.of(Symptom.LIMB_DEFORMITY, Symptom.EXPOSED_BONE, Symptom.FEVER), TriageLevel.II, "En tratamiento",
                "Quirófanos · Q1", false, 60, 0.2, 5, 10, List.of());

        assertEquals(TriageLevel.II, card.worstSymptomLevel().orElseThrow());
        assertTrue(card(34, 80, 0.5).worstSymptomLevel().isPresent());
    }

    @Test
    void knowsWhenThePatientCannotWalk() {
        PatientCard unconscious = new PatientCard("P003", 60, Sex.FEMALE, BloodType.A_POSITIVE, "Desmayo",
                List.of(Symptom.UNCONSCIOUS), TriageLevel.I, "Pase directo", "Triage", true, 40,
                PatientMarker.NO_PROGRESS, 0, 1, List.of());

        assertTrue(unconscious.cannotWalk());
        assertFalse(card(34, 80, 0.5).cannotWalk());
    }

    @Test
    void withoutSymptomsThereIsNoWorstLevel() {
        PatientCard card = new PatientCard("P004", 20, Sex.FEMALE, BloodType.A_NEGATIVE, "Gripa", List.of(), null,
                "Esperando", "Sala de espera", false, 90, PatientMarker.NO_PROGRESS, 0, 3, List.of());

        assertTrue(card.worstSymptomLevel().isEmpty());
    }
}
