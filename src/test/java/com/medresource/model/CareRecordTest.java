package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del registro de una atención.
 */
class CareRecordTest {

    private static CareRecord surgery(Map<BloodType, Integer> blood) {
        return new CareRecord("P004", BloodType.A_POSITIVE, 10, 70, 250, "D02", Specialty.SURGERY,
                List.of("E01", "E02"), "Q1", "UCI-C1", true, blood, 42.5);
    }

    @Test
    void keepsTheDataOfTheCare() {
        CareRecord care = surgery(Map.of(BloodType.A_POSITIVE, 2));

        assertEquals("P004", care.patientCode());
        assertEquals("D02", care.doctorCode());
        assertEquals(Specialty.SURGERY, care.doctorSpecialty());
        assertEquals(List.of("E01", "E02"), care.nurseCodes());
        assertEquals("Q1", care.operatingRoomCode());
        assertEquals("UCI-C1", care.bedCode());
        assertTrue(care.icuBed());
        assertEquals(42.5, care.healthAtStart(), 0.0);
    }

    @Test
    void addsUpTheBloodUnitsOfEveryDonorType() {
        CareRecord care = surgery(Map.of(BloodType.A_POSITIVE, 1, BloodType.O_NEGATIVE, 1));

        assertEquals(2, care.bloodUnits());
        assertTrue(care.usedUniversalDonor());
    }

    @Test
    void aCareWithoutTransfusionUsesNoBlood() {
        CareRecord care = surgery(Map.of());

        assertEquals(0, care.bloodUnits());
        assertFalse(care.usedUniversalDonor());
    }

    @Test
    void theStaffIsBusyUntilItIsReleased() {
        assertEquals(60, surgery(Map.of()).staffMinutes());
    }

    @Test
    void keepsItsOwnCopyOfTheNurses() {
        List<String> nurses = new ArrayList<>(List.of("E01"));
        CareRecord care = new CareRecord("P001", BloodType.O_POSITIVE, 0, 20, 20, "D01",
                Specialty.GENERAL_MEDICINE, nurses, null, null, false, Map.of(), 80.0);
        nurses.add("E02");

        assertEquals(List.of("E01"), care.nurseCodes());
        assertThrows(UnsupportedOperationException.class, () -> care.nurseCodes().add("E03"));
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new CareRecord(" ", BloodType.O_POSITIVE, 0, 0, 0,
                "D01", Specialty.SURGERY, List.of(), null, null, false, Map.of(), 50.0));
        assertThrows(IllegalArgumentException.class, () -> new CareRecord("P001", BloodType.O_POSITIVE, 10, 5,
                20, "D01", Specialty.SURGERY, List.of(), null, null, false, Map.of(), 50.0));
        assertThrows(IllegalArgumentException.class, () -> new CareRecord("P001", BloodType.O_POSITIVE, 0, 0, 0,
                "D01", Specialty.SURGERY, List.of(), null, null, true, Map.of(), 50.0));
        assertThrows(IllegalArgumentException.class, () -> new CareRecord("P001", BloodType.O_POSITIVE, 0, 0, 0,
                "D01", Specialty.SURGERY, List.of(), null, null, false, Map.of(BloodType.A_POSITIVE, 0), 50.0));
        assertThrows(IllegalArgumentException.class, () -> new CareRecord("P001", BloodType.O_POSITIVE, 0, 0, 0,
                "D01", Specialty.SURGERY, List.of(), null, null, false, Map.of(), 120.0));
    }
}
