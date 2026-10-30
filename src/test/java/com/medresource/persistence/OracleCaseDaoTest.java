package com.medresource.persistence;

import com.medresource.model.CaseFixtures;
import com.medresource.model.Disease;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.medresource.model.CaseFixtures.threePatients;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del DAO de casos con una base de datos falsa en memoria.
 */
class OracleCaseDaoTest {

    private final FakeCaseDatabase database = new FakeCaseDatabase();
    private final CatalogStub catalog = new CatalogStub(CaseFixtures.diseases());
    private final OracleCaseDao dao = new OracleCaseDao(database.manager(), catalog);

    OracleCaseDaoTest() {
        CaseFixtures.diseases().forEach(disease -> database.addDisease(disease.getId()));
    }

    // ---------- guardar ----------

    @Test
    void rejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new OracleCaseDao(null, catalog));
        assertThrows(IllegalArgumentException.class, () -> new OracleCaseDao(database.manager(), null));
    }

    @Test
    void doesNotSaveANullCase() {
        assertThrows(IllegalArgumentException.class, () -> dao.save(null));
        assertEquals(0, database.connectionsOpened);
    }

    @Test
    void savesTheCaseAndReturnsTheGeneratedId() {
        Integer first = dao.save(threePatients(1));
        Integer second = dao.save(threePatients(2));

        assertEquals(1, first.intValue());
        assertEquals(2, second.intValue());
        assertEquals(List.of(1, 2), database.caseIds());
    }

    @Test
    void savesTheConfigurationOfTheCase() {
        int id = dao.save(threePatients(77));

        assertEquals(77L, database.caseColumn(id, "seed"));
        assertEquals("NORMAL", database.caseColumn(id, "scenario"));
        assertEquals(3, database.caseColumn(id, "patient_count"));
        assertEquals(120, database.caseColumn(id, "duration_minutes"));
    }

    @Test
    void savesEveryDataOfEachPatient() {
        int id = dao.save(threePatients(77));

        assertEquals(List.of(1, 2, 3), database.patientIds(id));
        assertEquals(30, database.patientColumn(id, 1, "age"));
        assertEquals("FEMALE", database.patientColumn(id, 1, "sex"));
        assertEquals("O_POSITIVE", database.patientColumn(id, 1, "blood_type"));
        assertEquals(1, database.patientColumn(id, 1, "disease_id"));
        assertEquals(0, database.patientColumn(id, 1, "arrival_minute"));
        assertEquals(80.5, (Double) database.patientColumn(id, 1, "initial_health"), 0.0);
        assertEquals(0, database.patientColumn(id, 1, "irrecoverable"));
        assertEquals("MALE", database.patientColumn(id, 2, "sex"));
        assertEquals("AB_NEGATIVE", database.patientColumn(id, 2, "blood_type"));
        assertEquals(2, database.patientColumn(id, 2, "disease_id"));
        assertEquals(10, database.patientColumn(id, 2, "arrival_minute"));
        assertEquals(1, database.patientColumn(id, 3, "irrecoverable"));
        assertEquals(33.25, (Double) database.patientColumn(id, 3, "initial_health"), 0.0);
    }

    @Test
    void savesTheSymptomsOfEachPatientInOrder() {
        int id = dao.save(threePatients(77));

        assertEquals(List.of("CHEST_PAIN", "FEVER"), database.symptomsOf(id, 1));
        assertEquals(List.of("FEVER"), database.symptomsOf(id, 2));
        assertEquals(List.of("COUGH", "FEVER", "CHEST_PAIN"), database.symptomsOf(id, 3));
    }

    @Test
    void doesNotSaveTwoCasesWithTheSameConfiguration() {
        dao.save(threePatients(77));

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(threePatients(77)));

        assertTrue(error.getMessage().startsWith("No se pudo guardar el caso"));
        assertTrue(error.getMessage().contains("ORA-00001"));
        assertEquals(List.of(1), database.caseIds());
        assertEquals(3, database.patientIds(1).size());
    }

    @Test
    void savesNothingIfADiseaseOfAPatientIsNotSaved() {
        database.removeDisease(2);

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(threePatients(77)));

        assertTrue(error.getMessage().contains("ORA-02291"));
        assertTrue(database.caseIds().isEmpty());
        assertEquals(1, database.rollbacks);
        assertEquals(0, database.commits);
    }

    @Test
    void savesEverythingInOneTransactionAndClosesWhatItOpens() {
        dao.save(threePatients(77));

        assertEquals(1, database.commits);
        assertEquals(0, database.rollbacks);
        assertEquals(1, database.connectionsOpened);
        assertEquals(1, database.connectionsClosed);
        assertEquals(3, database.statementsClosed);
    }

    @Test
    void reportsADatabaseFailureAndUndoesTheCase() {
        database.failWhenContaining = "INSERT INTO case_patient_symptom";

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(threePatients(77)));

        assertEquals("No se pudo guardar el caso: fallo simulado", error.getMessage());
        assertTrue(database.caseIds().isEmpty());
        assertEquals(1, database.rollbacks);
        assertEquals(1, database.connectionsClosed);
    }

    /** Acceso a enfermedades de prueba: solo sabe listar y cuenta cuántas veces se le pidió. */
    private static final class CatalogStub implements GenericDao<Disease, Integer> {

        private final List<Disease> diseases;
        int lookups;

        CatalogStub(List<Disease> diseases) {
            this.diseases = diseases;
        }

        @Override
        public List<Disease> findAll() {
            lookups++;
            return diseases;
        }

        @Override
        public Integer save(Disease entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Disease> findById(Integer id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Disease entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Integer id) {
            throw new UnsupportedOperationException();
        }
    }
}