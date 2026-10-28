package com.medresource.services.strategy;

import org.junit.jupiter.api.Test;

import static com.medresource.services.strategy.StrategyFixtures.APPENDICITIS;
import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.disease;
import static com.medresource.services.strategy.StrategyFixtures.patient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del paciente en espera con su diagnóstico.
 */
class WaitingPatientTest {

    @Test
    void keepsThePatientAndTheDiagnosisApart() {
        // El paciente tiene gripa, pero se le diagnosticó apendicitis.
        WaitingPatient waiting = new WaitingPatient(patient(1, 0, GENERAL_FLU, 30), disease(APPENDICITIS));

        assertEquals(GENERAL_FLU, waiting.patient().getDisease().getId());
        assertEquals(APPENDICITIS, waiting.diagnosis().getId());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new WaitingPatient(null, disease(GENERAL_FLU)));
        assertThrows(IllegalArgumentException.class,
                () -> new WaitingPatient(patient(1, 0, GENERAL_FLU, 30), null));
    }
}