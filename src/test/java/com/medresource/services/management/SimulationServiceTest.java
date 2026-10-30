package com.medresource.services.management;

import com.medresource.model.CaseConfig;
import com.medresource.model.CaseFixtures;
import com.medresource.model.HospitalConfig;
import com.medresource.model.ResultFixtures;
import com.medresource.model.RunRecord;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationResult;
import com.medresource.persistence.DataAccessException;
import com.medresource.persistence.ResultDao;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.medresource.model.ResultFixtures.HOSPITAL;
import static com.medresource.model.ResultFixtures.assertSameRun;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationServiceTest {

    private static final CaseConfig CONFIG = new CaseConfig(5, Scenario.NORMAL, 10, 120);

    private final InMemoryCaseDao caseDao = new InMemoryCaseDao();
    private final CaseService cases = new CaseService(caseDao, new CaseGenerator(CaseFixtures.diseases()));
    private final InMemoryResultDao resultDao = new InMemoryResultDao();
    private final SimulationService service = new SimulationService(cases, resultDao);

    @Test
    void rejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationService(null, resultDao));
        assertThrows(IllegalArgumentException.class, () -> new SimulationService(cases, null));
    }

    // ---------- ejecutar ----------

    @Test
    void runsTheSimulationAndSavesTheResultWithTheHospital() {
        int id = service.run(CONFIG, HospitalConfig.standard(), new FifoStrategy());

        RunRecord run = service.find(id).orElseThrow();
        assertEquals(1, id);
        assertEquals(1, run.caseId());
        assertEquals(HospitalConfig.standard(), run.hospital());
        assertEquals(new FifoStrategy().getName(), run.result().strategyName());
        assertEquals(CONFIG, run.result().config());
        assertEquals(10, run.result().outcomes().size());
    }

    @Test
    void savesTheSameResultTheEngineGivesForTheCase() {
        int id = service.run(CONFIG, HospitalConfig.standard(), new ClinicalPriorityStrategy());

        SimulationResult expected = new SimulationEngine(new CaseGenerator(CaseFixtures.diseases()).generate(CONFIG),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy()).run();
        SimulationResult saved = service.find(id).orElseThrow().result();
        assertEquals(expected.totalMinutes(), saved.totalMinutes());
        assertEquals(expected.deaths(), saved.deaths());
        assertEquals(expected.events(), saved.events());
    }

    @Test
    void runsSeveralStrategiesOnTheSameSavedCase() {
        int fifo = service.run(CONFIG, HospitalConfig.standard(), new FifoStrategy());
        int priority = service.run(CONFIG, HospitalConfig.standard(), new ClinicalPriorityStrategy());

        assertEquals(1, caseDao.saved.size());
        assertEquals(1, service.find(fifo).orElseThrow().caseId());
        assertEquals(1, service.find(priority).orElseThrow().caseId());
        assertEquals(2, service.resultsOfCase(1).size());
    }

    @Test
    void aSmallerHospitalGivesADifferentResultOnTheSameCase() {
        int standard = service.run(CONFIG, HospitalConfig.standard(), new FifoStrategy());
        int small = service.run(CONFIG, new HospitalConfig(2, 1, 1, 1, 2, 1, 1), new FifoStrategy());

        assertEquals(HospitalConfig.standard(), service.find(standard).orElseThrow().hospital());
        assertEquals(new HospitalConfig(2, 1, 1, 1, 2, 1, 1), service.find(small).orElseThrow().hospital());
        assertEquals(1, caseDao.saved.size());
    }

    @Test
    void doesNotRunWithMissingData() {
        assertThrows(IllegalArgumentException.class, () -> service.run(null, HospitalConfig.standard(), new FifoStrategy()));
        assertThrows(IllegalArgumentException.class, () -> service.run(CONFIG, null, new FifoStrategy()));
        assertThrows(IllegalArgumentException.class, () -> service.run(CONFIG, HospitalConfig.standard(), null));
        assertTrue(caseDao.saved.isEmpty());
        assertTrue(resultDao.saved.isEmpty());
    }

    @Test
    void aDatabaseFailureWhileSavingTheResultReachesTheCaller() {
        resultDao.failOnSave = true;

        assertThrows(DataAccessException.class, () -> service.run(CONFIG, HospitalConfig.standard(), new FifoStrategy()));
    }

    // ---------- guardar un resultado ya calculado ----------

    @Test
    void savesAResultCalculatedOutsideTheService() {
        int caseId = cases.idFor(CONFIG);

        int id = service.save(caseId, HOSPITAL, ResultFixtures.resultOf("FIFO", 77));

        assertSameRun(ResultFixtures.runOf(caseId, "FIFO", 77), service.find(id).orElseThrow());
    }

    @Test
    void doesNotSaveAResultOfAMissingCase() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.save(7, HOSPITAL, ResultFixtures.resultOf("FIFO", 77)));

        assertTrue(error.getMessage().contains("identificador 7"));
        assertTrue(resultDao.saved.isEmpty());
    }

    @Test
    void doesNotSaveAResultWithInvalidData() {
        int caseId = cases.idFor(CONFIG);

        assertThrows(IllegalArgumentException.class, () -> service.save(0, HOSPITAL, ResultFixtures.resultOf("FIFO", 77)));
        assertThrows(IllegalArgumentException.class, () -> service.save(caseId, null, ResultFixtures.resultOf("FIFO", 77)));
        assertThrows(IllegalArgumentException.class, () -> service.save(caseId, HOSPITAL, null));
        assertTrue(resultDao.saved.isEmpty());
    }

    // ---------- consultar y borrar ----------

    @Test
    void findsNothingForAMissingRun() {
        assertEquals(Optional.empty(), service.find(3));
    }

    @Test
    void listsTheRunsOfACaseInOrderAndNothingForOtherCases() {
        int first = service.run(CONFIG, HospitalConfig.standard(), new FifoStrategy());
        service.run(new CaseConfig(6, Scenario.NORMAL, 10, 120), HospitalConfig.standard(), new FifoStrategy());
        int third = service.run(CONFIG, HospitalConfig.standard(), new ClinicalPriorityStrategy());

        List<RunRecord> runs = service.resultsOfCase(1);

        assertEquals(2, runs.size());
        assertEquals(service.find(first).orElseThrow(), runs.get(0));
        assertEquals(service.find(third).orElseThrow(), runs.get(1));
        assertTrue(service.resultsOfCase(9).isEmpty());
    }

    @Test
    void deletesAnExistingRun() {
        int id = service.run(CONFIG, HospitalConfig.standard(), new FifoStrategy());

        service.delete(id);

        assertTrue(resultDao.saved.isEmpty());
        assertEquals(Optional.empty(), service.find(id));
    }

    @Test
    void doesNotDeleteAMissingRun() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.delete(4));

        assertTrue(error.getMessage().contains("identificador 4"));
    }

    /** DAO en memoria: guarda las ejecuciones en una lista; el identificador es la posición (desde 1). */
    private static final class InMemoryResultDao implements ResultDao {

        final List<RunRecord> saved = new ArrayList<>();
        private final List<Integer> ids = new ArrayList<>();
        private int nextId = 1;
        boolean failOnSave;

        @Override
        public Integer save(RunRecord run) {
            if (failOnSave) {
                throw new DataAccessException("No se pudo guardar la ejecución: Oracle apagado");
            }
            saved.add(run);
            ids.add(nextId);
            return nextId++;
        }

        @Override
        public List<Integer> findIdsByCase(int caseId) {
            List<Integer> found = new ArrayList<>();
            for (int i = 0; i < saved.size(); i++) {
                if (saved.get(i).caseId() == caseId) {
                    found.add(ids.get(i));
                }
            }
            return found;
        }

        @Override
        public Optional<RunRecord> findById(Integer id) {
            int index = ids.indexOf(id);
            return index < 0 ? Optional.empty() : Optional.of(saved.get(index));
        }

        @Override
        public List<RunRecord> findAll() {
            return List.copyOf(saved);
        }

        @Override
        public void update(RunRecord run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            int index = ids.indexOf(id);
            saved.remove(index);
            ids.remove(index);
        }
    }
}