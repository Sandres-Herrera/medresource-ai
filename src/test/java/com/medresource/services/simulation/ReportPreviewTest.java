package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la vista previa del reporte en consola.
 */
class ReportPreviewTest {

    @Test
    void writesEverySectionOfTheReport() {
        SimulationReport report = ReportPreview.simulate(Scenario.CATASTROPHE, 7L, HospitalConfig.small());

        String text = ReportPreview.render(report);

        for (String section : new String[]{"Resumen", "Por nivel de triage", "Fallecidos y fallas",
                "Cuellos de botella", "Línea de tiempo", "Ocupación de los recursos", "Carga del personal",
                "Banco de sangre", "Casos para revisar", "Supuestos y límites"}) {
            assertTrue(text.contains("== " + section + " =="), section);
        }
        assertTrue(text.contains("Catástrofe"));
    }

    @Test
    void writesTheMinutesAsTheTimeOfTheClock() {
        assertEquals("00:00", ReportPreview.clock(0));
        assertEquals("01:10", ReportPreview.clock(70));
        assertEquals("25:05", ReportPreview.clock(1505));
    }

    @Test
    void needsAReport() {
        assertThrows(IllegalArgumentException.class, () -> ReportPreview.render(null));
    }
}
