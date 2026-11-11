package com.medresource.ui.render;

import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la búsqueda del paciente al que se le hizo clic.
 */
class PatientPickerTest {

    private static final double OFFSET = PatientPainter.OFFSET_X;

    private static PatientMarker at(String code, double x, double y) {
        return new PatientMarker(code, TriageLevel.III, Sex.FEMALE, false, false, x, y);
    }

    @Test
    void findsThePatientWhoseFigureIsUnderThePoint() {
        List<PatientMarker> markers = List.of(at("P001", 100, 100), at("P002", 300, 100));

        assertEquals("P001", PatientPicker.pick(markers, 100 + OFFSET, 100).orElseThrow());
        assertEquals("P002", PatientPicker.pick(markers, 300 + OFFSET + 5, 95).orElseThrow());
    }

    @Test
    void findsNobodyFarFromEveryFigure() {
        List<PatientMarker> markers = List.of(at("P001", 100, 100));

        assertTrue(PatientPicker.pick(markers, 100 + OFFSET + PatientPicker.PICK_RADIUS + 1, 100).isEmpty());
        assertTrue(PatientPicker.pick(List.of(), 0, 0).isEmpty());
    }

    @Test
    void choosesTheClosestAndOnATieTheOneDrawnOnTop() {
        List<PatientMarker> close = List.of(at("P001", 100, 100), at("P002", 110, 100));
        List<PatientMarker> tied = List.of(at("P001", 100, 100), at("P002", 100, 100));

        assertEquals("P002", PatientPicker.pick(close, 110 + OFFSET + 2, 100).orElseThrow());
        assertEquals("P002", PatientPicker.pick(tied, 100 + OFFSET, 100).orElseThrow());
    }

    @Test
    void rejectsAMissingList() {
        assertThrows(IllegalArgumentException.class, () -> PatientPicker.pick(null, 0, 0));
    }
}
