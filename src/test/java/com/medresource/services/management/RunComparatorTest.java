package com.medresource.services.management;

import com.medresource.model.CaseFixtures;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.ComparisonType;
import com.medresource.model.Disease;
import com.medresource.model.HospitalConfig;
import com.medresource.model.IndicatorComparison;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.ResultFixtures;
import com.medresource.model.RunComparison;
import com.medresource.model.RunRecord;
import com.medresource.model.SettingChange;
import com.medresource.model.Sex;
import com.medresource.model.SimulationResult;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.model.Trend;
import com.medresource.persistence.ResultDao;
import com.medresource.services.simulation.CaseGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la comparación de dos ejecuciones guardadas.
 *
 * <p>La ejecución 1 es la de referencia: FIFO, con el hospital de {@link ResultFixtures#HOSPITAL}, 2 atendidos,
 * 1 fallecido, 1 sin atención y esperas de 12, 0 y 120 minutos.</p>
 */
class RunComparatorTest {

    private static final String FIFO = "FIFO (orden de llegada)";
    private static final String PRIORITY = "Prioridad clínica (triage)";
    private static final HospitalConfig WITH_SURGEON =
            ResultFixtures.HOSPITAL.withExtraDoctors(List.of(Specialty.SURGERY));

    private final InMemoryResults results = new InMemoryResults();
    private final RunComparator comparator = new RunComparator(new SimulationService(
            new CaseService(new InMemoryCaseDao(), new CaseGenerator(CaseFixtures.diseases())), results));

    RunComparatorTest() {
        results.stored.put(1, new RunRecord(1, ResultFixtures.HOSPITAL, ResultFixtures.resultOf(FIFO, 77)));
    }

    // ---------- tipo y cambios ----------

    @Test
    void onlyTheStrategyChangedIsAComparisonOfStrategies() {
        results.stored.put(2, new RunRecord(1, ResultFixtures.HOSPITAL, everyoneAttended(PRIORITY), 1));

        RunComparison comparison = comparator.compare(1, 2);

        assertEquals(ComparisonType.STRATEGY, comparison.type());
        assertEquals(List.of(new SettingChange("Estrategia", FIFO, PRIORITY)), comparison.changes());
        assertEquals(1, comparison.caseId());
        assertEquals(FIFO, comparison.strategyA());
        assertEquals(PRIORITY, comparison.strategyB());
    }

    @Test
    void onlyTheResourcesChangedIsAComparisonOfResources() {
        results.stored.put(2, new RunRecord(1, WITH_SURGEON, everyoneAttended(FIFO), 1));

        RunComparison comparison = comparator.compare(1, 2);

        assertEquals(ComparisonType.RESOURCES, comparison.type());
        assertEquals(List.of(new SettingChange("Médicos extra", "ninguno", "1 de Cirugía")), comparison.changes());
    }

    @Test
    void bothChangedIsAMixedComparisonAndItIsWarned() {
        HospitalConfig bigger = new HospitalConfig(16, 4, 2, 7, 9, 4, 6, List.of(Specialty.SURGERY));
        results.stored.put(2, new RunRecord(1, bigger, everyoneAttended(PRIORITY)));

        RunComparison comparison = comparator.compare(1, 2);

        assertEquals(ComparisonType.MIXED, comparison.type());
        assertEquals(List.of("Estrategia", "Camas", "Camas UCI", "Médicos extra"),
                comparison.changes().stream().map(SettingChange::setting).toList());
        assertTrue(comparison.limitations().contains(ComparisonType.MIXED.getDescription()));
    }

    // ---------- indicadores y conclusión ----------

    @Test
    void theIndicatorsComeFromBothRunsInOrder() {
        results.stored.put(2, new RunRecord(1, WITH_SURGEON, everyoneAttended(FIFO)));

        RunComparison comparison = comparator.compare(1, 2);

        assertEquals(List.of(RunComparator.ATTENDED, RunComparator.DEATHS, RunComparator.MORTALITY,
                RunComparator.UNATTENDED, RunComparator.AVERAGE_WAIT, RunComparator.MAX_WAIT),
                comparison.indicators().stream().map(IndicatorComparison::name).toList());
        IndicatorComparison attended = comparison.indicator(RunComparator.ATTENDED).orElseThrow();
        assertEquals(2, attended.valueA(), 1e-9);
        assertEquals(3, attended.valueB(), 1e-9);
        IndicatorComparison wait = comparison.indicator(RunComparator.AVERAGE_WAIT).orElseThrow();
        assertEquals(44, wait.valueA(), 1e-9);
        assertEquals(5, wait.valueB(), 1e-9);
        assertEquals(Trend.IMPROVED, wait.trend());
    }

    @Test
    void whenEverythingImprovesTheConclusionSaysSo() {
        results.stored.put(2, new RunRecord(1, WITH_SURGEON, everyoneAttended(FIFO)));

        String conclusion = comparator.compare(1, 2).conclusion();

        assertEquals("La ejecución #2 mejoró frente a la #1 sin empeorar ningún indicador: pacientes atendidos "
                + "(+1), fallecidos (−1), mortalidad (−33,3 puntos), sin atención (−1), espera promedio "
                + "(−39 min), espera máxima (−110 min).", conclusion);
    }

    @Test
    void whenSomethingImprovesAndSomethingWorsensThereIsNoWinner() {
        results.stored.put(2, new RunRecord(1, WITH_SURGEON, fasterButDeadlier(FIFO)));

        String conclusion = comparator.compare(1, 2).conclusion();

        assertTrue(conclusion.startsWith("No hay un ganador claro: la ejecución #2 mejoró en"), conclusion);
        assertTrue(conclusion.contains("pero empeoró en fallecidos (+1)"), conclusion);
    }

    @Test
    void whenEverythingWorsensTheConclusionSaysSo() {
        results.stored.put(2, new RunRecord(1, WITH_SURGEON, everyoneAttended(FIFO)));

        String conclusion = comparator.compare(2, 1).conclusion();

        assertTrue(conclusion.startsWith("La ejecución #1 empeoró frente a la #2 sin mejorar ningún indicador"),
                conclusion);
    }

    @Test
    void withTheSameResultsTheConclusionSaysTheyAreEqual() {
        results.stored.put(2, new RunRecord(1, WITH_SURGEON, ResultFixtures.resultOf(FIFO, 77)));

        assertEquals("Las ejecuciones #1 y #2 dieron los mismos resultados en todos los indicadores.",
                comparator.compare(1, 2).conclusion());
    }

    @Test
    void theLimitationsRememberThatItIsOneCaseAndASimulation() {
        results.stored.put(2, new RunRecord(1, WITH_SURGEON, everyoneAttended(FIFO)));

        List<String> limitations = comparator.compare(1, 2).limitations();

        assertEquals(2, limitations.size());
        assertTrue(limitations.get(0).startsWith("Se compara un solo caso (semilla 77, Hospital normal)"),
                limitations.get(0));
        assertTrue(limitations.get(1).contains("no reemplaza las decisiones médicas reales"));
    }

    @Test
    void writesTheDifferencesWithSignAndUnit() {
        assertEquals("−4", RunComparator.signed(new IndicatorComparison("Fallecidos", "", 8, 4, true)));
        assertEquals("+12", RunComparator.signed(new IndicatorComparison("Atendidos", "", 80, 92, false)));
        assertEquals("−13,5 min", RunComparator.signed(new IndicatorComparison("Espera", "min", 35, 21.5, true)));
        assertEquals("+2,5 puntos", RunComparator.signed(new IndicatorComparison("Mortalidad", "%", 4, 6.5, true)));
        assertEquals("0", RunComparator.number(0.04));
    }

    // ---------- reglas ----------

    @Test
    void aRunIsNotComparedWithItself() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> comparator.compare(1, 1));

        assertTrue(error.getMessage().startsWith("Elige dos ejecuciones distintas"));
    }

    @Test
    void bothRunsMustExist() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> comparator.compare(1, 9));

        assertEquals("No existe la ejecución #9.", error.getMessage());
    }

    @Test
    void runsOfDifferentCasesAreNotCompared() {
        results.stored.put(2, new RunRecord(4, ResultFixtures.HOSPITAL, everyoneAttended(PRIORITY)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> comparator.compare(1, 2));

        assertTrue(error.getMessage().contains("son de casos distintos (#1 y #4)"), error.getMessage());
    }

    @Test
    void runsWithTheSameStrategyAndResourcesAreNotCompared() {
        results.stored.put(2, new RunRecord(1, ResultFixtures.HOSPITAL, ResultFixtures.resultOf(FIFO, 77)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> comparator.compare(1, 2));

        assertTrue(error.getMessage().contains("darían los mismos resultados"));
    }

    @Test
    void needsTheServiceOfRuns() {
        assertThrows(IllegalArgumentException.class, () -> new RunComparator(null));
    }

    // ---------- apoyos ----------

    /** Los mismos tres pacientes, todos atendidos rápido y sin fallecidos. */
    private static SimulationResult everyoneAttended(String strategy) {
        List<Disease> diseases = CaseFixtures.diseases();
        return resultWith(strategy, List.of(
                outcome(1, diseases.get(0), 0, 5, 75, PatientStatus.DISCHARGED),
                outcome(2, diseases.get(1), 10, 10, 60, PatientStatus.DISCHARGED),
                outcome(3, diseases.get(0), 10, 20, 130, PatientStatus.DISCHARGED)));
    }

    /** Los mismos tres pacientes, atendidos más rápido pero con un fallecido más. */
    private static SimulationResult fasterButDeadlier(String strategy) {
        List<Disease> diseases = CaseFixtures.diseases();
        return resultWith(strategy, List.of(
                outcome(1, diseases.get(0), 0, 1, 40, PatientStatus.DECEASED),
                outcome(2, diseases.get(1), 10, 10, 40, PatientStatus.DECEASED),
                outcome(3, diseases.get(0), 10, 15, 130, PatientStatus.DISCHARGED)));
    }

    private static PatientOutcome outcome(int id, Disease disease, int arrival, int start, int end,
                                          PatientStatus status) {
        boolean dead = status == PatientStatus.DECEASED;
        return new PatientOutcome(id, "P-00" + id, 30, Sex.FEMALE, TriageLevel.III, disease, arrival, start, end,
                status, dead ? CauseOfDeath.LACK_OF_TIMELY_CARE : null, CompatibilityLevel.PRIMARY, false,
                dead ? 0.0 : 80.0);
    }

    private static SimulationResult resultWith(String strategy, List<PatientOutcome> outcomes) {
        return new SimulationResult(strategy, ResultFixtures.configOf(77), 130, outcomes, List.of());
    }

    /** Ejecuciones guardadas en memoria; solo hace falta buscarlas. */
    private static final class InMemoryResults implements ResultDao {

        final Map<Integer, RunRecord> stored = new TreeMap<>();

        @Override
        public Integer save(RunRecord run) {
            int id = stored.size() + 1;
            stored.put(id, run);
            return id;
        }

        @Override
        public List<Integer> findIdsByCase(int caseId) {
            return stored.entrySet().stream().filter(entry -> entry.getValue().caseId() == caseId)
                    .map(Map.Entry::getKey).toList();
        }

        @Override
        public Optional<RunRecord> findById(Integer id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<RunRecord> findAll() {
            return List.copyOf(stored.values());
        }

        @Override
        public void update(RunRecord run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            stored.remove(id);
        }
    }
}
