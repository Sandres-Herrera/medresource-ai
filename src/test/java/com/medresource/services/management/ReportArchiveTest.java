package com.medresource.services.management;

import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.GeneratedReport;
import com.medresource.model.GeneratedReportFixtures;
import com.medresource.model.NarrativeSource;
import com.medresource.model.RunRecord;
import com.medresource.model.RerunPlan;
import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
import com.medresource.persistence.DataAccessException;
import com.medresource.persistence.GenericDao;
import com.medresource.persistence.ReportDao;
import com.medresource.persistence.ResultDao;
import com.medresource.services.simulation.CaseGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del archivo de reportes con accesos a datos en memoria.
 */
class ReportArchiveTest {

    private static final LocalDateTime SAVED_AT = LocalDateTime.of(2026, 11, 15, 9, 0);

    private final InMemoryDiseases diseases = new InMemoryDiseases();
    private final InMemoryCaseDao caseDao = new InMemoryCaseDao();
    private final InMemoryResults results = new InMemoryResults();
    private final InMemoryReports reports = new InMemoryReports();
    private final CaseService cases = new CaseService(caseDao, new CaseGenerator());
    private final ReportArchive archive = new ReportArchive(new DiseaseService(diseases), cases,
            new SimulationService(cases, results), reports);
    private final ReportRequest request = ReportRequestTest.request();
    private final GeneratedReport report = GeneratedReportFixtures.small();

    @Test
    void nothingCanBeMissing() {
        DiseaseService diseaseService = new DiseaseService(diseases);
        SimulationService runs = new SimulationService(cases, results);

        assertThrows(IllegalArgumentException.class, () -> new ReportArchive(null, cases, runs, reports));
        assertThrows(IllegalArgumentException.class, () -> new ReportArchive(diseaseService, null, runs, reports));
        assertThrows(IllegalArgumentException.class, () -> new ReportArchive(diseaseService, cases, null, reports));
        assertThrows(IllegalArgumentException.class, () -> new ReportArchive(diseaseService, cases, runs, null));
        assertThrows(IllegalArgumentException.class, () -> archive.save(null, report));
        assertThrows(IllegalArgumentException.class, () -> archive.save(request, null));
    }

    @Test
    void savesTheCaseTheRunAndTheReport() {
        SavedReport saved = archive.save(request, report);

        assertEquals(1, caseDao.saved.size());
        assertEquals(request.caseConfig(), caseDao.saved.get(0).config());
        assertEquals(1, results.saved.size());
        assertSame(request.result(), results.saved.get(0).result());
        assertEquals(request.hospitalConfig(), results.saved.get(0).hospital());
        assertEquals(new SavedReport(1, 1, SAVED_AT, report), saved);
    }

    @Test
    void savesTheRunThatTheSimulationIsBasedOn() {
        archive.save(request, report);
        ReportRequest rerun = new ReportRequest(request.result(), request.trace(), request.hospital(),
                request.hospitalConfig(), request.strategy(), 1);

        archive.save(rerun, report);

        assertEquals(Optional.empty(), results.saved.get(0).basedOn());
        assertEquals(Optional.of(1), results.saved.get(1).basedOn());
        assertEquals(results.saved.get(0).caseId(), results.saved.get(1).caseId());
    }

    @Test
    void preparesAnotherSimulationOfTheSameCaseOfASavedReport() {
        SavedReport saved = archive.save(request, report);

        RerunPlan same = archive.rerunPlan(saved, false);
        RerunPlan recommended = archive.rerunPlan(saved, true);

        assertEquals(saved.resultId(), same.baseRunId());
        assertEquals(request.caseConfig(), same.caseConfig());
        assertEquals(request.hospitalConfig(), same.hospital());
        assertEquals(request.result().strategyName(), same.strategyName());
        assertEquals(same, recommended);
    }

    @Test
    void theCatalogIsLoadedBeforeTheFirstSaveOnly() {
        archive.save(request, report);
        int afterFirst = diseases.saves;
        archive.save(request, report);

        assertEquals(DiseaseCatalog.defaultDiseases().size(), afterFirst);
        assertEquals(afterFirst, diseases.saves);
    }

    @Test
    void theSameCaseIsReusedForAnotherReport() {
        archive.save(request, report);
        SavedReport second = archive.save(request, report);

        assertEquals(1, caseDao.saved.size());
        assertEquals(2, results.saved.size());
        assertEquals(2, second.resultId());
    }

    @Test
    void ifTheReportFailsTheRunIsDeleted() {
        reports.failOnSave = true;

        DataAccessException error = assertThrows(DataAccessException.class, () -> archive.save(request, report));

        assertTrue(error.getMessage().contains("Oracle apagado"));
        assertTrue(results.saved.isEmpty());
    }

    @Test
    void ifTheRunCannotBeDeletedEitherBothErrorsAreKept() {
        reports.failOnSave = true;
        results.failOnDelete = true;

        DataAccessException error = assertThrows(DataAccessException.class, () -> archive.save(request, report));

        assertEquals(1, error.getSuppressed().length);
        assertEquals(1, results.saved.size());
    }

    @Test
    void listsAndOpensTheSavedReports() {
        SavedReport saved = archive.save(request, report);
        SavedReportSummary summary = new SavedReportSummary(saved.id(), saved.resultId(), SAVED_AT,
                request.caseConfig(), request.result().strategyName(), 0, NarrativeSource.AI, 1, null);
        reports.summaries.add(summary);

        assertEquals(List.of(summary), archive.summaries());
        assertEquals(Optional.of(saved), archive.find(saved.id()));
        assertEquals(Optional.empty(), archive.find(99));
    }

    /** Enfermedades en memoria que cuentan cuántas se guardaron. */
    private static final class InMemoryDiseases implements GenericDao<Disease, Integer> {

        private final Map<Integer, Disease> stored = new TreeMap<>();
        int saves;

        @Override
        public Integer save(Disease disease) {
            saves++;
            stored.put(disease.getId(), disease);
            return disease.getId();
        }

        @Override
        public Optional<Disease> findById(Integer id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Disease> findAll() {
            return List.copyOf(stored.values());
        }

        @Override
        public void update(Disease disease) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            throw new UnsupportedOperationException();
        }
    }

    /** Ejecuciones en memoria; el identificador es el orden en que se guardaron (desde 1). */
    private static final class InMemoryResults implements ResultDao {

        final Map<Integer, RunRecord> byId = new TreeMap<>();
        final List<RunRecord> saved = new ArrayList<>();
        private int nextId = 1;
        boolean failOnDelete;

        @Override
        public Integer save(RunRecord run) {
            byId.put(nextId, run);
            saved.add(run);
            return nextId++;
        }

        @Override
        public List<Integer> findIdsByCase(int caseId) {
            return byId.entrySet().stream().filter(entry -> entry.getValue().caseId() == caseId)
                    .map(Map.Entry::getKey).toList();
        }

        @Override
        public Optional<RunRecord> findById(Integer id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<RunRecord> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public void update(RunRecord run) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            if (failOnDelete) {
                throw new DataAccessException("No se pudo borrar la ejecución: Oracle apagado");
            }
            saved.remove(byId.remove(id));
        }
    }

    /** Reportes en memoria; todos quedan guardados a la misma hora. */
    private static final class InMemoryReports implements ReportDao {

        private final Map<Integer, SavedReport> stored = new TreeMap<>();
        final List<SavedReportSummary> summaries = new ArrayList<>();
        boolean failOnSave;

        @Override
        public Integer save(SavedReport report) {
            if (failOnSave) {
                throw new DataAccessException("No se pudo guardar el reporte: Oracle apagado");
            }
            int id = stored.size() + 1;
            stored.put(id, new SavedReport(id, report.resultId(), SAVED_AT, report.report()));
            return id;
        }

        @Override
        public Optional<SavedReport> findById(Integer id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Optional<SavedReport> findByResult(int resultId) {
            return stored.values().stream().filter(saved -> saved.resultId() == resultId).findFirst();
        }

        @Override
        public List<SavedReportSummary> summaries() {
            return summaries;
        }

        @Override
        public List<SavedReport> findAll() {
            return List.copyOf(stored.values());
        }

        @Override
        public void update(SavedReport report) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            throw new UnsupportedOperationException();
        }
    }
}
