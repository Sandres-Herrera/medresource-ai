package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los recursos que se pueden agregar al hospital.
 */
class ExtraResourceTest {

    @Test
    void everyResourceAddsAtLeastOne() {
        for (ExtraResource resource : ExtraResource.values()) {
            assertTrue(resource.getAmount() >= 1, resource.name());
        }
    }

    @Test
    void namesTheAmountInSingularOrPlural() {
        assertEquals("5 camas de hospitalización", ExtraResource.BEDS.describeAmount());
        assertEquals("1 quirófano", ExtraResource.OPERATING_ROOMS.describeAmount());
        assertEquals("2 enfermeros", ExtraResource.NURSES.describeAmount());
    }

    @Test
    void thereAreSevenKindsOfResource() {
        assertEquals(7, ExtraResource.values().length);
    }
}
