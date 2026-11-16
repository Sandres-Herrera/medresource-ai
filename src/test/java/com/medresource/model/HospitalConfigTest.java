package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la configuración del hospital.
 */
class HospitalConfigTest {

    @Test
    void standardHospitalHasTheProjectSizes() {
        HospitalConfig config = HospitalConfig.standard();

        assertEquals(30, config.hospitalizationBeds());
        assertEquals(6, config.icuBeds());
        assertEquals(4, config.operatingRooms());
        assertEquals(16, config.doctors());
        assertEquals(24, config.nurses());
        assertEquals(10, config.bloodUnitsPerType());
        assertEquals(5, config.morgueCapacity());
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

    @Test
    void theSmallHospitalIsHalfTheStandardOne() {
        assertEquals(new HospitalConfig(15, 3, 2, 8, 12, 5, 3), HospitalConfig.small());
    }

    @Test
    void theLargeHospitalIsTwiceTheStandardOne() {
        assertEquals(new HospitalConfig(60, 12, 8, 32, 48, 20, 10), HospitalConfig.large());
    }

    // ---------- médicos extra ----------

    @Test
    void aHospitalWithoutExtraDataHasNoExtraDoctors() {
        HospitalConfig config = HospitalConfig.standard();

        assertEquals(List.of(), config.extraDoctors());
        assertEquals(16, config.totalDoctors());
        assertEquals("ninguno", config.describeExtraDoctors());
    }

    @Test
    void theExtraDoctorsAreAddedToTheTotal() {
        HospitalConfig config = HospitalConfig.standard()
                .withExtraDoctors(List.of(Specialty.SURGERY, Specialty.PEDIATRICS, Specialty.SURGERY));

        assertEquals(16, config.doctors());
        assertEquals(19, config.totalDoctors());
        assertEquals(2, config.extraDoctorsOf(Specialty.SURGERY));
        assertEquals(1, config.extraDoctorsOf(Specialty.PEDIATRICS));
        assertEquals(0, config.extraDoctorsOf(Specialty.TRAUMATOLOGY));
    }

    @Test
    void theOrderOfTheExtraDoctorsDoesNotMatter() {
        HospitalConfig first = HospitalConfig.standard()
                .withExtraDoctors(List.of(Specialty.SURGERY, Specialty.PEDIATRICS));
        HospitalConfig second = HospitalConfig.standard()
                .withExtraDoctors(List.of(Specialty.PEDIATRICS, Specialty.SURGERY));

        assertEquals(first, second);
        assertEquals(List.of(Specialty.PEDIATRICS, Specialty.SURGERY), first.extraDoctors());
        assertNotEquals(HospitalConfig.standard(), first);
    }

    @Test
    void theExtraDoctorsAreDescribedBySpecialty() {
        HospitalConfig config = HospitalConfig.standard()
                .withExtraDoctors(List.of(Specialty.PEDIATRICS, Specialty.SURGERY, Specialty.PEDIATRICS));

        assertEquals("2 de Pediatría, 1 de Cirugía", config.describeExtraDoctors());
    }

    @Test
    void aMissingListMeansNoExtraDoctors() {
        HospitalConfig config = new HospitalConfig(30, 6, 4, 16, 24, 10, 5, null);

        assertEquals(HospitalConfig.standard(), config);
    }

    @Test
    void theListOfExtraDoctorsCannotBeChanged() {
        List<Specialty> doctors = new ArrayList<>(List.of(Specialty.SURGERY));
        HospitalConfig config = HospitalConfig.standard().withExtraDoctors(doctors);
        doctors.add(Specialty.PEDIATRICS);

        assertEquals(1, config.extraDoctors().size());
        assertThrows(UnsupportedOperationException.class, () -> config.extraDoctors().add(Specialty.SURGERY));
    }

    @Test
    void rejectsInvalidExtraDoctors() {
        HospitalConfig config = HospitalConfig.standard();
        List<Specialty> withoutSpecialty = new ArrayList<>();
        withoutSpecialty.add(null);
        List<Specialty> tooMany = Collections.nCopies(HospitalConfig.MAX_EXTRA_DOCTORS + 1, Specialty.SURGERY);

        assertThrows(IllegalArgumentException.class, () -> config.withExtraDoctors(null));
        assertThrows(IllegalArgumentException.class, () -> config.withExtraDoctors(withoutSpecialty));
        assertThrows(IllegalArgumentException.class, () -> config.withExtraDoctors(tooMany));
        assertThrows(IllegalArgumentException.class, () -> config.extraDoctorsOf(null));
        assertEquals(HospitalConfig.MAX_EXTRA_DOCTORS, config.withExtraDoctors(
                Collections.nCopies(HospitalConfig.MAX_EXTRA_DOCTORS, Specialty.SURGERY)).extraDoctors().size());
    }
}
