package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del resumen del banco de sangre.
 */
class BloodReportTest {

    private static final List<BloodUsage> TYPES = List.of(
            new BloodUsage(BloodType.O_NEGATIVE, 10, 10, 0, 220, 300),
            new BloodUsage(BloodType.A_POSITIVE, 10, 4, 6, -1, 0));

    @Test
    void addsUpTheUnitsAndListsTheTypesThatRanOut() {
        BloodReport report = new BloodReport(TYPES, 7, 5, 3, 2, 1);

        assertEquals(14, report.unitsUsed());
        assertEquals(List.of(BloodType.O_NEGATIVE), report.ranOut().stream().map(BloodUsage::type).toList());
    }

    @Test
    void theUniversalDonorCannotBeUsedMoreThanThereWereTransfusions() {
        assertThrows(IllegalArgumentException.class, () -> new BloodReport(TYPES, 2, 3, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BloodReport(TYPES, 5, 2, 3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BloodReport(null, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BloodReport(TYPES, 1, 0, 0, -1, 0));
    }
}
