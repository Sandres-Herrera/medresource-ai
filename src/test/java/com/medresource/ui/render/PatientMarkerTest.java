package com.medresource.ui.render;

import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}