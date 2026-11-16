package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.GeneratedReport;
import com.medresource.model.GeneratedReportFixtures;
import com.medresource.model.NarrativeSource;
import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del DAO de reportes con una base de datos falsa en memoria.
 *
 * <p>Las ejecuciones 1 (FIFO, semilla 7, normal) y 2 (prioridad clínica, semilla 9, catástrofe) ya están
 * guardadas.</p>
 */
class OracleReportDaoTest {

    private final FakeReportDatabase database = new FakeReportDatabase();
    private final OracleReportDao dao = new OracleReportDao(database.manager());
    private final GeneratedReport report = GeneratedReportFixtures.small();

    OracleReportDaoTest() {
        database.addResult(1, "FIFO", 7L, "NORMAL", 100, 4);
        database.addResult(2, "Prioridad clínica", 9L, "CATASTROPHE", 100, 13);
    }

    // ---------- guardar ----------

    @Test
    void needsAConnectionManager() {
        assertThrows(IllegalArgumentException.class, () -> new OracleReportDao(null));
    }

    @Test
    void doesNotSaveANullOrAnAlreadySavedReport() {
        SavedReport saved = new SavedReport(5, 1, FakeReportDatabase.FIRST_SAVE, report);

        assertThrows(IllegalArgumentException.class, () -> dao.save(null));
        assertThrows(IllegalArgumentException.class, () -> dao.save(saved));
        assertEquals(0, database.connectionsOpened);
    }

    @Test
    void savesTheReportAsJsonWithItsSource() {
        int first = dao.save(SavedReport.toSave(1, report));
        int second = dao.save(SavedReport.toSave(2, report));

        assertEquals(1, first);
        assertEquals(2, second);
        assertEquals(List.of(1, 2), database.reportIds());
        assertEquals(1, database.reportColumn(first, "id_ejecucion"));
        assertEquals("AI", database.reportColumn(first, "origen"));
        assertEquals(ReportJson.toJson(report), database.reportColumn(first, "contenido_json"));
        assertEquals(2, database.commits);
    }

    @Test
    void aRunHasOnlyOneReportAndMustExist() {
        dao.save(SavedReport.toSave(1, report));

        assertThrows(DataAccessException.class, () -> dao.save(SavedReport.toSave(1, report)));
        DataAccessException missing = assertThrows(DataAccessException.class,
                () -> dao.save(SavedReport.toSave(3, report)));
        assertTrue(missing.getMessage().contains("ejecución 3"));
        assertEquals(List.of(1), database.reportIds());
        assertEquals(2, database.rollbacks);
    }

    @Test
    void reportsAreNotModified() {
        int id = dao.save(SavedReport.toSave(1, report));
        SavedReport saved = dao.findById(id).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> dao.update(saved));
    }

    // ---------- leer ----------

    @Test
    void readsTheSameReportThatWasSaved() {
        int id = dao.save(SavedReport.toSave(1, report));

        SavedReport saved = dao.findById(id).orElseThrow();

        assertEquals(new SavedReport(id, 1, FakeReportDatabase.FIRST_SAVE, report), saved);
        assertEquals(Optional.of(saved), dao.findByResult(1));
    }

    @Test
    void aMissingReportIsEmpty() {
        assertEquals(Optional.empty(), dao.findById(9));
        assertEquals(Optional.empty(), dao.findByResult(2));
        assertEquals(List.of(), dao.findAll());
        assertEquals(List.of(), dao.summaries());
        assertThrows(IllegalArgumentException.class, () -> dao.findById(null));
    }

    @Test
    void listsAllTheReportsInOrder() {
        dao.save(SavedReport.toSave(2, report));
        dao.save(SavedReport.toSave(1, report));

        List<SavedReport> all = dao.findAll();

        assertEquals(List.of(1, 2), all.stream().map(SavedReport::id).toList());
        assertEquals(List.of(2, 1), all.stream().map(SavedReport::resultId).toList());
    }

    @Test
    void theSummariesComeFromTheRunsAndTheNewestGoesFirst() {
        dao.save(SavedReport.toSave(1, report));
        dao.save(SavedReport.toSave(2, report));

        List<SavedReportSummary> summaries = dao.summaries();

        assertEquals(List.of(
                new SavedReportSummary(2, 2, FakeReportDatabase.FIRST_SAVE.plusMinutes(1),
                        new CaseConfig(9L, Scenario.CATASTROPHE, 100, 480), "Prioridad clínica", 13,
                        NarrativeSource.AI),
                new SavedReportSummary(1, 1, FakeReportDatabase.FIRST_SAVE,
                        new CaseConfig(7L, Scenario.NORMAL, 100, 480), "FIFO", 4, NarrativeSource.AI)),
                summaries);
    }

    @Test
    void damagedDataIsReportedAsADataError() {
        int id = dao.save(SavedReport.toSave(1, report));

        database.corruptResult(1, "escenario", "INVENTADO");
        DataAccessException scenario = assertThrows(DataAccessException.class, dao::summaries);
        assertTrue(scenario.getMessage().contains("INVENTADO"));

        database.corruptReport(id, "contenido_json", "{\"report\":");
        DataAccessException json = assertThrows(DataAccessException.class, () -> dao.findById(id));
        assertTrue(json.getMessage().contains("reporte " + id));

        database.corruptReport(id, "contenido_json", ReportJson.toJson(report));
        database.corruptReport(id, "guardado_en", null);
        assertThrows(DataAccessException.class, () -> dao.findById(id));
    }

    @Test
    void theSavedAtComesFromTheDatabase() {
        int id = dao.save(SavedReport.toSave(1, report));

        assertEquals(Timestamp.valueOf(FakeReportDatabase.FIRST_SAVE), database.reportColumn(id, "guardado_en"));
    }

    // ---------- borrar ----------

    @Test
    void deletesAReport() {
        int id = dao.save(SavedReport.toSave(1, report));

        dao.deleteById(id);

        assertEquals(List.of(), database.reportIds());
        assertThrows(DataAccessException.class, () -> dao.deleteById(id));
        assertThrows(IllegalArgumentException.class, () -> dao.deleteById(null));
    }

    @Test
    void deletingTheRunDeletesItsReport() {
        dao.save(SavedReport.toSave(1, report));

        database.deleteResult(1);

        assertEquals(Optional.empty(), dao.findByResult(1));
    }

    // ---------- recursos ----------

    @Test
    void closesEverythingItOpensEvenWhenTheDatabaseFails() {
        dao.save(SavedReport.toSave(1, report));
        dao.findById(1);
        dao.summaries();
        database.failWhenContaining = "FROM reporte";

        assertThrows(DataAccessException.class, () -> dao.findAll());

        assertEquals(database.connectionsOpened, database.connectionsClosed);
        assertTrue(database.statementsClosed >= 3);
        assertTrue(database.resultSetsClosed >= 3);
        assertEquals(1, database.rollbacks);
    }
}
