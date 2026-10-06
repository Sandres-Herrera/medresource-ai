package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link TriageLevel} y de la relación entre síntomas y triage.
 */
class TriageLevelTest {

    @Test
    void levelOneIsMoreUrgentThanLevelTwo() {
        assertTrue(TriageLevel.I.isMoreUrgentThan(TriageLevel.II));
        assertFalse(TriageLevel.II.isMoreUrgentThan(TriageLevel.I));
    }

    @Test
    void levelIsNotMoreUrgentThanItself() {
        assertFalse(TriageLevel.III.isMoreUrgentThan(TriageLevel.III));
    }

    @Test
    void levelsAreDeclaredFromMostToLeastUrgent() {
        TriageLevel[] levels = TriageLevel.values();
        for (int i = 0; i < levels.length; i++) {
            assertEquals(i + 1, levels[i].getLevel());
        }
    }

    @Test
    void levelTwoMustBeAttendedWithinThirtyMinutes() {
        assertEquals(30, TriageLevel.II.getTargetMinutes());
    }

    @Test
    void unconsciousPatientIsTriageOne() {
        assertEquals(TriageLevel.I, Symptom.UNCONSCIOUS.getTriageLevel());
    }

    @Test
    void congestionIsTriageFive() {
        assertEquals(TriageLevel.V, Symptom.CONGESTION.getTriageLevel());
    }
}