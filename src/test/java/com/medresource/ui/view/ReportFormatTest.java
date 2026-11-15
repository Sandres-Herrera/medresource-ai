package com.medresource.ui.view;

import com.medresource.model.ReportProgress;
import com.medresource.model.ReportStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de cómo se escriben los números de la ventana del reporte.
 */
class ReportFormatTest {

    private static String remaining(long millis) {
        return ReportFormat.remaining(new ReportProgress(ReportStage.WHAT_IF, 1000, millis));
    }

    @Test
    void decimalsUseACommaAndAtMostOneDigit() {
        assertEquals("12,3", ReportFormat.decimal(12.345));
        assertEquals("12,4", ReportFormat.decimal(12.35));
        assertEquals("13", ReportFormat.decimal(13.0));
        assertEquals("0", ReportFormat.decimal(0.04));
        assertEquals("-0,8", ReportFormat.decimal(-0.75));
        assertEquals("—", ReportFormat.decimal(Double.NaN));
    }

    @Test
    void percentagesComeFromAPartOfOne() {
        assertEquals("38 %", ReportFormat.percent(0.38));
        assertEquals("12,5 %", ReportFormat.percent(0.125));
        assertEquals("100 %", ReportFormat.percent(1.0));
    }

    @Test
    void minutesAreWrittenAsTheTimeOfTheClock() {
        assertEquals("00:00", ReportFormat.clock(0));
        assertEquals("01:10", ReportFormat.clock(70));
        assertEquals("23:59", ReportFormat.clock(1439));
        assertThrows(IllegalArgumentException.class, () -> ReportFormat.clock(-1));
    }

    @Test
    void saysHowMuchTimeIsLeft() {
        assertEquals(ReportFormat.CALCULATING, remaining(ReportProgress.UNKNOWN));
        assertEquals(ReportFormat.ALMOST_DONE, remaining(0));
        assertEquals(ReportFormat.ALMOST_DONE, remaining(900));
        assertEquals("Faltan unos 25 s", remaining(24_200));
        assertEquals("Faltan unos 1 min 10 s", remaining(70_000));
        assertEquals("Faltan unos 2 min", remaining(120_000));
        assertThrows(IllegalArgumentException.class, () -> ReportFormat.remaining(null));
    }
}
