package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las reglas de compatibilidad sanguínea de {@link BloodType}.
 */
class BloodTypeTest {

    @Test
    void oNegativeIsUniversalDonor() {
        for (BloodType receiver : BloodType.values()) {
            assertTrue(receiver.canReceiveFrom(BloodType.O_NEGATIVE),
                    receiver + " debería poder recibir de O-");
        }
    }

    @Test
    void abPositiveIsUniversalReceiver() {
        for (BloodType donor : BloodType.values()) {
            assertTrue(BloodType.AB_POSITIVE.canReceiveFrom(donor),
                    "AB+ debería poder recibir de " + donor);
        }
    }

    @Test
    void everyTypeCanReceiveFromItself() {
        for (BloodType type : BloodType.values()) {
            assertTrue(type.canReceiveFrom(type), type + " debería poder recibir de sí mismo");
        }
    }

    @Test
    void aPositiveCannotReceiveFromBPositive() {
        assertFalse(BloodType.A_POSITIVE.canReceiveFrom(BloodType.B_POSITIVE));
    }

    @Test
    void rhNegativeCannotReceiveFromRhPositive() {
        assertFalse(BloodType.O_NEGATIVE.canReceiveFrom(BloodType.O_POSITIVE));
        assertFalse(BloodType.A_NEGATIVE.canReceiveFrom(BloodType.A_POSITIVE));
    }
}