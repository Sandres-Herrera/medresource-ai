package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Objective;
import com.medresource.model.PatientOutcome;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FeatureExtractor;
import com.medresource.services.strategy.NeuralNetwork;
import com.medresource.services.strategy.OptimizationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la estrategia de inteligencia artificial dentro del motor de simulación.
 */
class OptimizationStrategySimulationTest {

    private final CaseGenerator caseGenerator = new CaseGenerator();
    private final HospitalFactory hospitalFactory = new HospitalFactory();
    private final NeuralNetwork network = new NeuralNetwork(11, FeatureExtractor.FEATURE_COUNT, 6, 3, 1);

    private SimulationResult run(SimulationCase simulationCase, Objective objective) {
        return new SimulationEngine(simulationCase, hospitalFactory.create(HospitalConfig.standard()),
                new OptimizationStrategy(network, objective)).run();
    }

    @Test
    void finishesEveryScenarioWithAnOutcomeForEachPatient() {
        for (Scenario scenario : Scenario.values()) {
            SimulationCase simulationCase = caseGenerator.generate(new CaseConfig(3, scenario, 40, 240));

            SimulationResult result = run(simulationCase, Objective.MAXIMIZE_LIVES);

            assertEquals(40, result.outcomes().size(), scenario.name());
            assertTrue(result.outcomes().stream().allMatch(outcome -> outcome.status().isFinal()), scenario.name());
        }
    }

    @Test
    void theSameCaseGivesTheSameResult() {
        SimulationCase simulationCase = caseGenerator.generate(new CaseConfig(5, Scenario.CATASTROPHE, 40, 240));

        SimulationResult first = run(simulationCase, Objective.EQUITY);
        SimulationResult second = run(simulationCase, Objective.EQUITY);

        assertEquals(first.outcomes(), second.outcomes());
        assertEquals(first.events(), second.events());
    }

    @Test
    void theResultCarriesTheNameOfTheStrategy() {
        SimulationCase simulationCase = caseGenerator.generate(new CaseConfig(1, Scenario.NORMAL, 10, 120));

        SimulationResult result = run(simulationCase, Objective.MINIMIZE_WAIT);

        assertEquals("Inteligencia artificial (Minimizar el tiempo de espera)", result.strategyName());
    }

    @Test
    void decidesDifferentlyFromThePriorityByTriage() {
        SimulationCase simulationCase = caseGenerator.generate(new CaseConfig(8, Scenario.HIGH_DEMAND, 60, 240));

        SimulationResult artificial = run(simulationCase, Objective.MAXIMIZE_LIVES);
        SimulationResult clinical = new SimulationEngine(simulationCase,
                hospitalFactory.create(HospitalConfig.standard()), new ClinicalPriorityStrategy()).run();

        assertTrue(startMinutes(artificial) != startMinutes(clinical));
    }

    private static String startMinutes(SimulationResult result) {
        StringBuilder minutes = new StringBuilder();
        for (PatientOutcome outcome : result.outcomes()) {
            minutes.append(outcome.treatmentStartMinute()).append(',');
        }
        return minutes.toString();
    }
}