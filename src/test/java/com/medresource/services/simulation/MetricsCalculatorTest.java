package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.HospitalConfig;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.RunMetrics;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.model.TriageLevel;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del cálculo de métricas de una ejecución.
 */
class MetricsCalculatorTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    private final MetricsCalculator calculator = new MetricsCalculator();

    private static PatientOutcome outcome(int id, int age, TriageLevel triage, int arrival, int start, int end,
                                          PatientStatus status, CauseOfDeath cause, CompatibilityLevel care) {
        return new PatientOutcome(id, "P" + id, age, Sex.FEMALE, triage, FLU, arrival, start, end, status, cause,
                care, false, 50.0);
    }

    /**
     * Seis pacientes:
     * 1 menor de 10 años, triage I, esperó 4, alta;
     * 2 adulto mayor de 70, triage II, esperó 30, murió por falta de UCI;
     * 3 de 40, triage IV, nunca atendido, esperó 80;
     * 4 de 65 (el límite de adulto mayor), triage III, esperó 30, estabilizado sin cura;
     * 5 menor de 14, triage I, nunca atendido, esperó 40, murió por mortalidad irreducible;
     * 6 de 15 (el límite de menor, ya no es menor), triage V, remitido sin espera.
     */
    private static SimulationResult sixPatients() {
        List<PatientOutcome> outcomes = List.of(
                outcome(1, 10, TriageLevel.I, 0, 4, 50, PatientStatus.DISCHARGED, null, CompatibilityLevel.PRIMARY),
                outcome(2, 70, TriageLevel.II, 10, 40, 90, PatientStatus.DECEASED, CauseOfDeath.NO_ICU_BED,
                        CompatibilityLevel.PRIMARY),
                outcome(3, 40, TriageLevel.IV, 20, -1, 100, PatientStatus.UNATTENDED, null, null),
                outcome(4, 65, TriageLevel.III, 30, 60, 120, PatientStatus.STABILIZED_WITHOUT_CURE, null,
                        CompatibilityLevel.SECONDARY),
                outcome(5, 14, TriageLevel.I, 40, -1, 80, PatientStatus.DECEASED, CauseOfDeath.IRREDUCIBLE_MORTALITY,
                        null),
                outcome(6, 15, TriageLevel.V, 50, -1, 50, PatientStatus.REFERRED, null, null));
        return new SimulationResult("FIFO", new CaseConfig(1, Scenario.NORMAL, 6, 60), 120, outcomes, List.of());
    }

    @Test
    void countsEachKindOfOutcome() {
        RunMetrics metrics = calculator.calculate(sixPatients());

        assertEquals("FIFO", metrics.strategyName());
        assertEquals(6, metrics.patients());
        assertEquals(2, metrics.deaths());
        assertEquals(1, metrics.avoidableDeaths());
        assertEquals(2, metrics.survivors());
        assertEquals(1, metrics.referred());
        assertEquals(1, metrics.unattended());
    }

    @Test
    void averagesTheWaitOfAllThePatientsAndOfTheUrgentOnes() {
        RunMetrics metrics = calculator.calculate(sixPatients());

        assertEquals((4 + 30 + 80 + 30 + 40 + 0) / 6.0, metrics.averageWaitMinutes(), 1e-12);
        assertEquals((4 + 30 + 40) / 3.0, metrics.urgentAverageWaitMinutes(), 1e-12);
    }

    @Test
    void addsTheYearsOfLifeOfTheSurvivorsUpToOneHundred() {
        RunMetrics metrics = calculator.calculate(sixPatients());

        assertEquals((100 - 10) + (100 - 65), metrics.lifeYearsSaved(), 0.0);
    }

    @Test
    void aSurvivorOlderThanTheLifeExpectancyAddsNoYears() {
        SimulationResult result = new SimulationResult("FIFO", new CaseConfig(1, Scenario.NORMAL, 2, 60), 60,
                List.of(outcome(1, 110, TriageLevel.IV, 0, 10, 40, PatientStatus.DISCHARGED, null,
                                CompatibilityLevel.PRIMARY),
                        outcome(2, 90, TriageLevel.IV, 0, 10, 40, PatientStatus.DISCHARGED, null,
                                CompatibilityLevel.PRIMARY)), List.of());

        assertEquals(10.0, calculator.calculate(result).lifeYearsSaved(), 0.0);
    }

    @Test
    void countsTheMinorsAndTheElderlyWithTheirDeaths() {
        RunMetrics metrics = calculator.calculate(sixPatients());

        assertEquals(2, metrics.minors());
        assertEquals(1, metrics.minorDeaths());
        assertEquals(2, metrics.elderly());
        assertEquals(1, metrics.elderlyDeaths());
    }

    @Test
    void withoutUrgentPatientsTheUrgentWaitIsNotANumber() {
        SimulationResult result = new SimulationResult("FIFO", new CaseConfig(1, Scenario.NORMAL, 1, 60), 60,
                List.of(outcome(1, 30, TriageLevel.IV, 0, 10, 40, PatientStatus.DISCHARGED, null,
                        CompatibilityLevel.PRIMARY)), List.of());

        RunMetrics metrics = calculator.calculate(result);

        assertTrue(Double.isNaN(metrics.urgentAverageWaitMinutes()));
        assertEquals(10.0, metrics.averageWaitMinutes(), 0.0);
        assertTrue(Double.isNaN(metrics.minorMortality()));
    }

    @Test
    void doesNotCalculateAMissingResult() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(null));
    }

    @Test
    void agreesWithTheResultOfARealSimulation() {
        SimulationCase simulationCase = new CaseGenerator().generate(new CaseConfig(3, Scenario.CATASTROPHE, 60, 240));
        SimulationResult result = new SimulationEngine(simulationCase,
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy()).run();

        RunMetrics metrics = calculator.calculate(result);

        assertEquals(result.strategyName(), metrics.strategyName());
        assertEquals(60, metrics.patients());
        assertEquals(result.deaths(), metrics.deaths());
        assertEquals(result.avoidableDeaths(), metrics.avoidableDeaths());
        assertEquals(60, metrics.deaths() + metrics.survivors() + metrics.referred() + metrics.unattended());
    }
}