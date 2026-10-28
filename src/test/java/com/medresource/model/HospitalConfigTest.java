package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la configuración del hospital.
 */
class HospitalConfigTest {

    @Test
    void standardHospitalHasTheProjectSizes() {
        HospitalConfig config = HospitalConfig.standard();

        assertEquals(20, config.hospitalizationBeds());
        assertEquals(4, config.icuBeds());
        assertEquals(3, config.operatingRooms());
        assertEquals(8, config.doctors());
        assertEquals(12, config.nurses());
        assertEquals(5, config.bloodUnitsPerType());
        assertEquals(3, config.morgueCapacity());
    }

    @Test
    void configurationsWithTheSameDataAreEqual() {
        assertEquals(HospitalConfig.standard(), HospitalConfig.standard());
    }

    @Test
    void acceptsTheLimitsOfEveryRange() {
        new HospitalConfig(1, 1, 1, 1, 1, 0, 1);
        new HospitalConfig(HospitalConfig.MAX_BEDS, HospitalConfig.MAX_ICU_BEDS,
                HospitalConfig.MAX_OPERATING_ROOMS, HospitalConfig.MAX_DOCTORS, HospitalConfig.MAX_NURSES,
                HospitalConfig.MAX_BLOOD_UNITS_PER_TYPE, HospitalConfig.MAX_MORGUE_CAPACITY);
    }

    @Test
    void rejectsEveryQuantityBelowItsMinimum() {
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(0, 4, 3, 8, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 0, 3, 8, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 0, 8, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 0, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 8, 0, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 8, 12, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 8, 12, 5, 0));
    }

    @Test
    void rejectsEveryQuantityAboveItsMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(201, 4, 3, 8, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 51, 3, 8, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 21, 8, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 51, 12, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 8, 101, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 8, 12, 101, 3));
        assertThrows(IllegalArgumentException.class, () -> new HospitalConfig(20, 4, 3, 8, 12, 5, 51));
    }
}