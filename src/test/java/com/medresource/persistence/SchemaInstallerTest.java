package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del instalador del esquema.
 */
class SchemaInstallerTest {

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
}