package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del banco de sangre: existencias y compatibilidad.
 */
class BloodBankTest {

    private BloodBank bankWith(Object... typeAndAmount) {
        Map<BloodType, Integer> units = new EnumMap<>(BloodType.class);
        for (int i = 0; i < typeAndAmount.length; i += 2) {
            units.put((BloodType) typeAndAmount[i], (Integer) typeAndAmount[i + 1]);
        }
        return new BloodBank(units);
    }

    @Test
    void missingTypesStartAtZero() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 3);
        assertEquals(3, bank.available(BloodType.A_POSITIVE));
        assertEquals(0, bank.available(BloodType.B_NEGATIVE));
        assertEquals(3, bank.totalUnits());
        assertEquals(8, bank.getUnits().size());
    }

    @Test
    void invalidInitialUnitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BloodBank(null));
        assertThrows(IllegalArgumentException.class, () -> bankWith(BloodType.O_POSITIVE, -1));
    }

    @Test
    void universalDonorServesEveryType() {
        BloodBank bank = bankWith(BloodType.O_NEGATIVE, 1);
        for (BloodType recipient : BloodType.values()) {
            assertTrue(bank.hasCompatibleUnits(recipient, 1), recipient.getLabel());
        }
    }

    @Test
    void incompatibleStockIsNotEnough() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 3);
        assertTrue(bank.hasCompatibleUnits(BloodType.A_POSITIVE, 3));
        assertTrue(bank.hasCompatibleUnits(BloodType.AB_POSITIVE, 1));
        assertFalse(bank.hasCompatibleUnits(BloodType.A_POSITIVE, 4));
        assertFalse(bank.hasCompatibleUnits(BloodType.O_POSITIVE, 1));
        assertFalse(bank.hasCompatibleUnits(BloodType.B_POSITIVE, 1));
    }

    @Test
    void compatibleTypesAreAddedTogether() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 1, BloodType.O_NEGATIVE, 1);
        assertTrue(bank.hasCompatibleUnits(BloodType.A_POSITIVE, 2));
        assertFalse(bank.hasCompatibleUnits(BloodType.A_POSITIVE, 3));
    }

    @Test
    void withdrawUsesTheSameTypeFirst() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 2, BloodType.O_NEGATIVE, 2);
        bank.withdraw(BloodType.A_POSITIVE, 1);
        assertEquals(1, bank.available(BloodType.A_POSITIVE));
        assertEquals(2, bank.available(BloodType.O_NEGATIVE));
    }

    @Test
    void withdrawKeepsTheUniversalDonorForLast() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 1, BloodType.A_NEGATIVE, 1,
                BloodType.O_POSITIVE, 1, BloodType.O_NEGATIVE, 1);
        bank.withdraw(BloodType.A_POSITIVE, 3);
        assertEquals(0, bank.available(BloodType.A_POSITIVE));
        assertEquals(0, bank.available(BloodType.A_NEGATIVE));
        assertEquals(0, bank.available(BloodType.O_POSITIVE));
        assertEquals(1, bank.available(BloodType.O_NEGATIVE));
    }

    @Test
    void withdrawingMoreThanAvailableChangesNothing() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 2);
        assertThrows(IllegalStateException.class, () -> bank.withdraw(BloodType.A_POSITIVE, 3));
        assertEquals(2, bank.available(BloodType.A_POSITIVE));
    }

    @Test
    void amountMustBeAtLeastOne() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 2);
        assertThrows(IllegalArgumentException.class, () -> bank.hasCompatibleUnits(BloodType.A_POSITIVE, 0));
        assertThrows(IllegalArgumentException.class, () -> bank.withdraw(BloodType.A_POSITIVE, -1));
    }

    @Test
    void returnedUnitsCannotModifyTheBank() {
        BloodBank bank = bankWith(BloodType.A_POSITIVE, 2);
        assertThrows(UnsupportedOperationException.class, () -> bank.getUnits().put(BloodType.A_POSITIVE, 99));
        assertEquals(2, bank.available(BloodType.A_POSITIVE));
    }
}