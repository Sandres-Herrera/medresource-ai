package com.medresource.services.simulation;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationResult;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba que el hospital estándar siga calibrado: que en cada escenario la mortalidad con la prioridad
 * clínica quede en un rango creíble y que un día normal no tenga esperas largas.
 *
 * <p>Si un cambio del motor, del catálogo o del hospital mueve estos números fuera de su rango, hay que
 * revisar la calibración (y la documentación de {@link HospitalConfig#standard()}) a propósito, no por
 * accidente.</p>
 */
class StandardHospitalCalibrationTest {

    /** Primera semilla de los casos de la calibración. */
    private static final long FIRST_SEED = 1_000_000L;

    /** Casos por escenario: suficientes para un promedio estable y rápidos de simular. */
    private static final int CASES = 20;

    private record Summary(double mortality, double averageWait) {
    }

    private static final Map<Scenario, Summary> SUMMARIES = new EnumMap<>(Scenario.class);

    private static Summary summaryOf(Scenario scenario) {
        return SUMMARIES.computeIfAbsent(scenario, StandardHospitalCalibrationTest::simulate);
    }

    private static Summary simulate(Scenario scenario) {
        int patients = 0;
        int deaths = 0;
        long waiting = 0;
        for (long seed = FIRST_SEED; seed < FIRST_SEED + CASES; seed++) {
            SimulationResult result = new SimulationEngine(
                    new CaseGenerator().generate(CaseConfig.of(seed, scenario)),
                    new HospitalFactory().create(HospitalConfig.standard()),
                    new ClinicalPriorityStrategy()).run();
            for (PatientOutcome outcome : result.outcomes()) {
                patients++;
                if (outcome.status() == PatientStatus.DECEASED) {
                    deaths++;
                }
                waiting += outcome.waitingMinutes();
            }
        }
        return new Summary(100.0 * deaths / patients, (double) waiting / patients);
    }

    private static void assertBetween(double min, double max, double value, String what) {
        assertTrue(value >= min && value <= max, what + " = " + value + ", se esperaba entre " + min + " y " + max);
    }

    @Test
    void aNormalDayHasLowMortalityAndShortWaits() {
        Summary normal = summaryOf(Scenario.NORMAL);

        assertBetween(2.0, 6.0, normal.mortality(), "Mortalidad en un día normal (%)");
        assertTrue(normal.averageWait() < 10.0, "Espera media en un día normal = " + normal.averageWait());
    }

    @Test
    void eachScenarioStaysInItsCredibleRange() {
        assertBetween(3.0, 8.0, summaryOf(Scenario.HIGH_DEMAND).mortality(), "Mortalidad con alta demanda (%)");
        assertBetween(8.0, 18.0, summaryOf(Scenario.CATASTROPHE).mortality(), "Mortalidad en una catástrofe (%)");
        assertBetween(4.0, 10.0, summaryOf(Scenario.UNEXPECTED_EMERGENCIES).mortality(),
                "Mortalidad con emergencias inesperadas (%)");
    }

    @Test
    void theMoreDemandingTheScenarioTheHigherTheMortality() {
        double normal = summaryOf(Scenario.NORMAL).mortality();

        assertTrue(normal < summaryOf(Scenario.HIGH_DEMAND).mortality());
        assertTrue(normal < summaryOf(Scenario.UNEXPECTED_EMERGENCIES).mortality());
        assertTrue(summaryOf(Scenario.HIGH_DEMAND).mortality() < summaryOf(Scenario.CATASTROPHE).mortality());
        assertTrue(summaryOf(Scenario.UNEXPECTED_EMERGENCIES).mortality()
                < summaryOf(Scenario.CATASTROPHE).mortality());
    }
}
