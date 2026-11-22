package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.HospitalConfig;
import com.medresource.model.RunComparison;
import com.medresource.model.RunRecord;
import com.medresource.model.SavedComparison;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.services.management.CaseService;
import com.medresource.services.management.RunComparator;
import com.medresource.services.management.SimulationService;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de las comparaciones y del PL/SQL contra una base de datos Oracle real.
 *
 * <p>Solo corre en los equipos que tienen {@code src/main/resources/db.properties}; en los demás se omite.
 * Guarda un caso con dos ejecuciones (FIFO y prioridad clínica), comprueba que las funciones PL/SQL dan los mismos
 * comprobaciones de integridad, guarda la comparación con el procedimiento, comprueba que no se repite y que el
 * trigger rechaza un inicio de atención anterior a la llegada. Borra lo que crea. Si las tablas o el PL/SQL
 * todavía no existen (no se han corrido {@code schema.sql} y {@code plsql.sql}), se omite con un aviso.</p>
 */
class OracleComparisonDaoIntegrationTest {

    private static final CaseConfig CONFIG = new CaseConfig(987_654_324L, Scenario.HIGH_DEMAND, 30, 240);
    private static final HospitalConfig HOSPITAL = new HospitalConfig(6, 2, 1, 3, 4, 2, 2);
    private static final String INTEGRITY = "SELECT fn_integridad_ejecucion(?) FROM dual";
    private static final String SHIFT_START = "UPDATE resultado_paciente SET minuto_inicio_atencion = "
            + "minuto_llegada - 1 WHERE id_ejecucion = ? AND minuto_inicio_atencion > 0 AND minuto_llegada > 0";
    private static final String START_AFTER_OUTCOME = "UPDATE resultado_paciente SET minuto_inicio_atencion = "
            + "minuto_desenlace + 1 WHERE id_ejecucion = ? AND minuto_inicio_atencion >= 0";

    @Test
    void savesAComparisonAfterCheckingRunIntegrity() throws SQLException {
        assumeTrue(DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) != null,
                "No hay db.properties: se omite la prueba con Oracle.");
        ConnectionManager manager = new ConnectionManager(DatabaseConfig.fromClasspath());
        OracleDiseaseDao diseases = new OracleDiseaseDao(manager);
        OracleCaseDao cases = new OracleCaseDao(manager, diseases);
        OracleResultDao results = new OracleResultDao(manager, diseases);
        OracleComparisonDao dao = new OracleComparisonDao(manager);
        try {
            deleteCaseIfPresent(cases);
        } catch (DataAccessException error) {
            assumeTrue(!error.getMessage().contains("ORA-00942"),
                    "Las tablas no existen: corra db/schema.sql y db/plsql.sql y repita la prueba.");
            throw error;
        }

        try {
            loadMissingDiseases(diseases);
            SimulationCase generated = new CaseGenerator(diseases.findAll()).generate(CONFIG);
            int caseId = cases.save(generated);
            SimulationResult fifo = simulate(generated, new FifoStrategy());
            SimulationResult priority = simulate(generated, new ClinicalPriorityStrategy());
            int runA = results.save(new RunRecord(caseId, HOSPITAL, fifo));
            int runB = results.save(new RunRecord(caseId, HOSPITAL, priority));

            try (Connection connection = manager.getConnection()) {
                assertRunIsComplete(connection, runA);
                assertRunIsComplete(connection, runB);
                assertTheTriggerRejectsAStartBeforeArrival(connection, runA);
                assertTheTriggerRejectsAStartAfterOutcome(connection, runA);
            }

            RunComparison comparison = new RunComparator(new SimulationService(
                    new CaseService(cases, new CaseGenerator()), results)).compare(runA, runB);
            SavedComparison saved = dao.save(comparison);
            assertEquals(comparison, saved.comparison());
            assertEquals(saved, dao.save(comparison));
            assertEquals(Optional.of(saved), dao.findByRuns(runB, runA));
            DataAccessException missing = assertThrows(DataAccessException.class,
                    () -> dao.save(withMissingRun(comparison)));
            assertTrue(missing.getMessage().startsWith("No existe la ejecución #"), missing.getMessage());
        } finally {
            deleteCaseIfPresent(cases);
        }
    }

    private static SimulationResult simulate(SimulationCase generated, AllocationStrategy strategy) {
        return new SimulationEngine(generated, new HospitalFactory().create(HOSPITAL), strategy).run();
    }

    /** La base da por completa y coherente la ejecución que guardó la aplicación (fn_integridad_ejecucion = 1). */
    private static void assertRunIsComplete(Connection connection, int runId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INTEGRITY)) {
            statement.setInt(1, runId);
            try (ResultSet row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals(1, row.getInt(1), "la ejecución #" + runId + " no pasa fn_integridad_ejecucion");
            }
        }
    }

    /** Intenta mover un inicio de atención antes de la llegada: el trigger debe rechazarlo con ORA-20001. */
    private static void assertTheTriggerRejectsAStartBeforeArrival(Connection connection, int runId)
            throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(SHIFT_START)) {
            statement.setInt(1, runId);
            SQLException error = assertThrows(SQLException.class, statement::executeUpdate);
            assertEquals(20001, error.getErrorCode());
        } finally {
            connection.rollback();
        }
    }

    /** Una atención que comienza después del desenlace también debe ser rechazada. */
    private static void assertTheTriggerRejectsAStartAfterOutcome(Connection connection, int runId)
            throws SQLException {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(START_AFTER_OUTCOME)) {
            statement.setInt(1, runId);
            SQLException error = assertThrows(SQLException.class, statement::executeUpdate);
            assertEquals(20001, error.getErrorCode());
        } finally {
            connection.rollback();
        }
    }

    /** La misma comparación, pero contra una ejecución que no existe: el procedimiento debe rechazarla. */
    private static RunComparison withMissingRun(RunComparison comparison) {
        return new RunComparison(comparison.caseId(), comparison.runA(), comparison.runB() + 1_000_000,
                comparison.strategyA(), comparison.strategyB(), comparison.type(), comparison.changes(),
                comparison.indicators(), comparison.conclusion(), comparison.limitations());
    }

    private static void deleteCaseIfPresent(OracleCaseDao cases) {
        cases.findIdByConfig(CONFIG).ifPresent(cases::deleteById);
    }

    /** Guarda las enfermedades del catálogo inicial que todavía no estén en la base de datos. */
    private static void loadMissingDiseases(OracleDiseaseDao diseases) {
        for (Disease disease : DiseaseCatalog.defaultDiseases()) {
            if (diseases.findById(disease.getId()).isEmpty()) {
                diseases.save(disease);
            }
        }
    }
}
