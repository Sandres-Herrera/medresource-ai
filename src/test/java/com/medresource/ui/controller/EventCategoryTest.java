package com.medresource.ui.controller;

import com.medresource.model.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los grupos de eventos del filtro del registro.
 */
class EventCategoryTest {

    @Test
    void allIncludesEveryType() {
        for (EventType type : EventType.values()) {
            assertTrue(EventCategory.ALL.includes(type), type.name());
        }
    }

    @Test
    void everyTypeIsInExactlyOneGroupBesidesAll() {
        for (EventType type : EventType.values()) {
            int groups = 0;
            for (EventCategory category : EventCategory.values()) {
                if (category != EventCategory.ALL && category.includes(type)) {
                    groups++;
                }
            }
            assertEquals(1, groups, type.name());
        }
    }

    @Test
    void eachGroupHasTheTypesOfItsPartOfTheVisit() {
        assertTrue(EventCategory.ARRIVALS.includes(EventType.TRIAGE_ASSIGNED));
        assertTrue(EventCategory.CARE.includes(EventType.FIRST_AID));
        assertTrue(EventCategory.CARE.includes(EventType.RECOVERY_STARTED));
        assertTrue(EventCategory.OUTCOMES.includes(EventType.UNATTENDED));
        assertTrue(EventCategory.DEATHS.includes(EventType.MORGUE_RELEASE));
        assertFalse(EventCategory.DEATHS.includes(EventType.DISCHARGE));
        assertFalse(EventCategory.ARRIVALS.includes(EventType.DEATH));
    }

    @Test
    void showsItsNameInTheList() {
        assertEquals("Todos los eventos", EventCategory.ALL.toString());
        assertEquals("Fallecimientos y morgue", EventCategory.DEATHS.getDisplayName());
    }

    @Test
    void rejectsAMissingType() {
        assertThrows(IllegalArgumentException.class, () -> EventCategory.CARE.includes(null));
    }
}
