package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pruebas de las etapas de la generación del reporte.
 */
class ReportStageTest {

    @Test
    void theStagesGoInTheOrderTheyAreDone() {
        assertEquals(3, ReportStage.count());
        assertEquals(1, ReportStage.METRICS.number());
        assertEquals(2, ReportStage.WHAT_IF.number());
        assertEquals(3, ReportStage.WRITING.number());
    }

    @Test
    void everyStageHasAText() {
        for (ReportStage stage : ReportStage.values()) {
            assertFalse(stage.getLabel().isBlank(), stage.name());
        }
        assertEquals("Redactando el texto del reporte", ReportStage.WRITING.getLabel());
    }
}
