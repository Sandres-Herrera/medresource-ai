package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.HospitalConfig;
import com.medresource.model.RunRecord;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.FifoStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies the actual Oracle validation behavior, not just the PL/SQL script text.
 * Only runs when a local db.properties exists. Uses a dedicated temporary case,
 * rolls back all deliberate corruption, and removes the persisted test fixture.
 * Requires schema.sql and the UPDATED plsql.sql to be installed beforehand.
 */
class OraclePlsqlValidationIntegrationTest {

    private static final CaseConfig CONFIG = new CaseConfig(987_654_337L, Scenario.HIGH_DEMAND, 18, 180);
    private static final HospitalConfig HOSPITAL = new HospitalConfig(6, 2, 1, 3, 4, 2, 2);

    @Test
    void rejectsAllFiveValidationErrorsWithoutSavingInvalidRows() throws SQLException {
        assumeTrue(DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) != null,
                "Oracle validation tests require src/main/resources/db.properties.");

        ConnectionManager manager = new ConnectionManager(DatabaseConfig.fromClasspath());
        OracleDiseaseDao diseases = new OracleDiseaseDao(manager);
        OracleCaseDao cases = new OracleCaseDao(manager, diseases);
        OracleResultDao runs = new OracleResultDao(manager, diseases);

        // Never delete or alter an arbitrary user case. Reject collisions with a stale test fixture.
        assertTrue(cases.findIdByConfig(CONFIG).isEmpty(),
                "A previous validation test case exists. Remove only that test case before rerunning.");

        int caseId = -1;
        try {
            for (Disease disease : DiseaseCatalog.defaultDiseases()) {
                if (diseases.findById(disease.getId()).isEmpty()) {
                    diseases.save(disease);
                }
            }
            SimulationCase simulationCase = new CaseGenerator(diseases.findAll()).generate(CONFIG);
            caseId = cases.save(simulationCase);
            SimulationResult simulation = new SimulationEngine(simulationCase,
                    new HospitalFactory().create(HOSPITAL), new FifoStrategy()).run();
            int runId = runs.save(new RunRecord(caseId, HOSPITAL, simulation));

            try (Connection connection = manager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    assertEquals(1, function(connection, "fn_caso_integro", caseId));
                    assertEquals(1, function(connection, "fn_integridad_ejecucion", runId));
                    assertEquals(1, baseCompatible(connection, caseId, null));
                    assertEquals(1, baseCompatible(connection, caseId, runId));
                    assertEquals(0, baseCompatible(connection, caseId, -777));
                    assertEquals(1, function(connection, "fn_paciente_pertenece_caso", runId,
                            oneInt(connection, "SELECT MIN(id_paciente) FROM resultado_paciente WHERE id_ejecucion = ?", runId)));
                    assertEquals(0, function(connection, "fn_paciente_pertenece_caso", runId, 999999));
                    assertEquals(1, eventPatientValid(connection, runId, oneString(connection,
                            "SELECT MIN(codigo_paciente) FROM resultado_paciente WHERE id_ejecucion = ?", runId)));
                    assertEquals(0, eventPatientValid(connection, runId, "NO_SUCH_PATIENT"));

                    checkIncompleteCase(connection);       // ORA-20004
                    checkIncompatibleBase(connection, caseId, runId); // ORA-20005
                    checkWrongPatient(connection, runId); // ORA-20006
                    checkWrongEvent(connection, runId);   // ORA-20007
                    checkCorruptRunAndReport(connection, runId); // ORA-20008
                } finally {
                    connection.rollback();
                }
            }
        } finally {
            if (caseId != -1) {
                cases.deleteById(caseId);
            }
        }
    }

    /** A newly inserted case with no patients must not produce an execution. */
    private static void checkIncompleteCase(Connection connection) throws SQLException {
        try (CallableStatement create = connection.prepareCall("{call pr_crear_caso(?, ?, ?, ?, ?)}")) {
            create.setLong(1, 987_654_338L);
            create.setString(2, "NORMAL");
            create.setInt(3, 2);
            create.setInt(4, 180);
            create.registerOutParameter(5, Types.INTEGER);
            create.execute();
            int incompleteCase = create.getInt(5);
            assertEquals(0, function(connection, "fn_caso_integro", incompleteCase));
            assertOracleError(20004, () -> createRun(connection, incompleteCase, null));
            assertEquals(0, count(connection, "ejecucion", "id_caso", incompleteCase));
        }
    }

    private static void checkIncompatibleBase(Connection connection, int caseId, int existingRun)
            throws SQLException {
        assertEquals(1, count(connection, "ejecucion", "id_caso", caseId));
        assertOracleError(20005, () -> createRun(connection, caseId, -777));
        assertEquals(1, count(connection, "ejecucion", "id_caso", caseId));
        assertEquals(1, baseCompatible(connection, caseId, existingRun));

        // Build another valid case in this transaction. The original run cannot
        // become its base even though both cases contain equivalent patients.
        int otherCaseId = cloneCaseInTransaction(connection, caseId);
        assertEquals(1, function(connection, "fn_caso_integro", otherCaseId));
        assertEquals(0, baseCompatible(connection, otherCaseId, existingRun));
        assertOracleError(20005, () -> createRun(connection, otherCaseId, existingRun));
        assertEquals(0, count(connection, "ejecucion", "id_caso", otherCaseId));
    }

    private static int cloneCaseInTransaction(Connection connection, int originalCaseId) throws SQLException {
        int cloneId;
        try (CallableStatement create = connection.prepareCall("{call pr_crear_caso(?, ?, ?, ?, ?)}")) {
            create.setLong(1, 987_654_339L);
            create.setString(2, "HIGH_DEMAND");
            create.setInt(3, CONFIG.patientCount());
            create.setInt(4, CONFIG.durationMinutes());
            create.registerOutParameter(5, Types.INTEGER);
            create.execute();
            cloneId = create.getInt(5);
        }
        try (PreparedStatement patients = connection.prepareStatement(
                "INSERT INTO caso_paciente (id_caso, id_paciente, edad, sexo, tipo_sangre, id_enfermedad, "
                        + "minuto_llegada, salud_inicial, irrecuperable) "
                        + "SELECT ?, id_paciente, edad, sexo, tipo_sangre, id_enfermedad, "
                        + "minuto_llegada, salud_inicial, irrecuperable "
                        + "FROM caso_paciente WHERE id_caso = ?")) {
            patients.setInt(1, cloneId);
            patients.setInt(2, originalCaseId);
            assertEquals(CONFIG.patientCount(), patients.executeUpdate());
        }
        try (PreparedStatement symptoms = connection.prepareStatement(
                "INSERT INTO caso_paciente_sintoma (id_caso, id_paciente, orden_sintoma, sintoma) "
                        + "SELECT ?, id_paciente, orden_sintoma, sintoma "
                        + "FROM caso_paciente_sintoma WHERE id_caso = ?")) {
            symptoms.setInt(1, cloneId);
            symptoms.setInt(2, originalCaseId);
            assertTrue(symptoms.executeUpdate() > 0);
        }
        return cloneId;
    }

    private static void createRun(Connection connection, int caseId, Integer base) throws SQLException {
        try (CallableStatement call = connection.prepareCall(
                "{call pr_crear_ejecucion(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            call.setInt(1, caseId);
            call.setString(2, "FIFO");
            call.setInt(3, 180);
            call.setInt(4, 6);
            call.setInt(5, 2);
            call.setInt(6, 1);
            call.setInt(7, 3);
            call.setInt(8, 4);
            call.setInt(9, 2);
            call.setInt(10, 2);
            if (base == null) {
                call.setNull(11, Types.INTEGER);
            } else {
                call.setInt(11, base);
            }
            call.registerOutParameter(12, Types.INTEGER);
            call.execute();
        }
    }

    private static void checkWrongPatient(Connection connection, int runId) throws SQLException {
        int before = count(connection, "resultado_paciente", "id_ejecucion", runId);
        try (CallableStatement call = connection.prepareCall(
                "{call pr_agregar_resultado_paciente(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            call.setInt(1, runId);
            call.setInt(2, 999999);
            call.setString(3, "NO_SUCH_PATIENT");
            call.setInt(4, 35);
            call.setString(5, "MALE");
            call.setInt(6, 3);
            call.setInt(7, 1);
            call.setInt(8, 0);
            call.setInt(9, -1);
            call.setInt(10, 0);
            call.setString(11, "UNATTENDED");
            call.setNull(12, Types.VARCHAR);
            call.setNull(13, Types.VARCHAR);
            call.setInt(14, 0);
            call.setDouble(15, 50);
            assertOracleError(20006, call::execute);
        }
        assertEquals(before, count(connection, "resultado_paciente", "id_ejecucion", runId));
    }

    private static void checkWrongEvent(Connection connection, int runId) throws SQLException {
        int before = count(connection, "evento", "id_ejecucion", runId);
        try (CallableStatement call = connection.prepareCall("{call pr_agregar_evento(?, ?, ?, ?, ?, ?, ?)}")) {
            call.setInt(1, runId);
            call.setInt(2, 999999);
            call.setInt(3, 1);
            call.setString(4, "ARRIVAL");
            call.setString(5, "NO_SUCH_PATIENT");
            call.setNull(6, Types.VARCHAR);
            call.setString(7, "Invalid patient event");
            assertOracleError(20007, call::execute);
        }
        assertEquals(before, count(connection, "evento", "id_ejecucion", runId));
    }

    /** Deliberate corruption must be detected, and every change rolled back. */
    private static void checkCorruptRunAndReport(Connection connection, int runId) throws SQLException {
        assertEquals(0, count(connection, "reporte", "id_ejecucion", runId));
        checkCorruption(connection, runId,
                "DELETE FROM resultado_paciente WHERE id_ejecucion = ? AND ROWNUM = 1");
        checkCorruption(connection, runId,
                "UPDATE resultado_paciente SET edad = edad + 1 WHERE id_ejecucion = ? AND ROWNUM = 1");
        checkCorruption(connection, runId,
                "UPDATE evento SET codigo_paciente = 'NO_SUCH_PATIENT' WHERE id_ejecucion = ? AND ROWNUM = 1");
        checkCorruption(connection, runId, "DELETE FROM evento WHERE id_ejecucion = ?");
    }

    private static void checkCorruption(Connection connection, int runId, String change) throws SQLException {
        Savepoint original = connection.setSavepoint();
        try {
            try (PreparedStatement statement = connection.prepareStatement(change)) {
                statement.setInt(1, runId);
                assertTrue(statement.executeUpdate() > 0, "No records changed: " + change);
            }
            assertEquals(0, function(connection, "fn_integridad_ejecucion", runId), change);
            try (CallableStatement call = connection.prepareCall("{call pr_guardar_reporte(?, ?, ?, ?)}")) {
                call.setInt(1, runId);
                call.setString(2, "TEMPLATE");
                call.setString(3, "{}");
                call.registerOutParameter(4, Types.INTEGER);
                assertOracleError(20008, call::execute);
            }
            assertEquals(0, count(connection, "reporte", "id_ejecucion", runId));
        } finally {
            connection.rollback(original);
        }
        assertEquals(1, function(connection, "fn_integridad_ejecucion", runId),
                "Test fixture should be valid again after rollback");
    }

    private static int function(Connection connection, String name, int... args) throws SQLException {
        String placeholders = String.join(", ", java.util.Collections.nCopies(args.length, "?"));
        try (PreparedStatement call = connection.prepareStatement("SELECT " + name + "(" + placeholders + ") FROM dual")) {
            for (int i = 0; i < args.length; i++) {
                call.setInt(i + 1, args[i]);
            }
            try (ResultSet result = call.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int baseCompatible(Connection connection, int caseId, Integer base) throws SQLException {
        try (PreparedStatement call = connection.prepareStatement("SELECT fn_base_compatible(?, ?) FROM dual")) {
            call.setInt(1, caseId);
            if (base == null) {
                call.setNull(2, Types.INTEGER);
            } else {
                call.setInt(2, base);
            }
            try (ResultSet result = call.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int eventPatientValid(Connection connection, int runId, String code) throws SQLException {
        try (PreparedStatement call = connection.prepareStatement(
                "SELECT fn_evento_paciente_valido(?, ?) FROM dual")) {
            call.setInt(1, runId);
            call.setString(2, code);
            try (ResultSet result = call.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int count(Connection connection, String table, String key, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE " + key + " = ?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static int oneInt(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private static String oneString(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private static void assertOracleError(int expectedCode, Executable action) {
        SQLException error = assertThrows(SQLException.class, action);
        assertEquals(expectedCode, error.getErrorCode(), error.getMessage());
    }
}
