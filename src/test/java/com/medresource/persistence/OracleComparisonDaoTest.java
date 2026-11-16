package com.medresource.persistence;

import com.medresource.model.ComparisonFixtures;
import com.medresource.model.RunComparison;
import com.medresource.model.SavedComparison;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del DAO de comparaciones con una base de datos falsa en memoria.
 *
 * <p>Las ejecuciones 1 (FIFO), 2 (prioridad clínica) y 3 (prioridad clínica con otro hospital) son del caso 1;
 * la 4 es del caso 2.</p>
 */
class OracleComparisonDaoTest {

    private final FakeComparisonDatabase database = new FakeComparisonDatabase();
    private final OracleComparisonDao dao = new OracleComparisonDao(database.manager());

    OracleComparisonDaoTest() {
        database.addRun(1, 1, "FIFO", "estándar");
        database.addRun(2, 1, "Prioridad", "estándar");
        database.addRun(3, 1, "Prioridad", "grande");
        database.addRun(4, 2, "Prioridad", "estándar");
    }

    // ---------- guardar ----------

    @Test
    void savesTheComparisonWithItsRunsTypeAndJson() {
        RunComparison comparison = ComparisonFixtures.strategies(1, 2);

        SavedComparison saved = dao.save(comparison);

        assertEquals(1, saved.id());
        assertEquals(FakeComparisonDatabase.FIRST_SAVE, saved.savedAt());
        assertEquals(comparison, saved.comparison());
        assertEquals(1, database.column(1, "id_caso"));
        assertEquals(1, database.column(1, "id_ejecucion_a"));
        assertEquals(2, database.column(1, "id_ejecucion_b"));
        assertEquals("STRATEGY", database.column(1, "tipo"));
        assertEquals(ComparisonJson.toJson(comparison), database.column(1, "contenido_json"));
        assertEquals(1, database.commits);
    }

    @Test
    void theSamePairInAnyOrderIsNotSavedTwice() {
        SavedComparison first = dao.save(ComparisonFixtures.strategies(1, 2));

        SavedComparison again = dao.save(ComparisonFixtures.strategies(2, 1));

        assertEquals(first, again);
        assertEquals(List.of(1), database.comparisonIds());
    }

    @Test
    void theDatabaseRejectsAComparisonWithTheReasonReadyToShow() {
        DataAccessException otherCase = assertThrows(DataAccessException.class,
                () -> dao.save(ComparisonFixtures.strategies(1, 4)));
        DataAccessException missing = assertThrows(DataAccessException.class,
                () -> dao.save(ComparisonFixtures.strategies(1, 9)));

        assertEquals("Las ejecuciones #1 y #4 son de casos distintos.", otherCase.getMessage());
        assertEquals("No existe la ejecución #9.", missing.getMessage());
        assertTrue(database.comparisonIds().isEmpty());
        assertEquals(2, database.rollbacks);
    }

    @Test
    void aFailureOfTheDatabaseIsReportedAndNothingIsSaved() {
        database.failWhenContaining = "pr_guardar_comparacion";

        DataAccessException error = assertThrows(DataAccessException.class,
                () -> dao.save(ComparisonFixtures.strategies(1, 2)));

        assertTrue(error.getMessage().contains("fallo simulado"));
        assertTrue(database.comparisonIds().isEmpty());
    }

    @Test
    void doesNotSaveANullComparison() {
        assertThrows(IllegalArgumentException.class, () -> dao.save(null));
    }

    // ---------- leer ----------

    @Test
    void findsAComparisonByIdAndByItsPairOfRuns() {
        SavedComparison saved = dao.save(ComparisonFixtures.strategies(1, 2));

        assertEquals(Optional.of(saved), dao.findById(1));
        assertEquals(Optional.of(saved), dao.findByRuns(2, 1));
        assertEquals(Optional.empty(), dao.findById(5));
        assertEquals(Optional.empty(), dao.findByRuns(1, 3));
    }

    @Test
    void listsTheNewestComparisonFirst() {
        dao.save(ComparisonFixtures.strategies(1, 2));
        dao.save(ComparisonFixtures.strategies(2, 3));

        assertEquals(List.of(2, 1), dao.findAll().stream().map(SavedComparison::id).toList());
    }

    @Test
    void reportsADamagedComparison() {
        dao.save(ComparisonFixtures.strategies(1, 2));
        database.corrupt(1, "contenido_json", "{\"caseId\":");

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(1));

        assertTrue(error.getMessage().startsWith("La comparación 1 no se puede abrir"));
    }

    @Test
    void reportsAComparisonWithoutDate() {
        dao.save(ComparisonFixtures.strategies(1, 2));
        database.corrupt(1, "guardado_en", null);

        assertThrows(DataAccessException.class, () -> dao.findAll());
    }

    // ---------- borrar ----------

    @Test
    void deletesAComparisonAndOnlyThatOne() {
        dao.save(ComparisonFixtures.strategies(1, 2));
        dao.save(ComparisonFixtures.strategies(2, 3));

        dao.deleteById(1);

        assertEquals(List.of(2), database.comparisonIds());
        assertThrows(DataAccessException.class, () -> dao.deleteById(1));
    }

    @Test
    void deletingARunDeletesItsComparisons() {
        dao.save(ComparisonFixtures.strategies(1, 2));

        database.deleteRun(2);

        assertTrue(dao.findAll().isEmpty());
    }

    @Test
    void closesEveryStatementAndConnection() {
        dao.save(ComparisonFixtures.strategies(1, 2));
        dao.findAll();

        assertEquals(3, database.statementsClosed);
        assertEquals(2, database.connectionsClosed);
    }

    // ---------- motivo del error de Oracle ----------

    @Test
    void takesTheReasonOutOfTheOracleMessage() {
        assertEquals("Elige dos ejecuciones distintas.", OracleComparisonDao.reasonOf(
                new SQLException("ORA-20003: Elige dos ejecuciones distintas.\nORA-06512: en línea 9")));
        assertEquals("Otro texto", OracleComparisonDao.reasonOf(new SQLException("Otro texto")));
        assertEquals("La base de datos no aceptó la comparación.",
                OracleComparisonDao.reasonOf(new SQLException((String) null)));
    }
}
