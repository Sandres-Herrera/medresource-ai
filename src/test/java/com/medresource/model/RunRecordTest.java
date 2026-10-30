package com.medresource.model;

import org.junit.jupiter.api.Test;

import static com.medresource.model.ResultFixtures.HOSPITAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la ejecución guardada.
 */
class RunRecordTest {

    private static final SimulationResult RESULT = ResultFixtures.resultOf("FIFO", 77);

    @Test
    void keepsItsData() {
        RunRecord run = new RunRecord(4, HOSPITAL, RESULT);

        assertEquals(4, run.caseId());
        assertSame(HOSPITAL, run.hospital());
        assertSame(RESULT, run.result());
    }

    @Test
    void acceptsTheFirstCase() {
        assertEquals(1, new RunRecord(1, HOSPITAL, RESULT).caseId());
    }

    @Test
    void rejectsACaseIdThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new RunRecord(0, HOSPITAL, RESULT));
        assertThrows(IllegalArgumentException.class, () -> new RunRecord(-3, HOSPITAL, RESULT));
    }

    @Test
    void rejectsAMissingHospitalOrResult() {
        assertThrows(IllegalArgumentException.class, () -> new RunRecord(1, null, RESULT));
        assertThrows(IllegalArgumentException.class, () -> new RunRecord(1, HOSPITAL, null));
    }
}