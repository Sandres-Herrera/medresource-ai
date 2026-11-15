package com.medresource.services.simulation;

import com.medresource.model.Morgue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los supuestos y límites que se explican en el reporte.
 */
class ReportAssumptionsTest {

    @Test
    void explainsWhatTheSimulatorDoesNotModel() {
        List<String> assumptions = ReportAssumptions.all();

        assertTrue(assumptions.stream().anyMatch(text -> text.contains("turnos")));
        assertTrue(assumptions.stream().anyMatch(text -> text.contains("diagnósticos equivocados")));
    }

    @Test
    void takesTheNumbersFromTheConstantsOfTheSimulator() {
        assertTrue(ReportAssumptions.all().stream()
                .anyMatch(text -> text.contains(Morgue.RETENTION_MINUTES + " minutos en la morgue")));
    }

    @Test
    void everyAssumptionIsAFullSentence() {
        for (String text : ReportAssumptions.all()) {
            assertFalse(text.isBlank());
            assertTrue(text.endsWith("."));
        }
        assertEquals(ReportAssumptions.all().size(), ReportAssumptions.all().stream().distinct().count());
    }
}
