package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del plan para volver a simular un caso guardado.
 */
class RerunPlanTest {

    private static final CaseConfig CASE = CaseConfig.of(7, Scenario.CATASTROPHE);

    @Test
    void keepsItsData() {
        RerunPlan plan = new RerunPlan(5, CASE, HospitalConfig.standard(), "FIFO (orden de llegada)",
                List.of("1 médico de Cirugía más"));

        assertEquals(5, plan.baseRunId());
        assertEquals(CASE, plan.caseConfig());
        assertEquals(HospitalConfig.standard(), plan.hospital());
        assertEquals("FIFO (orden de llegada)", plan.strategyName());
        assertEquals(List.of("1 médico de Cirugía más"), plan.changes());
        assertTrue(plan.hasChanges());
    }

    @Test
    void repeatingTheRunAsItWasHasNoChanges() {
        RerunPlan plan = new RerunPlan(5, CASE, HospitalConfig.standard(), "Aleatoria", List.of());

        assertFalse(plan.hasChanges());
    }

    @Test
    void theChangesCannotBeModifiedFromOutside() {
        List<String> changes = new ArrayList<>(List.of("5 camas más"));
        RerunPlan plan = new RerunPlan(5, CASE, HospitalConfig.standard(), "Aleatoria", changes);
        changes.add("otro");

        assertEquals(1, plan.changes().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.changes().add("otro"));
    }

    @Test
    void rejectsInvalidData() {
        HospitalConfig hospital = HospitalConfig.standard();
        List<String> blank = List.of(" ");

        assertThrows(IllegalArgumentException.class, () -> new RerunPlan(0, CASE, hospital, "FIFO", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RerunPlan(1, null, hospital, "FIFO", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RerunPlan(1, CASE, null, "FIFO", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RerunPlan(1, CASE, hospital, " ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RerunPlan(1, CASE, hospital, "FIFO", null));
        assertThrows(IllegalArgumentException.class, () -> new RerunPlan(1, CASE, hospital, "FIFO", blank));
    }
}
