package com.medresource.services.simulation;

import com.medresource.model.CareFailures;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.DeathBreakdown;
import com.medresource.model.MinuteSample;
import com.medresource.model.PatientStatus;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.TriageLevel;
import com.medresource.model.UnfinishedPatients;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.medresource.services.simulation.ReportFixtures.arrivalHealth;
import static com.medresource.services.simulation.ReportFixtures.emptySamples;
import static com.medresource.services.simulation.ReportFixtures.outcome;
import static com.medresource.services.simulation.ReportFixtures.result;
import static com.medresource.services.simulation.ReportFixtures.trace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los fallecidos, los pacientes sin terminar y las fallas de la atención.
 */
class OutcomeAnalyzerTest {

    /** Un fallecido esperando, uno atendido tarde, uno atendido a tiempo, uno sin atender y uno dado de alta. */
    private static SimulationResult result5() {
        return result(60, 90,
                outcome(1, TriageLevel.I, 0, -1, 20, PatientStatus.DECEASED, CauseOfDeath.LACK_OF_TIMELY_CARE, 0.0),
                outcome(2, TriageLevel.II, 0, 40, 70, PatientStatus.DECEASED, CauseOfDeath.LACK_OF_TIMELY_CARE, 0.0),
                outcome(3, TriageLevel.II, 0, 10, 50, PatientStatus.DECEASED, CauseOfDeath.IRREDUCIBLE_MORTALITY,
                        0.0),
                outcome(4, TriageLevel.V, 5, -1, 85, PatientStatus.UNATTENDED, null, 40.0),
                outcome(5, TriageLevel.III, 0, 5, 30, PatientStatus.DISCHARGED, null, 90.0));
    }

    @Test
    void separatesTheDeathsByTheMomentOfTheirCare() {
        DeathBreakdown deaths = new OutcomeAnalyzer().deaths(result5());

        assertEquals(1, deaths.whileWaiting());
        assertEquals(1, deaths.afterLateCare());
        assertEquals(1, deaths.despiteTimelyCare());
        assertEquals(2, deaths.byCause().get(CauseOfDeath.LACK_OF_TIMELY_CARE).intValue());
        assertEquals(1, deaths.byCause().get(CauseOfDeath.IRREDUCIBLE_MORTALITY).intValue());
    }

    @Test
    void countsWhoWasStillInTheHospitalAtTheEndOfTheArrivals() {
        List<MinuteSample> samples = new ArrayList<>(emptySamples(90));
        samples.set(59, new MinuteSample(59, 1, 2, 3, 1, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()));
        SimulationTrace trace = trace(arrivalHealth(5, 80.0), List.of(), samples);

        UnfinishedPatients unfinished = new OutcomeAnalyzer().unfinished(result5(), trace);

        assertEquals(60, unfinished.periodEndMinute());
        assertEquals(1, unfinished.inTriage());
        assertEquals(2, unfinished.waiting());
        assertEquals(3, unfinished.inTreatment());
        assertEquals(30, unfinished.extraMinutes());
        assertEquals(1, unfinished.unattended());
    }

    @Test
    void withoutSamplesNobodyIsLeft() {
        UnfinishedPatients unfinished = new OutcomeAnalyzer().unfinished(result5(),
                trace(arrivalHealth(5, 80.0), List.of(), List.of()));

        assertEquals(0, unfinished.total());
    }

    @Test
    void countsTheWaitsOverTheTargetAndTheCriticalsWithoutFirstAid() {
        SimulationTrace trace = new SimulationTrace(arrivalHealth(5, 80.0), List.of(), List.of(), Map.of(),
                Map.of(), Map.of("P001", 7, "P002", OutcomeAnalyzer.FIRST_AID_TOLERANCE_MINUTES - 1), Map.of());

        CareFailures failures = new OutcomeAnalyzer().failures(result5(), trace);

        assertEquals(3, failures.overTarget());
        assertEquals(1, failures.criticalWithoutTimelyAid());
        assertEquals(7, failures.longestCriticalWithoutAidMinutes());
    }

    @Test
    void needsTheResultAndTheTrace() {
        OutcomeAnalyzer analyzer = new OutcomeAnalyzer();

        assertThrows(IllegalArgumentException.class, () -> analyzer.deaths(null));
        assertThrows(IllegalArgumentException.class, () -> analyzer.unfinished(result5(), null));
        assertThrows(IllegalArgumentException.class, () -> analyzer.failures(null, null));
    }
}
