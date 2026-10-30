package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pruebas del instalador del esquema.
 */
class SchemaInstallerTest {

    private static final DatabaseConfig CONFIG = new DatabaseConfig("jdbc:x", "u", "p");

    private static ConnectionManager managerFor(FakeJdbc.Recorder recorder) {
        return new ConnectionManager(CONFIG, (url, user, password) -> FakeJdbc.connection(recorder));
    }

    private static String realScript() throws IOException {
        try (InputStream stream = SchemaInstaller.class.getResourceAsStream(SchemaInstaller.SCHEMA_RESOURCE)) {
            assertNotNull(stream, "no se encontró el script");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------- separar sentencias ----------

    @Test
    void splitsTheScriptIntoStatements() {
        List<String> statements = SchemaInstaller.statementsFrom("CREATE TABLE a (x NUMBER);\nCREATE TABLE b (y NUMBER);\n");

        assertEquals(List.of("CREATE TABLE a (x NUMBER)", "CREATE TABLE b (y NUMBER)"), statements);
    }

    @Test
    void ignoresCommentLinesAndBlankLines() {
        String script = "-- encabezado\n\n   -- comentario con sangría\nCREATE TABLE a (x NUMBER);\n\n-- final\n";

        assertEquals(List.of("CREATE TABLE a (x NUMBER)"), SchemaInstaller.statementsFrom(script));
    }

    @Test
    void keepsAStatementThatSpansSeveralLines() {
        String script = "CREATE TABLE a (\n    x NUMBER,\n    y NUMBER\n);";

        assertEquals(List.of("CREATE TABLE a (\n    x NUMBER,\n    y NUMBER\n)"), SchemaInstaller.statementsFrom(script));
    }

    @Test
    void understandsWindowsLineEndings() {
        String script = "-- comentario\r\nCREATE TABLE a (\r\n    x NUMBER\r\n);\r\nCREATE TABLE b (y NUMBER);\r\n";

        assertEquals(List.of("CREATE TABLE a (\n    x NUMBER\n)", "CREATE TABLE b (y NUMBER)"),
                SchemaInstaller.statementsFrom(script));
    }

    @Test
    void aLastStatementWithoutSemicolonIsStillExecuted() {
        assertEquals(List.of("A", "B"), SchemaInstaller.statementsFrom("A;\nB"));
    }

    @Test
    void anEmptyScriptHasNoStatements() {
        assertTrue(SchemaInstaller.statementsFrom("").isEmpty());
        assertTrue(SchemaInstaller.statementsFrom("-- solo un comentario\n;\n").isEmpty());
    }

    @Test
    void rejectsAMissingScript() {
        assertThrows(IllegalArgumentException.class, () -> SchemaInstaller.statementsFrom(null));
    }

    // ---------- instalar ----------

    @Test
    void executesEveryStatementOfTheScriptInOrder() throws IOException {
        FakeJdbc.Recorder recorder = new FakeJdbc.Recorder();

        new SchemaInstaller(managerFor(recorder)).install();

        List<String> expected = SchemaInstaller.statementsFrom(realScript());
        assertFalse(expected.isEmpty());
        assertEquals(expected, recorder.executed);
    }

    @Test
    void closesTheStatementAndTheConnection() {
        FakeJdbc.Recorder recorder = new FakeJdbc.Recorder();

        new SchemaInstaller(managerFor(recorder)).install();

        assertEquals(1, recorder.connectionsClosed);
        assertEquals(1, recorder.statementsClosed);
    }

    @Test
    void aRejectedStatementStopsTheInstallationAndStillClosesEverything() {
        FakeJdbc.Recorder recorder = new FakeJdbc.Recorder();
        recorder.failWhenContaining = "CREATE TABLE sim_case";

        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> new SchemaInstaller(managerFor(recorder)).install());

        assertTrue(exception.getCause() instanceof SQLException);
        assertTrue(exception.getMessage().contains("fallo simulado"));
        assertTrue(recorder.executed.stream().noneMatch(sql -> sql.contains("CREATE TABLE sim_case")));
        assertTrue(recorder.executed.stream().anyMatch(sql -> sql.contains("CREATE TABLE disease")));
        assertEquals(1, recorder.connectionsClosed);
        assertEquals(1, recorder.statementsClosed);
    }

    @Test
    void aFailedConnectionIsReportedAsADataAccessException() {
        ConnectionManager manager = new ConnectionManager(CONFIG, (url, user, password) -> {
            throw new SQLException("ORA-12541");
        });

        assertThrows(DataAccessException.class, () -> new SchemaInstaller(manager).install());
    }

    @Test
    void rejectsAMissingConnectionManager() {
        assertThrows(IllegalArgumentException.class, () -> new SchemaInstaller(null));
    }
}