package com.medresource.ui.controller;

import com.medresource.model.HospitalConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los tamaños de hospital ya armados.
 */
class HospitalPresetTest {

    @Test
    void eachSizeHasItsHospital() {
        assertEquals(HospitalConfig.small(), HospitalPreset.SMALL.config());
        assertEquals(HospitalConfig.standard(), HospitalPreset.STANDARD.config());
        assertEquals(HospitalConfig.large(), HospitalPreset.LARGE.config());
    }

    @Test
    void findsTheSizeOfAConfiguration() {
        for (HospitalPreset preset : HospitalPreset.values()) {
            assertEquals(preset, HospitalPreset.matching(preset.config()).orElseThrow());
        }
    }

    @Test
    void aChangedHospitalHasNoSize() {
        HospitalConfig custom = HospitalField.DOCTORS.with(HospitalConfig.standard(), 17);

        assertTrue(HospitalPreset.matching(custom).isEmpty());
        assertTrue(HospitalPreset.matching(null).isEmpty());
    }

    @Test
    void hasTheNamesOfTheButtons() {
        assertEquals("Pequeño", HospitalPreset.SMALL.getDisplayName());
        assertEquals("Estándar", HospitalPreset.STANDARD.getDisplayName());
        assertEquals("Grande", HospitalPreset.LARGE.getDisplayName());
    }
}
