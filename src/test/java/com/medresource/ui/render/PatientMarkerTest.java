package com.medresource.ui.render;

import com.medresource.model.Patient;
import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los datos para dibujar un paciente.
 */
class PatientMarkerTest {

    @Test
    void keepsItsData() {
        PatientMarker marker = new PatientMarker("P001", TriageLevel.II, Sex.FEMALE, true, false, 10.5, 20.5);

        assertEquals("P001", marker.code());
        assertEquals(TriageLevel.II, marker.triage());
        assertEquals(Sex.FEMALE, marker.sex());
        assertTrue(marker.minor());
        assertEquals(10.5, marker.x(), 0.0);
        assertEquals(20.5, marker.y(), 0.0);
    }

    @Test
    void aPatientWithoutTriageIsAccepted() {
        assertEquals(null, new PatientMarker("P001", null, Sex.MALE, false, false, 0, 0).triage());
    }

    @Test
    void rejectsAMissingCodeOrSex() {
        assertThrows(IllegalArgumentException.class, () -> new PatientMarker(null, TriageLevel.I, Sex.MALE, false, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PatientMarker(" ", TriageLevel.I, Sex.MALE, false, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PatientMarker("P001", TriageLevel.I, null, false, false, 0, 0));
    }

    @Test
    void withoutHealthNorProgressThePatientIsHealthyAndNotInTreatment() {
        PatientMarker marker = new PatientMarker("P001", TriageLevel.II, Sex.FEMALE, false, false, 0, 0);

        assertEquals(Patient.MAX_HEALTH, marker.health(), 0.0);
        assertEquals(PatientMarker.NO_PROGRESS, marker.progress(), 0.0);
        assertFalse(marker.inTreatment());
    }

    @Test
    void keepsTheHealthAndTheProgressOfTheTreatment() {
        PatientMarker marker = new PatientMarker("P001", TriageLevel.II, Sex.FEMALE, false, false, 0, 0, 35.5,
                0.25);

        assertEquals(35.5, marker.health(), 0.0);
        assertEquals(0.25, marker.progress(), 0.0);
        assertTrue(marker.inTreatment());
    }

    @Test
    void rejectsAHealthOrAProgressOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new PatientMarker("P001", null, Sex.MALE, false, false, 0, 0, -1, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new PatientMarker("P001", null, Sex.MALE, false, false, 0, 0, 101, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new PatientMarker("P001", null, Sex.MALE, false, false, 0, 0, Double.NaN, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new PatientMarker("P001", null, Sex.MALE, false, false, 0, 0, 50, 1.5));
        assertThrows(IllegalArgumentException.class,
                () -> new PatientMarker("P001", null, Sex.MALE, false, false, 0, 0, 50, -0.5));
    }

    @Test
    void aPatientIsOnFootUnlessHeIsOnAStretcher() {
        PatientMarker walking = new PatientMarker("P001", TriageLevel.II, Sex.FEMALE, false, false, 0, 0, 50, 0.5);
        PatientMarker lying = new PatientMarker("P001", TriageLevel.II, Sex.FEMALE, false, false, 0, 0, 50, 0.5,
                true, false);

        assertFalse(walking.onStretcher());
        assertFalse(walking.moving());
        assertTrue(lying.onStretcher());
        assertFalse(lying.isCarried());
    }

    @Test
    void aPatientIsCarriedOnlyWhileHisStretcherMoves() {
        assertTrue(new PatientMarker("P001", null, Sex.MALE, false, true, 0, 0, 0, PatientMarker.NO_PROGRESS,
                true, true).isCarried());
        assertFalse(new PatientMarker("P001", null, Sex.MALE, false, false, 0, 0, 80, PatientMarker.NO_PROGRESS,
                false, true).isCarried());
    }
}
