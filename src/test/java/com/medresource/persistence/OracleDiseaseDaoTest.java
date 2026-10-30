package com.medresource.persistence;

import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Symptom;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static com.medresource.model.DiseaseFixtures.assertSameDisease;
import static com.medresource.model.DiseaseFixtures.full;
import static com.medresource.model.DiseaseFixtures.simple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del DAO de enfermedades con una base de datos falsa en memoria.
 */
class OracleDiseaseDaoTest {

    private final FakeDiseaseDatabase database = new FakeDiseaseDatabase();
    private final OracleDiseaseDao dao = new OracleDiseaseDao(database.manager());

    // ---------- guardar ----------

    @Test
    void savesTheDiseaseAndReturnsItsId() {
        Integer id = dao.save(full(5, "Neumonía grave"));

        assertEquals(5, id.intValue());
        assertEquals(List.of(5), database.diseaseIds());
    }

    @Test
    void savesEveryAttributeOfTheDisease() {
        dao.save(full(5, "Neumonía grave"));

        assertEquals("Neumonía grave", database.column(5, "name"));
        assertEquals("INTERNAL_MEDICINE", database.column(5, "main_specialty"));
        assertEquals("HOSPITALIZATION", database.column(5, "treatment"));
        assertEquals(0, database.column(5, "curable"));
        assertEquals(1, database.column(5, "requires_blood"));
        assertEquals(1, database.column(5, "requires_icu"));
        assertEquals(0.125, (Double) database.column(5, "deterioration_rate"), 0.0);
        assertEquals(0.25, (Double) database.column(5, "irreducible_mortality"), 0.0);
        assertEquals(45, database.column(5, "min_duration_minutes"));
        assertEquals(120, database.column(5, "max_duration_minutes"));
        assertEquals(2, database.column(5, "min_age"));
        assertEquals(90, database.column(5, "max_age"));
        assertEquals(7.5, (Double) database.column(5, "frequency"), 0.0);
    }

    @Test
    void savesBooleansAsOneAndZero() {
        dao.save(simple(6, "Gripa"));

        assertEquals(1, database.column(6, "curable"));
        assertEquals(0, database.column(6, "requires_blood"));
        assertEquals(0, database.column(6, "requires_icu"));
    }

    @Test
    void savesTheSecondarySpecialtiesAndTheSymptomsInOrder() {
        dao.save(full(5, "Neumonía grave"));

        assertEquals(List.of("GENERAL_MEDICINE", "PEDIATRICS"), database.secondaryOf(5));
        assertEquals(List.of("CHEST_PAIN", "FEVER", "COUGH"), database.symptomsOf(5));
    }

    @Test
    void aDiseaseWithoutSecondarySpecialtiesSavesNone() {
        dao.save(simple(6, "Gripa"));

        assertEquals(List.of(), database.secondaryOf(6));
        assertEquals(List.of("FEVER"), database.symptomsOf(6));
    }

    @Test
    void keepsEachDiseasesChildrenApart() {
        dao.save(full(5, "Neumonía grave"));
        dao.save(simple(6, "Gripa"));

        assertEquals(List.of("CHEST_PAIN", "FEVER", "COUGH"), database.symptomsOf(5));
        assertEquals(List.of("FEVER"), database.symptomsOf(6));
        assertEquals(List.of(5, 6), database.diseaseIds());
    }

    @Test
    void confirmsTheTransactionAndClosesEverything() {
        dao.save(full(5, "Neumonía grave"));

        assertEquals(1, database.commits);
        assertEquals(0, database.rollbacks);
        assertEquals(1, database.connectionsOpened);
        assertEquals(1, database.connectionsClosed);
        assertTrue(database.statementsClosed >= 3);
    }

    @Test
    void aRepeatedIdIsRejectedAndNothingChanges() {
        dao.save(full(5, "Neumonía grave"));

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(simple(5, "Otra")));

        assertTrue(error.getMessage().contains("5"), error.getMessage());
        assertTrue(error.getMessage().contains("ORA-00001"), error.getMessage());
        assertEquals(List.of(5), database.diseaseIds());
        assertEquals("Neumonía grave", database.column(5, "name"));
        assertEquals(1, database.rollbacks);
    }

    @Test
    void aRepeatedNameIsRejected() {
        dao.save(full(5, "Neumonía grave"));

        assertThrows(DataAccessException.class, () -> dao.save(simple(6, "Neumonía grave")));
        assertEquals(List.of(5), database.diseaseIds());
    }

    @Test
    void aFailureWhileSavingTheSymptomsUndoesTheWholeDisease() {
        database.failWhenContaining = "disease_symptom";

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(full(5, "Neumonía grave")));

        assertTrue(error.getMessage().contains("fallo simulado"), error.getMessage());
        assertTrue(error.getCause() instanceof SQLException);
        assertEquals(List.of(), database.diseaseIds());
        assertEquals(List.of(), database.secondaryOf(5));
        assertEquals(1, database.rollbacks);
        assertEquals(1, database.connectionsClosed);
    }

    @Test
    void savesEveryDiseaseOfTheDefaultCatalog() {
        for (Disease disease : DiseaseCatalog.defaultDiseases()) {
            dao.save(disease);
        }

        assertEquals(DiseaseCatalog.defaultDiseases().size(), database.diseaseIds().size());
    }

    @Test
    void reportsAConnectionThatCannotBeOpened() {
        ConnectionManager broken = new ConnectionManager(new DatabaseConfig("jdbc:x", "u", "p"),
                (url, user, password) -> {
                    throw new SQLException("base apagada");
                });

        assertThrows(DataAccessException.class, () -> new OracleDiseaseDao(broken).save(simple(1, "Gripa")));
    }

    @Test
    void rejectsMissingArgumentsWhenSaving() {
        assertThrows(IllegalArgumentException.class, () -> new OracleDiseaseDao(null));
        assertThrows(IllegalArgumentException.class, () -> dao.save(null));
    }

    // ---------- leer ----------

    @Test
    void findsASavedDiseaseWithAllItsData() {
        Disease saved = full(5, "Neumonía grave");
        dao.save(saved);

        Disease found = dao.findById(5).orElseThrow();

        assertSameDisease(saved, found);
    }

    @Test
    void findsASimpleDiseaseWithoutSecondarySpecialties() {
        Disease saved = simple(6, "Gripa");
        dao.save(saved);

        assertSameDisease(saved, dao.findById(6).orElseThrow());
    }

    @Test
    void findsTheRightDiseaseAmongSeveral() {
        dao.save(full(5, "Neumonía grave"));
        dao.save(simple(6, "Gripa"));

        assertEquals("Gripa", dao.findById(6).orElseThrow().getName());
        assertEquals(List.of(Symptom.FEVER), dao.findById(6).orElseThrow().getSymptoms());
        assertEquals(3, dao.findById(5).orElseThrow().getSymptoms().size());
    }

    @Test
    void anUnknownIdGivesNothing() {
        dao.save(simple(6, "Gripa"));

        assertEquals(Optional.empty(), dao.findById(99));
    }

    @Test
    void listsTheDiseasesOrderedById() {
        dao.save(simple(9, "Novena"));
        dao.save(full(2, "Segunda"));
        dao.save(simple(5, "Quinta"));

        List<Disease> diseases = dao.findAll();

        assertEquals(List.of(2, 5, 9), diseases.stream().map(Disease::getId).toList());
        assertSameDisease(full(2, "Segunda"), diseases.get(0));
    }

    @Test
    void listsNothingWhenThereAreNoDiseases() {
        assertEquals(List.of(), dao.findAll());
    }

    @Test
    void readsBackTheWholeDefaultCatalogUnchanged() {
        for (Disease disease : DiseaseCatalog.defaultDiseases()) {
            dao.save(disease);
        }

        List<Disease> read = dao.findAll();

        assertEquals(DiseaseCatalog.defaultDiseases().size(), read.size());
        for (int i = 0; i < read.size(); i++) {
            assertSameDisease(DiseaseCatalog.defaultDiseases().get(i), read.get(i));
        }
    }

    @Test
    void readingClosesTheConnectionTheStatementsAndTheResults() {
        dao.save(full(5, "Neumonía grave"));
        int statementsBefore = database.statementsClosed;

        dao.findAll();

        assertEquals(2, database.connectionsClosed);
        assertEquals(3, database.statementsClosed - statementsBefore);
        assertEquals(3, database.resultSetsClosed);
    }

    @Test
    void anUnknownValueInTheDatabaseIsReportedWithTheDisease() {
        dao.save(simple(6, "Gripa"));
        database.corruptColumn(6, "main_specialty", "NO_EXISTE");

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(6));

        assertTrue(error.getMessage().contains("6"), error.getMessage());
        assertTrue(error.getMessage().contains("NO_EXISTE"), error.getMessage());
    }

    @Test
    void aDiseaseWithoutSymptomsInTheDatabaseIsReportedAsInvalid() {
        dao.save(simple(6, "Gripa"));
        database.removeSymptoms(6);

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findAll());

        assertTrue(error.getMessage().contains("6"), error.getMessage());
        assertTrue(error.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void aFailureWhileReadingIsReported() {
        dao.save(simple(6, "Gripa"));
        database.failWhenContaining = "FROM disease_symptom";

        assertThrows(DataAccessException.class, () -> dao.findAll());
        assertThrows(DataAccessException.class, () -> dao.findById(6));
    }

    @Test
    void rejectsANullIdWhenFinding() {
        assertThrows(IllegalArgumentException.class, () -> dao.findById(null));
    }
}