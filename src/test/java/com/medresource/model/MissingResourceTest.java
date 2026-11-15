package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pruebas de los recursos que le pueden faltar a un paciente que espera.
 */
class MissingResourceTest {

    @Test
    void thereAreEightKindsOfMissingResource() {
        assertEquals(8, MissingResource.values().length);
    }

    @Test
    void everyResourceHasItsOwnName() {
        Set<String> names = new HashSet<>();
        for (MissingResource resource : MissingResource.values()) {
            assertFalse(resource.getDisplayName().isBlank());
            names.add(resource.getDisplayName());
        }
        assertEquals(MissingResource.values().length, names.size());
    }

    @Test
    void theDoctorIsShownAsACompatibleDoctor() {
        assertEquals("Médico compatible", MissingResource.DOCTOR.getDisplayName());
        assertEquals("Quirófano", MissingResource.OPERATING_ROOM.getDisplayName());
        assertEquals(MissingResource.TRIAGE_STATION, Arrays.stream(MissingResource.values()).findFirst().orElseThrow());
    }
}
