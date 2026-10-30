package com.medresource.services.strategy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del resumen de un entrenamiento.
 */
class TrainingReportTest {

    @Test
    void reportsTheFirstAndTheLastLoss() {
        TrainingReport report = new TrainingReport(100, List.of(0.5, 0.3, 0.1));

        assertEquals(100, report.examples());
        assertEquals(0.5, report.firstLoss());
        assertEquals(0.1, report.lastLoss());
    }

    @Test
    void oneEpochIsBothTheFirstAndTheLast() {
        TrainingReport report = new TrainingReport(1, List.of(0.2));

        assertEquals(0.2, report.firstLoss());
        assertEquals(0.2, report.lastLoss());
    }

    @Test
    void keepsItsOwnCopyOfTheLosses() {
        List<Double> losses = new ArrayList<>(List.of(0.5, 0.3));
        TrainingReport report = new TrainingReport(10, losses);

        losses.add(0.1);

        assertEquals(List.of(0.5, 0.3), report.epochLosses());
        assertThrows(UnsupportedOperationException.class, () -> report.epochLosses().add(0.0));
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingReport(0, List.of(0.5)));
        assertThrows(IllegalArgumentException.class, () -> new TrainingReport(10, null));
        assertThrows(IllegalArgumentException.class, () -> new TrainingReport(10, List.of()));
    }
}