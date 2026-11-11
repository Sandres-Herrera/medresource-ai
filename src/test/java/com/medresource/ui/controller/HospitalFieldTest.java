package com.medresource.ui.controller;

import com.medresource.model.HospitalConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los recursos del hospital que se cambian en el formulario.
 */
class HospitalFieldTest {

    private static final HospitalConfig STANDARD = HospitalConfig.standard();

    @Test
    void readsEachResourceOfTheHospital() {
        assertEquals(30, HospitalField.BEDS.valueIn(STANDARD));
        assertEquals(6, HospitalField.ICU_BEDS.valueIn(STANDARD));
        assertEquals(4, HospitalField.OPERATING_ROOMS.valueIn(STANDARD));
        assertEquals(16, HospitalField.DOCTORS.valueIn(STANDARD));
        assertEquals(24, HospitalField.NURSES.valueIn(STANDARD));
        assertEquals(10, HospitalField.BLOOD.valueIn(STANDARD));
        assertEquals(5, HospitalField.MORGUE.valueIn(STANDARD));
    }

    @Test
    void changesOnlyItsOwnResource() {
        for (HospitalField field : HospitalField.values()) {
            HospitalConfig changed = field.with(STANDARD, field.getMax());

            for (HospitalField other : HospitalField.values()) {
                int expected = other == field ? field.getMax() : other.valueIn(STANDARD);
                assertEquals(expected, other.valueIn(changed), field + " cambió " + other);
            }
        }
    }

    @Test
    void theRangesAreTheOnesTheHospitalAccepts() {
        for (HospitalField field : HospitalField.values()) {
            field.with(STANDARD, field.getMin());
            field.with(STANDARD, field.getMax());
            assertThrows(IllegalArgumentException.class, () -> field.with(STANDARD, field.getMax() + 1), field.name());
            assertThrows(IllegalArgumentException.class, () -> field.with(STANDARD, field.getMin() - 1), field.name());
        }
        assertEquals(0, HospitalField.BLOOD.getMin());
        assertEquals(HospitalConfig.MAX_BEDS, HospitalField.BEDS.getMax());
    }

    @Test
    void clampsAQuantityToItsRange() {
        assertEquals(1, HospitalField.DOCTORS.clamp(-5));
        assertEquals(12, HospitalField.DOCTORS.clamp(12));
        assertEquals(HospitalConfig.MAX_DOCTORS, HospitalField.DOCTORS.clamp(999));
    }

    @Test
    void hasTheNamesOfTheForm() {
        assertEquals("Camas UCI", HospitalField.ICU_BEDS.getDisplayName());
        assertEquals("Cupos morgue", HospitalField.MORGUE.getDisplayName());
    }

    @Test
    void rejectsAMissingHospital() {
        assertThrows(IllegalArgumentException.class, () -> HospitalField.BEDS.valueIn(null));
        assertThrows(IllegalArgumentException.class, () -> HospitalField.BEDS.with(null, 3));
    }
}
