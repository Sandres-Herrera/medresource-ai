package com.medresource.services.simulation;

import com.medresource.model.ExtraResource;
import com.medresource.model.HospitalConfig;
import com.medresource.model.ResourceChange;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationReport;
import com.medresource.model.Statistic;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    /** Análisis hecho a mano con un quirófano más y FIFO, que no mejora. */
    private static WhatIfReport whatIf(double roomChange, double roomWait) {
        Statistic base = Statistic.of(0.10, 0.12, 0.11);
        ResourceChange room = ResourceChange.of(ExtraResource.OPERATING_ROOMS);
        WhatIfResult roomResult = new WhatIfResult(room.describe(), room, "Prioridad clínica (triage)", base,
                Statistic.of(roomChange - 0.001, roomChange, roomChange + 0.001),
                Statistic.of(roomWait - 1, roomWait, roomWait + 1));
        WhatIfResult fifo = new WhatIfResult("Usar la estrategia FIFO (orden de llegada)", null,
                "FIFO (orden de llegada)", base, Statistic.of(0.019, 0.02, 0.021), Statistic.of(1, 0, 2));
        return new WhatIfReport(3, "Prioridad clínica (triage)", base, Statistic.of(40, 45, 50), List.of(roomResult),
                List.of(fifo));
    }

    @Test
    void writesTheSectionsOfTheWhatIfAnalysis() {
        String text = ReportPreview.renderWhatIf(whatIf(-0.03, -20));

        for (String section : new String[]{"Recursos que faltaron", "Política de atención", "Recomendaciones"}) {
            assertTrue(text.contains("== " + section + " =="), section);
        }
        assertTrue(text.contains("1. 1 quirófano más: evita 3,0 muertes por cada 100 pacientes"));
        assertTrue(text.contains("La estrategia actual es la mejor de las probadas."));
    }

    @Test
    void saysSoWhenTheHospitalNeedsNoChanges() {
        String text = ReportPreview.renderWhatIf(whatIf(0.0, 0.0));

        assertTrue(text.contains("Ningún recurso extra evita muertes con claridad."));
        assertTrue(text.contains("El hospital no necesita cambios: ninguna alternativa lo mejora con claridad."));
    }

    @Test
    void needsAWhatIfAnalysis() {
        assertThrows(IllegalArgumentException.class, () -> ReportPreview.renderWhatIf(null));
    }
}
