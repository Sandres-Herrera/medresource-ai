package com.medresource.persistence;

import com.medresource.model.CaseFixtures;
import com.medresource.model.Disease;
import com.medresource.model.ResultFixtures;
import com.medresource.model.RunRecord;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.medresource.model.ResultFixtures.HOSPITAL;
import static com.medresource.model.ResultFixtures.assertSameRun;
import static com.medresource.model.ResultFixtures.runOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del DAO de ejecuciones con una base de datos falsa en memoria.
 *
 * <p>El caso 1 (semilla 77) y el caso 2 (semilla 88) ya están guardados; los dos son de tres pacientes,
 * escenario normal y 120 minutos, como los de {@link ResultFixtures}.</p>
 */
class OracleResultDaoTest {

    private final FakeResultDatabase database = new FakeResultDatabase();
    private final CatalogStub catalog = new CatalogStub(CaseFixtures.diseases());
    private final OracleResultDao dao = new OracleResultDao(database.manager(), catalog);

    OracleResultDaoTest() {
        CaseFixtures.diseases().forEach(disease -> database.addDisease(disease.getId()));
        database.addCase(1, 77, "NORMAL", 3, 120);
        database.addCase(2, 88, "NORMAL", 3, 120);
    }

    // ---------- guardar ----------

    @Test
    void rejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new OracleResultDao(null, catalog));
        assertThrows(IllegalArgumentException.class, () -> new OracleResultDao(database.manager(), null));
    }

    @Test
    void doesNotSaveANullRun() {
        assertThrows(IllegalArgumentException.class, () -> dao.save(null));
        assertEquals(0, database.connectionsOpened);
    }

    @Test
    void savesTheRunAndReturnsTheGeneratedId() {
        Integer first = dao.save(runOf(1, "FIFO", 77));
        Integer second = dao.save(runOf(2, "FIFO", 88));

        assertEquals(1, first.intValue());
        assertEquals(2, second.intValue());
        assertEquals(List.of(1, 2), database.resultIds());
    }

    @Test
    void savesTheStrategyTheDurationAndEveryResourceOfTheHospital() {
        int id = dao.save(runOf(2, "Prioridad clínica", 88));

        assertEquals(2, database.resultColumn(id, "id_caso"));
        assertEquals("Prioridad clínica", database.resultColumn(id, "estrategia"));
        assertEquals(130, database.resultColumn(id, "minutos_totales"));
        assertEquals(HOSPITAL.hospitalizationBeds(), database.resultColumn(id, "camas"));
        assertEquals(HOSPITAL.icuBeds(), database.resultColumn(id, "camas_uci"));
        assertEquals(HOSPITAL.operatingRooms(), database.resultColumn(id, "quirofanos"));
        assertEquals(HOSPITAL.doctors(), database.resultColumn(id, "medicos"));
        assertEquals(HOSPITAL.nurses(), database.resultColumn(id, "enfermeros"));
        assertEquals(HOSPITAL.bloodUnitsPerType(), database.resultColumn(id, "unidades_sangre_por_tipo"));
        assertEquals(HOSPITAL.morgueCapacity(), database.resultColumn(id, "capacidad_morgue"));
    }

    @Test
    void savesTheExtraDoctorsOfTheHospitalInOrder() {
        RunRecord run = withExtraDoctors(runOf(1, "FIFO", 77), Specialty.SURGERY, Specialty.PEDIATRICS,
                Specialty.SURGERY);

        int id = dao.save(run);

        assertEquals(List.of("PEDIATRICS", "SURGERY", "SURGERY"), database.extraDoctorsOf(id));
        assertEquals(HOSPITAL.doctors(), database.resultColumn(id, "medicos"));
    }

    @Test
    void aHospitalWithoutExtraDoctorsSavesNone() {
        int id = dao.save(runOf(1, "FIFO", 77));

        assertTrue(database.extraDoctorsOf(id).isEmpty());
    }

    @Test
    void savesEveryDataOfEachPatient() {
        int id = dao.save(runOf(1, "FIFO", 77));

        assertEquals(List.of(1, 2, 3), database.patientIds(id));
        assertEquals("P-001", database.patientColumn(id, 1, "codigo_paciente"));
        assertEquals(30, database.patientColumn(id, 1, "edad"));
        assertEquals("FEMALE", database.patientColumn(id, 1, "sexo"));
        assertEquals(2, database.patientColumn(id, 1, "nivel_triage"));
        assertEquals(1, database.patientColumn(id, 1, "id_enfermedad"));
        assertEquals(0, database.patientColumn(id, 1, "minuto_llegada"));
        assertEquals(12, database.patientColumn(id, 1, "minuto_inicio_atencion"));
        assertEquals(75, database.patientColumn(id, 1, "minuto_desenlace"));
        assertEquals("DISCHARGED", database.patientColumn(id, 1, "estado"));
        assertEquals("PRIMARY", database.patientColumn(id, 1, "nivel_atencion"));
        assertEquals(0, database.patientColumn(id, 1, "irrecuperable"));
        assertEquals(88.5, (Double) database.patientColumn(id, 1, "salud_final"), 0.0);
    }

    @Test
    void savesTheCauseOfDeathAndLeavesEmptyWhatDoesNotApply() {
        int id = dao.save(runOf(1, "FIFO", 77));

        assertNull(database.patientColumn(id, 1, "causa_muerte"));
        assertEquals("DECEASED", database.patientColumn(id, 2, "estado"));
        assertEquals("NO_ICU_BED", database.patientColumn(id, 2, "causa_muerte"));
        assertEquals("SECONDARY", database.patientColumn(id, 2, "nivel_atencion"));
        assertEquals(-1, database.patientColumn(id, 3, "minuto_inicio_atencion"));
        assertNull(database.patientColumn(id, 3, "nivel_atencion"));
        assertEquals(1, database.patientColumn(id, 3, "irrecuperable"));
        assertEquals(5, database.patientColumn(id, 3, "nivel_triage"));
    }

    @Test
    void savesTheEventsInOrderWithAnEmptyResourceWhenThereIsNone() {
        int id = dao.save(runOf(1, "FIFO", 77));

        assertEquals(4, database.eventCount(id));
        assertEquals(0, database.eventColumn(id, 0, "minuto_evento"));
        assertEquals("ARRIVAL", database.eventColumn(id, 0, "tipo_evento"));
        assertEquals("P-001", database.eventColumn(id, 0, "codigo_paciente"));
        assertNull(database.eventColumn(id, 0, "codigo_recurso"));
        assertEquals("Llega P-001", database.eventColumn(id, 0, "descripcion"));
        assertEquals("TREATMENT_STARTED", database.eventColumn(id, 1, "tipo_evento"));
        assertEquals("DOC-1", database.eventColumn(id, 1, "codigo_recurso"));
        assertEquals(75, database.eventColumn(id, 3, "minuto_evento"));
        assertEquals("BED-3", database.eventColumn(id, 3, "codigo_recurso"));
    }

    @Test
    void savesSeveralRunsOfTheSameCase() {
        int first = dao.save(runOf(1, "FIFO", 77));
        int second = dao.save(runOf(1, "Prioridad clínica", 77));

        assertEquals(List.of(first, second), database.resultIds());
        assertEquals(3, database.patientIds(second).size());
        assertEquals(4, database.eventCount(second));
    }

    @Test
    void doesNotSaveARunOfAMissingCase() {
        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(runOf(5, "FIFO", 77)));

        assertTrue(error.getMessage().contains("No existe el caso 5"));
        assertTrue(database.resultIds().isEmpty());
        assertEquals(1, database.rollbacks);
        assertEquals(0, database.commits);
    }

    @Test
    void doesNotSaveAResultThatIsNotOfTheCase() {
        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(runOf(1, "FIFO", 88)));

        assertTrue(error.getMessage().contains("El resultado no es del caso 1"));
        assertTrue(database.resultIds().isEmpty());
        assertEquals(1, database.rollbacks);
    }

    @Test
    void savesNothingIfADiseaseOfAPatientIsNotSaved() {
        FakeResultDatabase incomplete = new FakeResultDatabase();
        incomplete.addDisease(1);
        incomplete.addCase(1, 77, "NORMAL", 3, 120);
        OracleResultDao incompleteDao = new OracleResultDao(incomplete.manager(), catalog);

        DataAccessException error = assertThrows(DataAccessException.class,
                () -> incompleteDao.save(runOf(1, "FIFO", 77)));

        assertTrue(error.getMessage().contains("ORA-02291"));
        assertTrue(incomplete.resultIds().isEmpty());
        assertEquals(1, incomplete.rollbacks);
        assertEquals(0, incomplete.commits);
    }

    @Test
    void savesEverythingInOneTransactionAndClosesWhatItOpens() {
        dao.save(runOf(1, "FIFO", 77));

        assertEquals(1, database.commits);
        assertEquals(0, database.rollbacks);
        assertEquals(1, database.connectionsOpened);
        assertEquals(1, database.connectionsClosed);
        assertEquals(4, database.statementsClosed);
        assertEquals(2, database.resultSetsClosed);
    }

    @Test
    void reportsADatabaseFailureAndUndoesTheRun() {
        database.failWhenContaining = "INSERT INTO evento";

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.save(runOf(1, "FIFO", 77)));

        assertEquals("No se pudo guardar la ejecución de FIFO: fallo simulado", error.getMessage());
        assertTrue(database.resultIds().isEmpty());
        assertTrue(database.patientIds(1).isEmpty());
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
    void findsASavedRunWithAllItsData() {
        int id = dao.save(runOf(1, "FIFO", 77));

        assertSameRun(runOf(1, "FIFO", 77), dao.findById(id).orElseThrow());
    }

    @Test
    void findsASavedRunWithItsExtraDoctors() {
        RunRecord run = withExtraDoctors(runOf(1, "FIFO", 77), Specialty.TRAUMATOLOGY, Specialty.SURGERY);
        int id = dao.save(run);
        dao.save(runOf(2, "FIFO", 88));

        assertSameRun(run, dao.findById(id).orElseThrow());
        assertEquals(List.of(Specialty.SURGERY, Specialty.TRAUMATOLOGY),
                dao.findAll().get(0).hospital().extraDoctors());
        assertEquals(List.of(), dao.findAll().get(1).hospital().extraDoctors());
    }

    @Test
    void reportsAnExtraDoctorWithAnUnknownSpecialty() {
        int id = dao.save(withExtraDoctors(runOf(1, "FIFO", 77), Specialty.SURGERY));
        database.corruptExtraDoctor(id, 0, "BRUJERIA");

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(error.getMessage().contains("BRUJERIA"));
    }

    @Test
    void findsNothingForAMissingId() {
        dao.save(runOf(1, "FIFO", 77));

        assertEquals(Optional.empty(), dao.findById(9));
    }

    @Test
    void doesNotSearchWithANullId() {
        assertThrows(IllegalArgumentException.class, () -> dao.findById(null));
    }

    @Test
    void listsAllTheRunsOrderedByIdKeepingTheirPatientsAndEventsApart() {
        dao.save(runOf(2, "Prioridad clínica", 88));
        dao.save(runOf(1, "FIFO", 77));

        List<RunRecord> runs = dao.findAll();

        assertEquals(2, runs.size());
        assertSameRun(runOf(2, "Prioridad clínica", 88), runs.get(0));
        assertSameRun(runOf(1, "FIFO", 77), runs.get(1));
        assertEquals(1, catalog.lookups);
    }

    @Test
    void listsNothingAndDoesNotAskForDiseasesWhenThereAreNoRuns() {
        assertTrue(dao.findAll().isEmpty());
        assertEquals(0, catalog.lookups);
    }

    @Test
    void findsTheRunsOfACaseOrderedById() {
        int first = dao.save(runOf(1, "FIFO", 77));
        dao.save(runOf(2, "FIFO", 88));
        int third = dao.save(runOf(1, "Prioridad clínica", 77));

        assertEquals(List.of(first, third), dao.findIdsByCase(1));
        assertEquals(List.of(2), dao.findIdsByCase(2));
    }

    @Test
    void findsNoRunsForACaseWithoutRuns() {
        dao.save(runOf(1, "FIFO", 77));

        assertTrue(dao.findIdsByCase(2).isEmpty());
        assertTrue(dao.findIdsByCase(99).isEmpty());
    }

    @Test
    void reportsARunWhoseDiseaseIsNoLongerSaved() {
        int id = dao.save(runOf(1, "FIFO", 77));
        OracleResultDao withoutDisease = new OracleResultDao(database.manager(),
                new CatalogStub(List.of(CaseFixtures.diseases().get(0))));

        DataAccessException error = assertThrows(DataAccessException.class, () -> withoutDisease.findById(id));

        assertTrue(error.getMessage().contains("El paciente 2 de la ejecución " + id));
        assertTrue(error.getMessage().contains("enfermedad 2"));
    }

    @Test
    void reportsASavedScenarioThatDoesNotExist() {
        int id = dao.save(runOf(1, "FIFO", 77));
        database.corruptCase(1, "escenario", "INEXISTENTE");

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(error.getMessage().contains("INEXISTENTE"));
        assertTrue(error.getMessage().contains("Scenario"));
    }

    @Test
    void reportsAPatientWithAnUnknownTriageStatusOrSex() {
        int id = dao.save(runOf(1, "FIFO", 77));

        database.corruptPatient(id, 1, "nivel_triage", 9);
        DataAccessException triage = assertThrows(DataAccessException.class, () -> dao.findById(id));
        database.corruptPatient(id, 1, "nivel_triage", 2);
        database.corruptPatient(id, 1, "estado", "VOLANDO");
        DataAccessException status = assertThrows(DataAccessException.class, () -> dao.findById(id));
        database.corruptPatient(id, 1, "estado", "DISCHARGED");
        database.corruptPatient(id, 1, "sexo", "X");
        DataAccessException sex = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(triage.getMessage().contains("triage desconocido (9)"));
        assertTrue(status.getMessage().contains("VOLANDO"));
        assertTrue(status.getMessage().contains("PatientStatus"));
        assertTrue(sex.getMessage().contains("Sex"));
    }

    @Test
    void reportsAnUnknownCauseOfDeathOrCareLevel() {
        int id = dao.save(runOf(1, "FIFO", 77));

        database.corruptPatient(id, 2, "causa_muerte", "BRUJERIA");
        DataAccessException cause = assertThrows(DataAccessException.class, () -> dao.findById(id));
        database.corruptPatient(id, 2, "causa_muerte", "NO_ICU_BED");
        database.corruptPatient(id, 2, "nivel_atencion", "MEDIO");
        DataAccessException care = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(cause.getMessage().contains("CauseOfDeath"));
        assertTrue(care.getMessage().contains("CompatibilityLevel"));
    }

    @Test
    void reportsAPatientWithInvalidData() {
        int id = dao.save(runOf(1, "FIFO", 77));
        database.corruptPatient(id, 1, "estado", "WAITING");

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(error.getMessage().contains("El paciente 1 de la ejecución " + id + " no tiene datos válidos"));
    }

    @Test
    void reportsAnEventWithAnUnknownTypeOrInvalidData() {
        int id = dao.save(runOf(1, "FIFO", 77));

        database.corruptEvent(id, 2, "tipo_evento", "TERREMOTO");
        DataAccessException type = assertThrows(DataAccessException.class, () -> dao.findById(id));
        database.corruptEvent(id, 2, "tipo_evento", "DEATH");
        database.corruptEvent(id, 2, "descripcion", "");
        DataAccessException invalid = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(type.getMessage().contains("Un evento de la ejecución " + id));
        assertTrue(type.getMessage().contains("EventType"));
        assertTrue(invalid.getMessage().contains("no tiene datos válidos"));
    }

    @Test
    void reportsARunWhoseHospitalOrPatientsDoNotMatchWhatWasSaved() {
        int id = dao.save(runOf(1, "FIFO", 77));

        database.corruptResult(id, "camas", 9999);
        DataAccessException hospital = assertThrows(DataAccessException.class, () -> dao.findById(id));
        database.corruptResult(id, "camas", 11);
        database.corruptCase(1, "cantidad_pacientes", 4);
        DataAccessException patients = assertThrows(DataAccessException.class, () -> dao.findById(id));

        assertTrue(hospital.getMessage().contains("Los datos guardados de la ejecución " + id + " no son válidos"));
        assertTrue(patients.getMessage().contains("Los datos guardados de la ejecución " + id + " no son válidos"));
    }

    @Test
    void readsInOneTransactionAndClosesWhatItOpens() {
        int id = dao.save(runOf(1, "FIFO", 77));
        int connections = database.connectionsOpened;
        int statements = database.statementsClosed;
        int results = database.resultSetsClosed;

        dao.findById(id);

        assertEquals(connections + 1, database.connectionsOpened);
        assertEquals(database.connectionsOpened, database.connectionsClosed);
        assertEquals(statements + 4, database.statementsClosed);
        assertEquals(results + 4, database.resultSetsClosed);
    }

    // ---------- cambiar y borrar ----------

    @Test
    void doesNotAllowToUpdateARun() {
        int id = dao.save(runOf(1, "FIFO", 77));

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> dao.update(runOf(1, "FIFO", 77)));

        assertTrue(error.getMessage().contains("no se modifican"));
        assertEquals(3, database.patientIds(id).size());
    }

    @Test
    void deletesTheRunWithItsPatientsAndEvents() {
        int id = dao.save(runOf(1, "FIFO", 77));

        dao.deleteById(id);

        assertTrue(database.resultIds().isEmpty());
        assertTrue(database.patientIds(id).isEmpty());
        assertEquals(0, database.eventCount(id));
        assertEquals(Optional.empty(), dao.findById(id));
    }

    @Test
    void deletingARunDeletesItsExtraDoctors() {
        int id = dao.save(withExtraDoctors(runOf(1, "FIFO", 77), Specialty.SURGERY));

        dao.deleteById(id);

        assertTrue(database.extraDoctorsOf(id).isEmpty());
    }

    @Test
    void deletingARunKeepsTheOthers() {
        int first = dao.save(runOf(1, "FIFO", 77));
        int second = dao.save(runOf(2, "FIFO", 88));

        dao.deleteById(first);

        assertEquals(List.of(second), database.resultIds());
        assertSameRun(runOf(2, "FIFO", 88), dao.findById(second).orElseThrow());
    }

    @Test
    void doesNotDeleteAMissingRun() {
        dao.save(runOf(1, "FIFO", 77));

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.deleteById(9));

        assertTrue(error.getMessage().contains("No existe la ejecución 9"));
        assertEquals(List.of(1), database.resultIds());
        assertEquals(1, database.rollbacks);
    }

    @Test
    void doesNotDeleteWithANullId() {
        assertThrows(IllegalArgumentException.class, () -> dao.deleteById(null));
    }

    @Test
    void reportsADatabaseFailureWhileDeleting() {
        int id = dao.save(runOf(1, "FIFO", 77));
        database.failWhenContaining = "DELETE FROM";

        DataAccessException error = assertThrows(DataAccessException.class, () -> dao.deleteById(id));

        assertEquals("No se pudo borrar la ejecución " + id + ": fallo simulado", error.getMessage());
        assertEquals(List.of(id), database.resultIds());
        assertEquals(database.connectionsOpened, database.connectionsClosed);
    }

    private static RunRecord withExtraDoctors(RunRecord run, Specialty... doctors) {
        return new RunRecord(run.caseId(), run.hospital().withExtraDoctors(List.of(doctors)), run.result());
    }
}
