package com.medresource.ui.render;

import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los colores de la interfaz.
 */
class PaletteTest {

    @Test
    void everyZoneHasItsOwnColor() {
        Set<Color> colors = new HashSet<>();
        for (ZoneType zone : ZoneType.values()) {
            colors.add(Palette.zone(zone));
        }

        assertEquals(ZoneType.values().length, colors.size());
    }

    @Test
    void everyTriageLevelHasItsOwnColor() {
        Set<Color> colors = new HashSet<>();
        for (TriageLevel level : TriageLevel.values()) {
            colors.add(Palette.triage(level));
        }

        assertEquals(TriageLevel.values().length, colors.size());
    }

    @Test
    void theMostUrgentTriageIsRedAndTheLeastUrgentIsBlue() {
        Color urgent = Palette.triage(TriageLevel.I);
        Color calm = Palette.triage(TriageLevel.V);

        assertTrue(urgent.getRed() > urgent.getBlue());
        assertTrue(calm.getBlue() > calm.getRed());
    }

    @Test
    void theMarksAreDifferentFromEachOtherSoTheyCanBeTellApart() {
        Set<Color> marks = Set.of(Palette.CARD, Palette.OPERATING_TABLE, Palette.MORGUE_SLAB, Palette.STAFF_SEAT,
                Palette.STAFF_TABLE, Palette.WAITING_SEAT, Palette.TRIAGE_STATION, Palette.BLOOD_UNIT,
                Palette.ENTRANCE, Palette.CONSULT_DESK, Palette.DOOR);

        assertEquals(11, marks.size());
    }

    @Test
    void thePatientColorsAreDifferentFromTheTriageColors() {
        Set<Color> colors = new HashSet<>(Set.of(Palette.PATIENT_HEAD, Palette.DECEASED, Palette.BADGE));
        for (TriageLevel level : TriageLevel.values()) {
            colors.add(Palette.triage(level));
        }

        assertEquals(3 + TriageLevel.values().length, colors.size());
    }

    @Test
    void doesNotColorNothing() {
        assertThrows(IllegalArgumentException.class, () -> Palette.zone(null));
        assertThrows(IllegalArgumentException.class, () -> Palette.triage(null));
    }

    @Test
    void eachSpecialtyHasItsOwnColorDifferentFromTheTriageColors() {
        Set<Color> colors = new HashSet<>();
        for (Specialty specialty : Specialty.values()) {
            colors.add(Palette.specialty(specialty));
        }
        assertEquals(Specialty.values().length, colors.size());
        for (TriageLevel level : TriageLevel.values()) {
            assertTrue(!colors.contains(Palette.triage(level)), level.name());
        }
        assertThrows(IllegalArgumentException.class, () -> Palette.specialty(null));
    }

    @Test
    void doctorsAndNursesAreDressedDifferently() {
        assertNotEquals(Palette.DOCTOR_COAT, Palette.NURSE_UNIFORM);
    }

    private static double distance(Color first, Color second) {
        int red = first.getRed() - second.getRed();
        int green = first.getGreen() - second.getGreen();
        int blue = first.getBlue() - second.getBlue();
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    @Test
    void aNurseCannotBeMistakenForAPatientOfAnyTriageLevel() {
        for (TriageLevel level : TriageLevel.values()) {
            assertTrue(distance(Palette.NURSE_UNIFORM, Palette.triage(level)) > 80, level.name());
        }
    }

    @Test
    void theHealthGoesFromGreenToRedAsItDrops() {
        assertEquals(Palette.HEALTH_GOOD, Palette.health(100.0));
        assertEquals(Palette.HEALTH_GOOD, Palette.health(70.0));
        assertEquals(Palette.HEALTH_FAIR, Palette.health(69.9));
        assertEquals(Palette.HEALTH_FAIR, Palette.health(40.0));
        assertEquals(Palette.HEALTH_POOR, Palette.health(39.9));
        assertEquals(Palette.HEALTH_POOR, Palette.health(20.0));
        assertEquals(Palette.HEALTH_CRITICAL, Palette.health(19.9));
        assertEquals(Palette.HEALTH_CRITICAL, Palette.health(0.0));
    }

    @Test
    void theHealthColorsAndTheProgressAreDifferentFromEachOther() {
        Set<Color> colors = Set.of(Palette.HEALTH_GOOD, Palette.HEALTH_FAIR, Palette.HEALTH_POOR,
                Palette.HEALTH_CRITICAL, Palette.BAR_TRACK, Palette.TREATMENT_PROGRESS);

        assertEquals(6, colors.size());
    }

    @Test
    void aHealthOutOfRangeHasNoColor() {
        assertThrows(IllegalArgumentException.class, () -> Palette.health(-0.1));
        assertThrows(IllegalArgumentException.class, () -> Palette.health(100.1));
        assertThrows(IllegalArgumentException.class, () -> Palette.health(Double.NaN));
    }

    @Test
    void thePorterCannotBeMistakenForTheStaffNorForAPatient() {
        assertTrue(distance(Palette.PORTER_UNIFORM, Palette.NURSE_UNIFORM) > 60);
        assertTrue(distance(Palette.PORTER_UNIFORM, Palette.DOCTOR_COAT) > 60);
        for (TriageLevel level : TriageLevel.values()) {
            assertTrue(distance(Palette.PORTER_UNIFORM, Palette.triage(level)) > 60, level.name());
        }
        assertNotEquals(Palette.STRETCHER, Palette.STRETCHER_FRAME);
    }

    @Test
    void theSelectionRingCannotBeMistakenForATriageLevelNorForTheStaff() {
        for (TriageLevel level : TriageLevel.values()) {
            assertTrue(distance(Palette.SELECTION, Palette.triage(level)) > 60, level.name());
        }
        assertTrue(distance(Palette.SELECTION, Palette.NURSE_UNIFORM) > 60);
        assertTrue(distance(Palette.SELECTION, Palette.DOCTOR_COAT) > 60);
    }
}
