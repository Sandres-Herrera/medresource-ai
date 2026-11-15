package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la ocupación de un grupo de recursos.
 */
class ResourceUsageTest {

    @Test
    void aGroupThatWasFullIsNotUnderused() {
        ResourceUsage usage = new ResourceUsage(ResourceGroup.OPERATING_ROOMS, 4, 0.81, 4, 150);

        assertEquals(1.0, usage.peakOccupancy(), 1e-12);
        assertTrue(usage.wasFull());
        assertFalse(usage.isUnderused());
    }

    @Test
    void aGroupBelowTheLimitIsUnderused() {
        ResourceUsage usage = new ResourceUsage(ResourceGroup.BEDS, 30, 0.35, 12, 0);

        assertEquals(0.4, usage.peakOccupancy(), 1e-12);
        assertFalse(usage.wasFull());
        assertTrue(usage.isUnderused());
    }

    @Test
    void everyGroupHasAName() {
        assertEquals(6, ResourceGroup.values().length);
        assertEquals("Camas de UCI", ResourceGroup.ICU_BEDS.getDisplayName());
    }

    @Test
    void rejectsIncoherentData() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceUsage(null, 1, 0.5, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResourceUsage(ResourceGroup.BEDS, 0, 0.5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResourceUsage(ResourceGroup.BEDS, 5, 1.2, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResourceUsage(ResourceGroup.BEDS, 5, 0.5, 6, 0));
    }
}
