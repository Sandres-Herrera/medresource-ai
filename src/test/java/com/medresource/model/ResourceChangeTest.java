package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los cambios de recursos del análisis «¿y si…?».
 */
class ResourceChangeTest {

    private static final HospitalConfig STANDARD = HospitalConfig.standard();

    @Test
    void addsTheAmountOfTheResourceToTheHospital() {
        assertEquals(35, ResourceChange.of(ExtraResource.BEDS).applyTo(STANDARD).hospitalizationBeds());
        assertEquals(7, ResourceChange.of(ExtraResource.ICU_BEDS).applyTo(STANDARD).icuBeds());
        assertEquals(5, ResourceChange.of(ExtraResource.OPERATING_ROOMS).applyTo(STANDARD).operatingRooms());
        assertEquals(26, ResourceChange.of(ExtraResource.NURSES).applyTo(STANDARD).nurses());
        assertEquals(15, ResourceChange.of(ExtraResource.BLOOD).applyTo(STANDARD).bloodUnitsPerType());
        assertEquals(7, ResourceChange.of(ExtraResource.MORGUE).applyTo(STANDARD).morgueCapacity());
    }

    @Test
    void aDoctorKeepsTheConfigurationAndAddsItsSpecialtyApart() {
        ResourceChange surgeon = ResourceChange.doctor(Specialty.SURGERY);

        assertSame(STANDARD, surgeon.applyTo(STANDARD));
        assertEquals(List.of(Specialty.SURGERY), surgeon.extraDoctors());
        assertTrue(ResourceChange.of(ExtraResource.BEDS).extraDoctors().isEmpty());
    }

    @Test
    void describesTheChangeForTheReport() {
        assertEquals("1 quirófano más", ResourceChange.of(ExtraResource.OPERATING_ROOMS).describe());
        assertEquals("1 médico de Cirugía más", ResourceChange.doctor(Specialty.SURGERY).describe());
        assertEquals("5 camas de hospitalización más", ResourceChange.of(ExtraResource.BEDS).describe());
    }

    @Test
    void listsEveryChangeWithOneDoctorPerSpecialty() {
        List<ResourceChange> changes = ResourceChange.allFor(STANDARD);

        assertEquals(ExtraResource.values().length - 1 + Specialty.values().length, changes.size());
        assertEquals(ResourceChange.of(ExtraResource.BEDS), changes.get(0));
        assertEquals(ResourceChange.doctor(Specialty.GENERAL_MEDICINE), changes.get(3));
    }

    @Test
    void aChangeThatPassesTheMaximumIsLeftOut() {
        HospitalConfig full = new HospitalConfig(HospitalConfig.MAX_BEDS, 6, HospitalConfig.MAX_OPERATING_ROOMS, 16,
                24, 10, 5);

        assertFalse(ResourceChange.of(ExtraResource.BEDS).fitsIn(full));
        assertFalse(ResourceChange.allFor(full).contains(ResourceChange.of(ExtraResource.OPERATING_ROOMS)));
        assertThrows(IllegalArgumentException.class, () -> ResourceChange.of(ExtraResource.BEDS).applyTo(full));
    }

    @Test
    void onlyADoctorHasASpecialty() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceChange(ExtraResource.DOCTOR, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceChange(ExtraResource.BEDS, Specialty.SURGERY));
        assertThrows(IllegalArgumentException.class, () -> new ResourceChange(null, null));
    }
}
