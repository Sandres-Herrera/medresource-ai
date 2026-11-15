package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los casos para revisar y de sus motivos.
 */
class ReviewCaseTest {

    @Test
    void keepsTheCase() {
        ReviewCase review = new ReviewCase("P023", ReviewReason.LONGEST_WAIT_BEFORE_DEATH, TriageLevel.II, 95, 41.5,
                PatientStatus.DECEASED, 140);

        assertEquals("P023", review.patientCode());
        assertEquals(140, review.focusMinute());
    }

    @Test
    void everyReasonHasItsOwnText() {
        Set<String> texts = new HashSet<>();
        for (ReviewReason reason : ReviewReason.values()) {
            texts.add(reason.getDisplayName());
        }
        assertEquals(ReviewReason.values().length, texts.size());
    }

    @Test
    void rejectsIncoherentData() {
        assertThrows(IllegalArgumentException.class, () -> new ReviewCase("P001", null, TriageLevel.I, 0, 0.0,
                PatientStatus.DECEASED, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReviewCase("P001", ReviewReason.LEFT_UNATTENDED,
                TriageLevel.I, 0, 0.0, PatientStatus.WAITING, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReviewCase("P001", ReviewReason.LEFT_UNATTENDED,
                TriageLevel.I, -1, 0.0, PatientStatus.UNATTENDED, 0));
    }
}
