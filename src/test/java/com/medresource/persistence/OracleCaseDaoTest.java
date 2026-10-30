package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.CaseFixtures;
import com.medresource.model.Disease;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.medresource.model.CaseFixtures.assertSameCase;
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

    // ---------- leer ----------

    @Test
    void findsASavedCaseWithAllItsPatients() {
        int id = dao.save(threePatients(77));

        SimulationCase found = dao.findById(id).orElseThrow();

        assertSameCase(threePatients(77), found);
    }

    @Test
    void findsNothingForAMissingId() {
        dao.save(threePatients(77));

        assertEquals(Optional.empty(), dao.findById(9));
    }

    @Test
    void doesNotSearchWithANullId() {
        assertThrows(IllegalArgumentException.class, () -> dao.findById(null));
    }

    @Test
    void listsAllTheCasesOrderedByIdKeepingTheirPatientsApart() {
        dao.save(threePatients(10));
        dao.save(threePatients(20));

        List<SimulationCase> all = dao.findAll();

        assertEquals(2, all.size());
        assertSameCase(threePatients(10), all.get(0));
        assertSameCase(threePatients(20), all.get(1));
    }

    @Test
    void listsNothingAndDoesNotAskForDiseasesWhenThereAreNoCases() {
        assertTrue(dao.findAll().isEmpty());
        assertEquals(Optional.empty(), dao.findById(1));
        assertEquals(0, catalog.lookups);
    }

    @Test
    void findsTheIdOfTheCaseWithAConfiguration() {
        dao.save(threePatients(10));
        int id = dao.save(threePatients(20));

        Optional<Integer> found = dao.findIdByConfig(new CaseConfig(20, Scenario.NORMAL, 3, 120));

        assertEquals(Optional.of(id), found);
    }

    @Test
    void findsNoIdIfAnyDataOfTheConfigurationDiffers() {
        dao.save(threePatients(20));

        assertEquals(Optional.empty(), dao.findIdByConfig(new CaseConfig(21, Scenario.NORMAL, 3, 120)));
        assertEquals(Optional.empty(), dao.findIdByConfig(new CaseConfig(20, Scenario.HIGH_DEMAND, 3, 120)));
        assertEquals(Optional.empty(), dao.findIdByConfig(new CaseConfig(20, Scenario.NORMAL, 4, 120)));
        assertEquals(Optional.empty(), dao.findIdByConfig(new CaseConfig(20, Scenario.NORMAL, 3, 121)));
    }

    @Test
    void doesNotSearchWithANullConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> dao.findIdByConfig(null));
    }

    @Test
    void reportsACaseWhoseDiseaseIsNoLongerSaved() {
        int id = dao.save(threePatients(77));
        CatalogStub withoutFlu = new CatalogStub(List.of(CaseFixtures.diseases().get(0)));

        DataAccessException error = assertThrows(DataAccessException.class,
                () -> new OracleCaseDao(database.manager(), withoutFlu).findById(id));

        assertTrue(error.getMessage().contains("enfermedad 2"));
    }

    @Test
    void reportsASavedScenarioThatDoesNotExist() {
        int id = dao.save(threePatients(77));
        database.corruptCase(id, "scenario", "INVENTADO");

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(error.getMessage().contains("caso " + id));
    }

    @Test
    void reportsAPatientWithAnUnknownSexOrBloodType() {
        int id = dao.save(threePatients(77));
        database.corruptPatient(id, 2, "sex", "X");

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(error.getMessage().contains("paciente 2"));
        assertTrue(error.getMessage().contains("Sex"));

        database.corruptPatient(id, 2, "sex", "MALE");
        database.corruptPatient(id, 2, "blood_type", "Z");
        error = assertThrows(DataAccessException.class, () -> dao.findById(id));
        assertTrue(error.getMessage().contains("BloodType"));
    }

    @Test
    void reportsAPatientWithInvalidData() {
        int id = dao.save(threePatients(77));
        database.corruptPatient(id, 1, "age", 1);

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findAll());

        assertTrue(error.getMessage().contains("no son válidos"));
        assertTrue(error.getMessage().contains("paciente 1"));
    }

    @Test
    void reportsACaseWhosePatientsDoNotMatchItsConfiguration() {
        int id = dao.save(threePatients(77));
        database.corruptCase(id, "patient_count", 5);

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(error.getMessage().contains("caso " + id));
    }

    @Test
    void reportsPatientsOutOfArrivalOrder() {
        int id = dao.save(threePatients(77));
        database.corruptPatient(id, 3, "arrival_minute", 5);

        assertThrows(DataAccessException.class, () -> dao.findById(id));
    }

    @Test
    void readsInOneTransactionAndClosesWhatItOpens() {
        int id = dao.save(threePatients(77));
        int connections = database.connectionsOpened;
        int statements = database.statementsClosed;
        int results = database.resultSetsClosed;

        dao.findById(id);

        assertEquals(connections + 1, database.connectionsOpened);
        assertEquals(database.connectionsOpened, database.connectionsClosed);
        assertEquals(statements + 3, database.statementsClosed);
        assertEquals(results + 3, database.resultSetsClosed);
    }

    // ---------- cambiar y borrar ----------

    @Test
    void doesNotAllowToUpdateACase() {
        int id = dao.save(threePatients(77));

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> dao.update(threePatients(77)));

        assertTrue(error.getMessage().contains("no se modifican"));
        assertEquals(3, database.patientIds(id).size());
    }

    @Test
    void deletesTheCaseWithItsPatientsAndSymptoms() {
        int id = dao.save(threePatients(77));

        dao.deleteById(id);

        assertTrue(database.caseIds().isEmpty());
        assertTrue(database.patientIds(id).isEmpty());
        assertTrue(database.symptomsOf(id, 1).isEmpty());
        assertEquals(Optional.empty(), dao.findById(id));
    }

    @Test
    void deletingACaseKeepsTheOthers() {
        int first = dao.save(threePatients(10));
        int second = dao.save(threePatients(20));

        dao.deleteById(first);

        assertEquals(List.of(second), database.caseIds());
        assertSameCase(threePatients(20), dao.findById(second).orElseThrow());
    }

    @Test
    void doesNotDeleteAMissingCase() {
        dao.save(threePatients(77));

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.deleteById(9));

        assertTrue(error.getMessage().contains("No existe el caso 9"));
        assertEquals(List.of(1), database.caseIds());
        assertEquals(1, database.rollbacks);
    }

    @Test
    void doesNotDeleteWithANullId() {
        assertThrows(IllegalArgumentException.class, () -> dao.deleteById(null));
    }

    @Test
    void reportsADatabaseFailureWhileDeleting() {
        int id = dao.save(threePatients(77));
        database.failWhenContaining = "DELETE FROM";

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.deleteById(id));

        assertEquals("No se pudo borrar el caso " + id + ": fallo simulado", error.getMessage());
        assertEquals(List.of(id), database.caseIds());
        assertEquals(database.connectionsOpened, database.connectionsClosed);
    }
}