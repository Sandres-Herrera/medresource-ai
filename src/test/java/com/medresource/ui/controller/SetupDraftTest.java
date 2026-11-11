package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del borrador de una simulación nueva.
 */
class SetupDraftTest {

    private final SetupDraft draft = new SetupDraft();

    @Test
    void startsWithTheStandardHospitalAndTheDefaultCase() {
        assertEquals(HospitalConfig.standard(), draft.getHospital());
        assertEquals(HospitalPreset.STANDARD, draft.getPreset().orElseThrow());
        assertEquals(Scenario.NORMAL, draft.getScenario());
        assertEquals(CaseConfig.DEFAULT_PATIENTS, draft.getPatients());
        assertEquals(CaseConfig.DEFAULT_DURATION_MINUTES, draft.getDurationMinutes());
        assertEquals(1L, draft.getSeed());
        assertEquals(StrategyOption.CLINICAL_PRIORITY, draft.getStrategy());
        assertEquals(SimulationClock.DEFAULT_SPEED, draft.getSpeed());
    }

    @Test
    void changingAResourceMakesTheHospitalCustom() {
        draft.setResource(HospitalField.DOCTORS, 20);

        assertEquals(20, draft.getHospital().doctors());
        assertTrue(draft.getPreset().isEmpty());

        draft.setHospital(HospitalPreset.LARGE.config());

        assertEquals(HospitalPreset.LARGE, draft.getPreset().orElseThrow());
    }

    @Test
    void everyQuantityIsKeptInsideItsRange() {
        draft.setResource(HospitalField.BEDS, 999);
        draft.setPatients(0);
        draft.setDurationMinutes(5000);
        draft.setSeed(-3);

        assertEquals(HospitalConfig.MAX_BEDS, draft.getHospital().hospitalizationBeds());
        assertEquals(CaseConfig.MIN_PATIENTS, draft.getPatients());
        assertEquals(CaseConfig.MAX_DURATION_MINUTES, draft.getDurationMinutes());
        assertEquals(0L, draft.getSeed());
    }

    @Test
    void theDiceGivesASeedInsideItsRangeAndRepeatsWithTheSameGenerator() {
        long first = draft.rollSeed(new Random(5));
        long again = new SetupDraft().rollSeed(new Random(5));

        assertEquals(first, again);
        assertEquals(first, draft.getSeed());
        assertTrue(first >= 1 && first <= SetupDraft.MAX_RANDOM_SEED);
    }

    @Test
    void describesHowThePatientsArriveWithTheChosenTime() {
        assertEquals("Llegadas parejas", draft.arrivalsOf(Scenario.NORMAL));
        assertEquals("Todos en las primeras 4 h", draft.arrivalsOf(Scenario.HIGH_DEMAND));
        assertEquals("70 % en 48 min", draft.arrivalsOf(Scenario.CATASTROPHE));
        assertEquals("20 % urgentes sin aviso", draft.arrivalsOf(Scenario.UNEXPECTED_EMERGENCIES));

        draft.setDurationMinutes(120);

        assertEquals("Todos en las primeras 60 min", draft.arrivalsOf(Scenario.HIGH_DEMAND));
        assertEquals("70 % en 12 min", draft.arrivalsOf(Scenario.CATASTROPHE));
    }

    @Test
    void theSummarySaysTheRhythmTheHospitalAndHowLongItLasts() {
        assertEquals("1 paciente cada 4,8 min · hospital estándar · a ×1 se ve en 8 h", draft.summary());

        draft.setSpeed(10);
        draft.setResource(HospitalField.NURSES, 30);

        assertEquals("1 paciente cada 4,8 min · hospital personalizado · a ×10 se ve en 48 min", draft.summary());
    }

    @Test
    void withManyPatientsTheSummaryCountsThemPerMinute() {
        draft.setPatients(1000);
        draft.setDurationMinutes(60);

        assertTrue(draft.summary().startsWith("16,7 pacientes por minuto"), draft.summary());
    }

    @Test
    void writesShortTimes() {
        assertEquals("48 min", SetupDraft.formatTime(48));
        assertEquals("4 h", SetupDraft.formatTime(240));
        assertEquals("4,5 h", SetupDraft.formatTime(270));
    }

    @Test
    void buildsTheRequestWithEverythingThatWasChosen() {
        draft.setHospital(HospitalPreset.SMALL.config());
        draft.setScenario(Scenario.CATASTROPHE);
        draft.setPatients(60);
        draft.setDurationMinutes(120);
        draft.setSeed(7);
        draft.setStrategy(StrategyOption.FIFO);
        draft.setSpeed(5);

        SimulationRequest request = draft.toRequest();

        assertEquals(HospitalConfig.small(), request.setup().hospital());
        assertEquals(new CaseConfig(7L, Scenario.CATASTROPHE, 60, 120), request.setup().caseConfig());
        assertEquals(StrategyOption.FIFO, request.setup().strategy());
        assertEquals(5, request.speed());
    }

    @Test
    void rejectsMissingChoicesAndUnknownSpeeds() {
        assertThrows(IllegalArgumentException.class, () -> draft.setHospital(null));
        assertThrows(IllegalArgumentException.class, () -> draft.setResource(null, 3));
        assertThrows(IllegalArgumentException.class, () -> draft.setScenario(null));
        assertThrows(IllegalArgumentException.class, () -> draft.setStrategy(null));
        assertThrows(IllegalArgumentException.class, () -> draft.setSpeed(3));
        assertThrows(IllegalArgumentException.class, () -> draft.rollSeed(null));
        assertThrows(IllegalArgumentException.class, () -> draft.arrivalsOf(null));
    }
}
