package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del avance de la generación del reporte.
 */
class ReportProgressTest {

    @Test
    void itStartsInTheFirstStageWithoutAnEstimate() {
        ReportProgress start = ReportProgress.start();

        assertEquals(ReportStage.METRICS, start.stage());
        assertFalse(start.isEstimated());
        assertEquals(0.0, start.fraction(), 1e-9);
    }

    @Test
    void theDonePartIsTheTimeSpentOverTheTotalTime() {
        ReportProgress progress = new ReportProgress(ReportStage.WHAT_IF, 6000, 18000);

        assertTrue(progress.isEstimated());
        assertEquals(0.25, progress.fraction(), 1e-9);
    }

    @Test
    void withoutAnEstimateTheBarStaysAtTheStartOfTheStage() {
        assertEquals(1.0 / 3, new ReportProgress(ReportStage.WHAT_IF, 900, ReportProgress.UNKNOWN).fraction(),
                1e-9);
        assertEquals(2.0 / 3, new ReportProgress(ReportStage.WRITING, 0, 0).fraction(), 1e-9);
        assertEquals(1.0, new ReportProgress(ReportStage.WRITING, 500, 0).fraction(), 1e-9);
    }

    @Test
    void laterTheTimeGoesOnAndTheRemainingTimeGoesDown() {
        ReportProgress later = new ReportProgress(ReportStage.WRITING, 20000, 9000).after(4000);

        assertEquals(ReportStage.WRITING, later.stage());
        assertEquals(24000, later.elapsedMillis());
        assertEquals(5000, later.remainingMillis());
    }

    @Test
    void whileItIsNotOverItNeverSaysThatNothingIsLeft() {
        assertEquals(ReportProgress.MIN_REMAINING_MILLIS,
                new ReportProgress(ReportStage.WRITING, 1000, 3000).after(60000).remainingMillis());
        assertEquals(400, new ReportProgress(ReportStage.WRITING, 1000, 400).after(300).remainingMillis());
        assertEquals(ReportProgress.UNKNOWN,
                new ReportProgress(ReportStage.METRICS, 0, ReportProgress.UNKNOWN).after(700).remainingMillis());
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReportProgress(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReportProgress(ReportStage.METRICS, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReportProgress(ReportStage.METRICS, 0, -2));
        assertThrows(IllegalArgumentException.class, () -> ReportProgress.start().after(-1));
    }
}
